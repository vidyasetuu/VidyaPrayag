/*
 * File: AdmissionRouting.kt
 * Module: feature.admissions
 *
 * Endpoints implemented:
 *   GET /api/v1/admissions/enquiries/summary    (JWT — admin/staff)
 *   GET /api/v1/admissions/enquiries            (JWT — admin/staff, paginated)
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: Admission Enquiries Dashboard
 *
 * Efficiency metric (per spec, free-form String):
 *   We compute   converted / (converted + follow_ups)   as a percentage with 0
 *   decimals. When the denominator is 0, we return "0%".
 *
 * Pagination:
 *   default page=1, limit=20. Total record count comes from a count() against
 *   the same WHERE clause. Pagination math is offset = (page-1) * limit.
 *
 * Used by UI:
 *   - composeApp/.../ui/screens/admin/AdmissionCRMDashboard.kt
 */
package com.littlebridge.vidyaprayag.feature.admissions

import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.core.principalUserId
import com.littlebridge.vidyaprayag.db.AdmissionEnquiryTable
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.SchoolTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Serializable
data class EnquiryDto(
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("class") val grade: String,
    val date: String,
    val status: String,
    @SerialName("profile_pic") val profilePic: String? = null
)

@Serializable
data class EnquirySummaryCount(
    val total: Int,
    val new: Int,
    @SerialName("follow_ups") val followUps: Int,
    val converted: Int
)

@Serializable
data class EnquirySummaryResponse(
    @SerialName("summary_count") val summaryCount: EnquirySummaryCount,
    @SerialName("recent_enquiries") val recentEnquiries: List<EnquiryDto>,
    val efficiency: String
)

@Serializable
data class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_records") val totalRecords: Int
)

@Serializable
data class EnquiryListResponse(
    val enquiries: List<EnquiryDto>,
    val pagination: PaginationDto
)

private suspend fun resolveSchoolId(uid: UUID): Long? = dbQuery {
    SchoolTable.selectAll().where { SchoolTable.ownerUserId eq uid }
        .singleOrNull()?.get(SchoolTable.id)?.value
}

private fun org.jetbrains.exposed.sql.ResultRow.toEnquiryDto() = EnquiryDto(
    studentName = this[AdmissionEnquiryTable.studentName],
    parentName  = this[AdmissionEnquiryTable.parentName],
    grade       = this[AdmissionEnquiryTable.grade],
    date        = this[AdmissionEnquiryTable.date],
    status      = this[AdmissionEnquiryTable.status],
    profilePic  = this[AdmissionEnquiryTable.profilePic]
)

fun Route.admissionRouting() {
    authenticate("jwt") {
        route("/api/v1/admissions/enquiries") {

            // -------- summary --------
            get("/summary") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val data = dbQuery {
                    val rows = AdmissionEnquiryTable.selectAll()
                        .where { AdmissionEnquiryTable.schoolId eq schoolId }
                        .toList()
                    val total = rows.size
                    val newC = rows.count { it[AdmissionEnquiryTable.status] == "new" }
                    val foll = rows.count { it[AdmissionEnquiryTable.status] == "followup" }
                    val conv = rows.count { it[AdmissionEnquiryTable.status] == "converted" }
                    val recent = rows
                        .sortedByDescending { it[AdmissionEnquiryTable.date] }
                        .take(5)
                        .map { it.toEnquiryDto() }
                    val denom = conv + foll
                    val eff = if (denom == 0) "0%" else "${conv * 100 / denom}%"
                    EnquirySummaryResponse(
                        summaryCount = EnquirySummaryCount(total, newC, foll, conv),
                        recentEnquiries = recent,
                        efficiency = eff
                    )
                }
                call.ok(data, message = "Enquiry summary fetched successfully")
            }

            // -------- list (paginated) --------
            get {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val offset = ((page - 1) * limit).toLong()

                val data = dbQuery {
                    val total = AdmissionEnquiryTable.selectAll()
                        .where { AdmissionEnquiryTable.schoolId eq schoolId }
                        .count().toInt()
                    val list = AdmissionEnquiryTable.selectAll()
                        .where { AdmissionEnquiryTable.schoolId eq schoolId }
                        .orderBy(AdmissionEnquiryTable.date, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { it.toEnquiryDto() }
                    val totalPages = if (total == 0) 0 else (total + limit - 1) / limit
                    EnquiryListResponse(list, PaginationDto(page, totalPages, total))
                }
                call.ok(data, message = "Enquiries fetched")
            }
        }
    }
}
