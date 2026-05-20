/*
 * File: SchoolRouting.kt
 * Module: feature.school
 *
 * Endpoints implemented (all under "Drawer Options" in the spec):
 *   GET /api/v1/school/analytics                  (Coming Soon placeholder)
 *   GET /api/v1/school/calendar?date=&view_type=&standard=
 *   GET /api/v1/school/holidays?filter_type=weekly|monthly|yearly
 *   GET /api/v1/school/attendance/daily?type=student|faculty&grade=
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: Drawer Options
 *
 * Calendar filtering:
 *   - view_type=week  → events whose date is within ±3 days of `date`
 *   - view_type=month → events whose date starts with the YYYY-MM of `date`
 *   - `standard` (optional) restricts to events tagged for that grade
 *     (or null-standard events, which are global).
 *
 * Holidays:
 *   `filter_type` simply selects rows by frequency column. yearly is the
 *   default if the param is missing.
 *
 * Attendance:
 *   Today's date (LocalDate.now()) is used by default. If you want historical
 *   queries we'd add a `date` query param later — the spec only describes
 *   "Daily Attendance" so we keep it implicitly today-only.
 *
 * Used by UI:
 *   - composeApp/.../ui/screens/admin/AnalyticsDashboardScreen.kt
 *   - composeApp/.../ui/screens/admin/AcademicCalendarScreen.kt
 *   - composeApp/.../ui/screens/admin/DailyAttendanceScreen.kt
 */
package com.littlebridge.vidyaprayag.feature.school

import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.core.principalUserId
import com.littlebridge.vidyaprayag.db.AttendanceTable
import com.littlebridge.vidyaprayag.db.CalendarEventTable
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.FacultyTable
import com.littlebridge.vidyaprayag.db.HolidayTable
import com.littlebridge.vidyaprayag.db.SchoolTable
import com.littlebridge.vidyaprayag.db.StudentTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// ---------- DTOs ----------

@Serializable
data class AnalyticsResponse(
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("expected_release") val expectedRelease: String
)

@Serializable
data class CalendarEventDto(
    val date: String,
    val day: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("event_title") val eventTitle: String,
    @SerialName("event_description") val eventDescription: String
)

@Serializable
data class CalendarSummary(
    @SerialName("total_working_days") val totalWorkingDays: Int,
    @SerialName("public_holidays") val publicHolidays: Int,
    @SerialName("school_holidays") val schoolHolidays: Int
)

@Serializable
data class CalendarResponse(
    @SerialName("calendar_events") val calendarEvents: List<CalendarEventDto>,
    val summary: CalendarSummary
)

@Serializable
data class HolidayDto(
    val date: String,
    val title: String,
    val type: String
)

@Serializable
data class HolidaysResponse(val holidays: List<HolidayDto>)

@Serializable
data class AttendanceEntry(
    @SerialName("profile_pic") val profilePic: String? = null,
    val name: String,
    val id: String,
    val status: String
)

@Serializable
data class DailyAttendanceResponse(
    val type: String,
    val grade: String? = null,
    @SerialName("present_count") val presentCount: Int,
    @SerialName("absent_count") val absentCount: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("attendance_percentage") val attendancePercentage: String,
    @SerialName("attendance_list") val attendanceList: List<AttendanceEntry>
)

// ---------- helpers ----------

private suspend fun resolveSchoolId(uid: UUID): Long? = dbQuery {
    SchoolTable.selectAll().where { SchoolTable.ownerUserId eq uid }
        .singleOrNull()?.get(SchoolTable.id)?.value
}

private fun pct(num: Int, denom: Int): String =
    if (denom == 0) "0%" else "${"%.1f".format(num * 100.0 / denom)}%"

// ---------- Routing ----------

