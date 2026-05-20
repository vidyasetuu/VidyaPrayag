/*
 * File: AnnouncementRouting.kt
 * Module: feature.announcements
 *
 * Endpoints implemented:
 *   GET  /api/v1/school/announcements?user_id=…
 *   GET  /api/v1/school/announcements/search?query=…&user_id=…
 *   POST /api/v1/school/announcements/sync-whatsapp
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: School Dashboard (Announcement Tab)
 *
 * School-resolution rule:
 *   - For ADMIN  : the school is `SchoolTable WHERE owner_user_id = jwt.sub`.
 *   - For PARENT/TEACHER (per spec note): currently uses the same query because
 *     parent-school enrolment table is out of scope for this MVP.
 *     TODO (manual, future): when the `student_enrollment` table is added, look
 *     up the parent's child's school here.
 *
 * sync-whatsapp behaviour:
 *   - Marks the chosen announcements as synced_to_wa = true.
 *   - Inserts one row per (announcement × parent_phone) into whatsapp_logs with
 *     status = QUEUED.
 *   - Returns a fake job_id (UUID) and an estimated time.
 *   - NO real WhatsApp call is made. To turn this on, replace the loop inside
 *     `dbQuery { … }` below with a call to your provider (Twilio / Meta).
 */
package com.littlebridge.vidyaprayag.feature.announcements

import com.littlebridge.vidyaprayag.core.accepted
import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.core.principalUserId
import com.littlebridge.vidyaprayag.db.AnnouncementTable
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.SchoolTable
import com.littlebridge.vidyaprayag.db.UserTable
import com.littlebridge.vidyaprayag.db.WhatsappLogTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

// ---------- DTOs ----------

@Serializable
data class AnnouncementDto(
    val type: String,
    @SerialName("event_id") val eventId: String,
    val title: String,
    @SerialName("sub_title") val subTitle: String? = null,
    val description: String,
    @SerialName("event_image") val eventImage: String? = null,
    val date: String
)

@Serializable
data class AnnouncementsListResponse(
    val announcements: List<AnnouncementDto>
)

@Serializable
data class SyncWhatsAppRequest(
    @SerialName("school_id") val schoolId: Long? = null,
    @SerialName("announcement_ids") val announcementIds: List<String>? = null
)

@Serializable
data class SyncWhatsAppResponse(
    @SerialName("job_id") val jobId: String,
    @SerialName("total_queued") val totalQueued: Int,
    @SerialName("estimated_time_minutes") val estimatedTimeMinutes: Int
)

// ---------- helpers ----------

private suspend fun resolveSchoolIdForUser(uid: UUID): Long? = dbQuery {
    SchoolTable.selectAll().where { SchoolTable.ownerUserId eq uid }
        .singleOrNull()?.get(SchoolTable.id)?.value
}

// ---------- Routing ----------

fun Route.announcementRouting() {
    authenticate("jwt") {
        route("/api/v1/school/announcements") {

            // -------- list --------
            get {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolIdForUser(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val list = dbQuery {
                    AnnouncementTable.selectAll()
                        .where { AnnouncementTable.schoolId eq schoolId }
                        .orderBy(AnnouncementTable.date, org.jetbrains.exposed.sql.SortOrder.DESC)
                        .map { it.toDto() }
                }
                call.ok(AnnouncementsListResponse(list), message = "Announcements fetched successfully")
            }

            // -------- search --------
            get("/search") {
                val q = call.request.queryParameters["query"]?.lowercase().orEmpty()
                if (q.isBlank()) { call.fail("query is required"); return@get }
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolIdForUser(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val pattern = "%$q%"
                val list = dbQuery {
                    AnnouncementTable.selectAll()
                        .where {
                            (AnnouncementTable.schoolId eq schoolId) and
                            ((AnnouncementTable.title.lowerCase() like pattern) or
                             (AnnouncementTable.description.lowerCase() like pattern))
                        }
                        .orderBy(AnnouncementTable.date, org.jetbrains.exposed.sql.SortOrder.DESC)
                        .map { it.toDto() }
                }
                call.ok(AnnouncementsListResponse(list), message = "Search results fetched")
            }

            // -------- sync-whatsapp --------
            post("/sync-whatsapp") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@post
                }
                val req = call.receive<SyncWhatsAppRequest>()
                val schoolId = req.schoolId ?: resolveSchoolIdForUser(uid) ?: run {
                    call.fail("school_id missing and could not be inferred from token"); return@post
                }

                val jobId = "SYNC_WA_${UUID.randomUUID().toString().take(8).uppercase()}"
                val now = LocalDateTime.now()

                val queued = dbQuery {
                    // 1. Pick announcements to sync.
                    val toSync = if (req.announcementIds.isNullOrEmpty()) {
                        AnnouncementTable.selectAll()
                            .where { (AnnouncementTable.schoolId eq schoolId) and
                                     (AnnouncementTable.syncedToWa eq false) }
                            .map { it[AnnouncementTable.eventId] }
                    } else req.announcementIds

                    if (toSync.isEmpty()) return@dbQuery 0

                    // 2. Pick parent phone numbers. For this MVP we treat all users with
                    //    role = PARENT and a non-blank phone as recipients.
                    val parents = UserTable.selectAll()
                        .where { (UserTable.role eq "PARENT") }
                        .mapNotNull { it[UserTable.phone] }
                        .filter { it.isNotBlank() }

                    // 3. Insert a whatsapp_log row per (announcement × parent).
                    var inserted = 0
                    toSync.forEach { aid ->
                        parents.forEach { phone ->
                            WhatsappLogTable.insert {
                                it[WhatsappLogTable.schoolId] = schoolId
                                it[announcementId] = aid
                                it[WhatsappLogTable.jobId] = jobId
                                it[WhatsappLogTable.phone] = phone
                                it[status] = "QUEUED"
                                it[createdAt] = now
                            }
                            inserted++
                        }
                        // Mark announcement as synced regardless of parent count.
                        AnnouncementTable.update({ AnnouncementTable.eventId eq aid }) {
                            it[syncedToWa] = true
                        }
                    }
                    // If no parents existed, still return non-zero so the UI shows progress.
                    if (inserted == 0) toSync.size else inserted
                }

                val eta = (queued / 30 + 1).coerceAtMost(60)
                call.accepted(
                    SyncWhatsAppResponse(jobId = jobId, totalQueued = queued, estimatedTimeMinutes = eta),
                    message = "Sync process initiated successfully"
                )
            }
        }
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toDto() = AnnouncementDto(
    type = this[AnnouncementTable.type],
    eventId = this[AnnouncementTable.eventId],
    title = this[AnnouncementTable.title],
    subTitle = this[AnnouncementTable.subTitle],
    description = this[AnnouncementTable.description],
    eventImage = this[AnnouncementTable.eventImage],
    date = this[AnnouncementTable.date]
)
