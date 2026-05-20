/*
 * File: UserProfileRouting.kt
 * Module: feature.user
 *
 * Endpoints implemented:
 *   GET /api/v1/user/profile?user_id=…             (JWT)
 *   PUT /api/v1/user/profile/philosophy            (JWT)
 *   PUT /api/v1/user/profile/tour-videos           (JWT)
 *   PUT /api/v1/user/profile/gallery               (JWT)
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: User Profile / School Profile
 *
 * Storage strategy:
 *   - philosophy   → 1 row in school_philosophy keyed by schoolId
 *   - videos+gallery images → many rows in school_media (kind = "VIDEO" or "IMAGE")
 *   - storage info → 1 row in storage_metrics keyed by schoolId
 *
 *   For PUT /tour-videos and PUT /gallery, the request body holds the FULL
 *   desired list. We DELETE-then-INSERT all rows of the matching kind to keep
 *   the implementation simple and correct (idempotent sync). The spec calls
 *   this "Delete / Insert (Sync list)".
 *
 * Storage stats:
 *   storage_used is currently a string ("2.4 GB"). When real uploads land we'll
 *   compute this from file sizes; for now we just bump the displayed string by
 *   0.2 GB per added image so the UI shows movement. This is intentional MVP
 *   behaviour — flagged as TODO in UserProfileRouting.kt.
 *
 * Used by UI:
 *   - composeApp/.../ui/screens/admin/InstitutionalProfileScreen.kt
 */
package com.littlebridge.vidyaprayag.feature.user

import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.core.okMessage
import com.littlebridge.vidyaprayag.core.principalUserId
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.SchoolMediaTable
import com.littlebridge.vidyaprayag.db.SchoolPhilosophyTable
import com.littlebridge.vidyaprayag.db.SchoolTable
import com.littlebridge.vidyaprayag.db.StorageMetricsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

// ---------- DTOs ----------

@Serializable
data class PhilosophyDetails(
    @SerialName("core_mission") val coreMission: String? = null,
    @SerialName("learning_model") val learningModel: String? = null,
    @SerialName("primary_language") val primaryLanguage: String? = null
)

@Serializable
data class GalleryBlock(
    val images: List<String>,
    @SerialName("total_storage") val totalStorage: String,
    @SerialName("storage_used") val storageUsed: String
)

@Serializable
data class UserProfileResponse(
    @SerialName("public_profile") val publicProfile: Boolean,
    @SerialName("philosophy_details") val philosophyDetails: PhilosophyDetails,
    @SerialName("video_tour_data") val videoTourData: List<String>,
    val gallery: GalleryBlock
)

@Serializable
data class TourVideosRequest(
    @SerialName("video_tour_data") val videoTourData: List<String>
)

@Serializable
data class GalleryRequest(
    val images: List<String>
)

@Serializable
data class GalleryUpdateResponse(
    @SerialName("storage_used") val storageUsed: String,
    @SerialName("total_storage") val totalStorage: String
)

// ---------- helpers ----------

private suspend fun resolveSchoolId(uid: UUID): Long? = dbQuery {
    SchoolTable.selectAll().where { SchoolTable.ownerUserId eq uid }
        .singleOrNull()?.get(SchoolTable.id)?.value
}

private fun ensureStorageRow(schoolId: Long) {
    val exists = StorageMetricsTable.selectAll()
        .where { StorageMetricsTable.schoolId eq schoolId }.count() > 0L
    if (!exists) {
        StorageMetricsTable.insert {
            it[StorageMetricsTable.schoolId] = schoolId
            it[totalStorage] = "10 GB"
            it[storageUsed] = "0 B"
        }
    }
}

private fun ensurePhilosophyRow(schoolId: Long) {
    val exists = SchoolPhilosophyTable.selectAll()
        .where { SchoolPhilosophyTable.schoolId eq schoolId }.count() > 0L
    if (!exists) {
        SchoolPhilosophyTable.insert {
            it[SchoolPhilosophyTable.schoolId] = schoolId
            it[publicProfile] = true
        }
    }
}

// ---------- Routing ----------