fun Route.schoolRouting() {
    authenticate("jwt") {
        route("/api/v1/school") {

            // -------- analytics (Coming Soon) --------
            get("/analytics") {
                call.ok(
                    AnalyticsResponse(isAvailable = false, expectedRelease = "Q3 2024"),
                    message = "Analytics feature is coming soon!"
                )
            }

            // -------- calendar --------
            get("/calendar") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val dateStr = call.request.queryParameters["date"] ?: LocalDate.now().toString()
                val view = call.request.queryParameters["view_type"]?.lowercase() ?: "month"
                val standard = call.request.queryParameters["standard"]

                val refDate = runCatching { LocalDate.parse(dateStr) }.getOrElse {
                    call.fail("Invalid date '$dateStr', expected YYYY-MM-DD"); return@get
                }

                val data = dbQuery {
                    val all = CalendarEventTable.selectAll()
                        .where { CalendarEventTable.schoolId eq schoolId }
                        .toList()
                    val filteredByDate = all.filter { row ->
                        val rd = runCatching { LocalDate.parse(row[CalendarEventTable.date]) }.getOrNull()
                            ?: return@filter false
                        when (view) {
                            "week"  -> !rd.isBefore(refDate.minusDays(3)) && !rd.isAfter(refDate.plusDays(3))
                            else    -> YearMonth.from(rd) == YearMonth.from(refDate)
                        }
                    }
                    val filteredByStd = if (standard.isNullOrBlank()) filteredByDate
                        else filteredByDate.filter {
                            val s = it[CalendarEventTable.standard]
                            s == null || s == standard
                        }
                    val events = filteredByStd.map {
                        CalendarEventDto(
                            date = it[CalendarEventTable.date],
                            day = it[CalendarEventTable.day],
                            eventId = it[CalendarEventTable.eventId],
                            eventTitle = it[CalendarEventTable.title],
                            eventDescription = it[CalendarEventTable.description]
                        )
                    }
                    // Summary derived from holiday_list within the same period.
                    val holidaysInPeriod = HolidayTable.selectAll()
                        .where { HolidayTable.schoolId eq schoolId }
                        .mapNotNull { row ->
                            val hd = runCatching { LocalDate.parse(row[HolidayTable.date]) }.getOrNull()
                            if (hd != null && YearMonth.from(hd) == YearMonth.from(refDate)) row else null
                        }
                    val publicHolidays = holidaysInPeriod.count { it[HolidayTable.type] == "Public" }
                    val schoolHolidays = holidaysInPeriod.count { it[HolidayTable.type] == "School" }
                    val daysInMonth = YearMonth.from(refDate).lengthOfMonth()
                    val totalWorking = daysInMonth - publicHolidays - schoolHolidays -
                        // count weekend days
                        (1..daysInMonth).count { d ->
                            val day = refDate.withDayOfMonth(d).dayOfWeek.value
                            day == 6 || day == 7
                        }

                    CalendarResponse(
                        calendarEvents = events,
                        summary = CalendarSummary(
                            totalWorkingDays = totalWorking.coerceAtLeast(0),
                            publicHolidays = publicHolidays,
                            schoolHolidays = schoolHolidays
                        )
                    )
                }
                call.ok(data, message = "Academic calendar fetched successfully")
            }

            // -------- holidays --------
            get("/holidays") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val filter = call.request.queryParameters["filter_type"]?.lowercase() ?: "yearly"

                val list = dbQuery {
                    HolidayTable.selectAll()
                        .where { (HolidayTable.schoolId eq schoolId) and (HolidayTable.frequency eq filter) }
                        .orderBy(HolidayTable.date)
                        .map {
                            HolidayDto(
                                date = it[HolidayTable.date],
                                title = it[HolidayTable.title],
                                type = it[HolidayTable.type]
                            )
                        }
                }
                call.ok(HolidaysResponse(list), message = "Holidays list fetched")
            }

            // -------- attendance/daily --------
            get("/attendance/daily") {
                val uid = call.principalUserId()?.let { UUID.fromString(it) } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val schoolId = resolveSchoolId(uid) ?: run {
                    call.fail("User not associated with any school", HttpStatusCode.NotFound); return@get
                }
                val type = call.request.queryParameters["type"]?.lowercase() ?: "student"
                val grade = call.request.queryParameters["grade"]
                val today = LocalDate.now().toString()

                if (type !in listOf("student", "faculty")) {
                    call.fail("type must be 'student' or 'faculty'"); return@get
                }
                if (type == "student" && grade.isNullOrBlank()) {
                    call.fail("grade is required when type=student"); return@get
                }

                val data = dbQuery {
                    val records = AttendanceTable.selectAll()
                        .where {
                            val base = (AttendanceTable.schoolId eq schoolId) and
                                       (AttendanceTable.date eq today) and
                                       (AttendanceTable.type eq type)
                            if (type == "student") base and (AttendanceTable.grade eq grade!!)
                            else base
                        }
                        .toList()

                    // Join to name/profile-pic via the people tables.
                    val entries = records.map { row ->
                        val pid = row[AttendanceTable.personId]
                        val (name, pic) = if (type == "student") {
                            val s = StudentTable.selectAll()
                                .where { StudentTable.externalId eq pid }.singleOrNull()
                            (s?.get(StudentTable.name) ?: pid) to s?.get(StudentTable.profilePic)
                        } else {
                            val f = FacultyTable.selectAll()
                                .where { FacultyTable.externalId eq pid }.singleOrNull()
                            (f?.get(FacultyTable.name) ?: pid) to f?.get(FacultyTable.profilePic)
                        }
                        AttendanceEntry(
                            profilePic = pic,
                            name = name,
                            id = pid,
                            status = row[AttendanceTable.status]
                        )
                    }
                    val present = entries.count { it.status == "present" }
                    val absent = entries.count { it.status == "absent" }
                    DailyAttendanceResponse(
                        type = type,
                        grade = if (type == "student") grade else null,
                        presentCount = present,
                        absentCount = absent,
                        totalCount = entries.size,
                        attendancePercentage = pct(present, entries.size),
                        attendanceList = entries
                    )
                }
                call.ok(data, message = "Daily attendance fetched successfully")
            }
        }
    }
}