fun Route.userProfileRouting() {
    authenticate("jwt") {
        route("/api/v1/user/profile") {

            // -------- GET /profile --------
            get {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val data = dbQuery {
                    val phil = SchoolPhilosophyTable.selectAll()
                        .where { SchoolPhilosophyTable.schoolId eq schoolId }.singleOrNull()
                    val mediaRows = SchoolMediaTable.selectAll()
                        .where { SchoolMediaTable.schoolId eq schoolId }
                        .orderBy(SchoolMediaTable.position).toList()
                    val videos = mediaRows.filter { it[SchoolMediaTable.kind] == "VIDEO" }
                        .map { it[SchoolMediaTable.url] }
                    val images = mediaRows.filter { it[SchoolMediaTable.kind] == "IMAGE" }
                        .map { it[SchoolMediaTable.url] }
                    val storage = StorageMetricsTable.selectAll()
                        .where { StorageMetricsTable.schoolId eq schoolId }.singleOrNull()

                    UserProfileResponse(
                        publicProfile = phil?.get(SchoolPhilosophyTable.publicProfile) ?: true,
                        philosophyDetails = PhilosophyDetails(
                            coreMission = phil?.get(SchoolPhilosophyTable.coreMission),
                            learningModel = phil?.get(SchoolPhilosophyTable.learningModel),
                            primaryLanguage = phil?.get(SchoolPhilosophyTable.primaryLanguage)
                        ),
                        videoTourData = videos,
                        gallery = GalleryBlock(
                            images = images,
                            totalStorage = storage?.get(StorageMetricsTable.totalStorage) ?: "10 GB",
                            storageUsed = storage?.get(StorageMetricsTable.storageUsed) ?: "0 B"
                        )
                    )
                }
                call.ok(data, message = "Profile fetched successfully")
            }

            // -------- PUT /philosophy --------
            put("/philosophy") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@put
                }
                val req = call.receive<PhilosophyDetails>()
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@put
                }
                dbQuery {
                    ensurePhilosophyRow(schoolId)
                    SchoolPhilosophyTable.update({ SchoolPhilosophyTable.schoolId eq schoolId }) {
                        req.coreMission?.let { v -> it[coreMission] = v }
                        req.learningModel?.let { v -> it[learningModel] = v }
                        req.primaryLanguage?.let { v -> it[primaryLanguage] = v }
                    }
                }
                call.okMessage("Philosophy updated successfully")
            }

            // -------- PUT /tour-videos --------
            put("/tour-videos") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@put
                }
                val req = call.receive<TourVideosRequest>()
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@put
                }
                dbQuery {
                    SchoolMediaTable.deleteWhere {
                        (SchoolMediaTable.schoolId eq schoolId) and (SchoolMediaTable.kind eq "VIDEO")
                    }
                    req.videoTourData.forEachIndexed { idx, url ->
                        SchoolMediaTable.insert {
                            it[SchoolMediaTable.schoolId] = schoolId
                            it[kind] = "VIDEO"
                            it[SchoolMediaTable.url] = url
                            it[position] = idx
                        }
                    }
                }
                call.okMessage("Tour videos updated successfully")
            }

            // -------- PUT /gallery --------
            put("/gallery") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@put
                }
                val req = call.receive<GalleryRequest>()
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@put
                }
                val data = dbQuery {
                    SchoolMediaTable.deleteWhere {
                        (SchoolMediaTable.schoolId eq schoolId) and (SchoolMediaTable.kind eq "IMAGE")
                    }
                    req.images.forEachIndexed { idx, url ->
                        SchoolMediaTable.insert {
                            it[SchoolMediaTable.schoolId] = schoolId
                            it[kind] = "IMAGE"
                            it[SchoolMediaTable.url] = url
                            it[position] = idx
                        }
                    }
                    // TODO: replace with real usage calculation when binary upload
                    // is wired in. For now, approximate 0.2 GB per image.
                    ensureStorageRow(schoolId)
                    val used = String.format("%.1f GB", req.images.size * 0.2)
                    StorageMetricsTable.update({ StorageMetricsTable.schoolId eq schoolId }) {
                        it[storageUsed] = used
                    }
                    val row = StorageMetricsTable.selectAll()
                        .where { StorageMetricsTable.schoolId eq schoolId }.single()
                    GalleryUpdateResponse(
                        storageUsed = row[StorageMetricsTable.storageUsed],
                        totalStorage = row[StorageMetricsTable.totalStorage]
                    )
                }
                call.ok(data, message = "Gallery updated successfully")
            }
        }
    }
}
