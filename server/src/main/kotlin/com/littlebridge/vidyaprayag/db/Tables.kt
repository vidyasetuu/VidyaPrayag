/*
 * File: Tables.kt
 * Module: db
 * Purpose:
 *   All Exposed table definitions for the VidyaPrayag backend in one place
 *   (small enough that splitting per-feature would be over-engineering, and
 *   keeping them co-located lets `SchemaUtils.createMissingTablesAndColumns`
 *   pick them up via one call in DatabaseFactory.kt).
 *
 * Tables defined here (one-to-one with the two API spec artifacts):
 *   - SchoolTable                 : core school record (1 per ADMIN onboarding)
 *   - OnboardingDraftTable        : key-value draft store per (user, step, key)
 *   - ClassTable                  : ACADEMIC step — Grade 5, Grade 10, …
 *   - SubjectTable                : subjects under a class with teacher names
 *   - AnnouncementTable           : Holidays / PTM / Events / Special / Remainder
 *   - AdmissionEnquiryTable       : leads in the school CRM
 *   - SchoolPhilosophyTable       : core_mission / learning_model / language
 *   - SchoolMediaTable            : gallery image URLs + video tour URLs
 *   - StorageMetricsTable         : per-school gallery storage stats
 *   - LandingContentTable         : KV store for landing-page CMS strings
 *   - AppConfigTable              : KV store for /config/app-status flags
 *   - CalendarEventTable          : academic calendar events
 *   - HolidayTable                : public/school holidays
 *   - AttendanceTable             : daily attendance per (date, person)
 *   - FacultyTable / StudentTable : lookup for attendance lists
 *
 * NOTE on identifiers:
 *   - `users.id` stays UUID (existing UserTable).
 *   - All other rows use Long auto-increment primary keys; cross-table foreign
 *     keys reference user.id (UUID) or school.id (Long) as appropriate.
 */
package com.littlebridge.vidyaprayag.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ---------- School & Onboarding ----------

object SchoolTable : LongIdTable("schools") {
    val ownerUserId      = uuid("owner_user_id").index()
    val name             = varchar("name", 255)
    val boardAffiliation = varchar("board_affiliation", 100).nullable()
    val officialEmail    = varchar("official_email", 255).nullable()
    val contactNumber    = varchar("contact_number", 50).nullable()
    val countryCode      = varchar("country_code", 8).default("+91")
    val address          = text("address").nullable()
    val logoUrl          = text("logo_url").nullable()
    val themeColor       = varchar("theme_color", 32).nullable()
    val isVerified       = bool("is_verified").default(false)
    val onboardingStatus = varchar("onboarding_status", 32).default("IN_PROGRESS") // NOT_STARTED|IN_PROGRESS|COMPLETED
    val createdAt        = datetime("created_at")
    val updatedAt        = datetime("updated_at")
}

/** KV store of in-progress onboarding drafts, indexed by (user, step, key). */
object OnboardingDraftTable : LongIdTable("school_onboarding_drafts") {
    val userId   = uuid("user_id").index()
    val stepType = varchar("step_type", 32) // BASIC | BRANDING | ACADEMIC | REVIEW
    val key      = varchar("key", 100)
    val value    = text("value")
    init {
        uniqueIndex("ux_draft_user_step_key", userId, stepType, key)
    }
}

object ClassTable : LongIdTable("school_classes") {
    val schoolId = long("school_id").index()
    val code     = varchar("code", 32)   // e.g. C10
    val name     = varchar("name", 64)   // e.g. Grade 10
    val sections = varchar("sections", 255).default("") // CSV: "A,B,C"
    init {
        uniqueIndex("ux_class_school_code", schoolId, code)
    }
}

object SubjectTable : LongIdTable("school_subjects") {
    val classId         = long("class_id").index()
    val subName         = varchar("sub_name", 128)
    val subCode         = varchar("sub_code", 64)
    val teacherAssigned = varchar("teacher_assigned", 128).nullable()
}

// ---------- Announcements ----------

object AnnouncementTable : LongIdTable("announcements") {
    val schoolId   = long("school_id").index()
    val type       = varchar("type", 32) // Holidays | PTM | Events | Special | Remainder
    val eventId    = varchar("event_id", 64).uniqueIndex()
    val title      = varchar("title", 255)
    val subTitle   = varchar("sub_title", 255).nullable()
    val description= text("description")
    val eventImage = text("event_image").nullable()
    val date       = varchar("date", 16) // YYYY-MM-DD
    val syncedToWa = bool("synced_to_wa").default(false)
    val createdAt  = datetime("created_at")
}

object WhatsappLogTable : LongIdTable("whatsapp_logs") {
    val schoolId      = long("school_id").index()
    val announcementId= varchar("announcement_id", 64)
    val jobId         = varchar("job_id", 64).index()
    val phone         = varchar("phone", 32)
    val status        = varchar("status", 32).default("QUEUED") // QUEUED|SENT|FAILED
    val createdAt     = datetime("created_at")
}

// ---------- Admission Enquiries ----------

object AdmissionEnquiryTable : LongIdTable("admission_enquiries") {
    val schoolId    = long("school_id").index()
    val studentName = varchar("student_name", 128)
    val parentName  = varchar("parent_name", 128)
    val grade       = varchar("class", 64) // SQL column literally "class" — quoted by Exposed
    val date        = varchar("date", 16)  // YYYY-MM-DD
    val status      = varchar("status", 32) // new | followup | converted
    val profilePic  = text("profile_pic").nullable()
    val createdAt   = datetime("created_at")
}

// ---------- School Profile (Philosophy / Media / Storage) ----------

object SchoolPhilosophyTable : Table("school_philosophy") {
    val schoolId        = long("school_id")
    val coreMission     = text("core_mission").nullable()
    val learningModel   = text("learning_model").nullable()
    val primaryLanguage = varchar("primary_language", 64).nullable()
    val publicProfile   = bool("public_profile").default(true)
    override val primaryKey = PrimaryKey(schoolId)
}

object SchoolMediaTable : LongIdTable("school_media") {
    val schoolId = long("school_id").index()
    val kind     = varchar("kind", 16) // "IMAGE" | "VIDEO"
    val url      = text("url")
    val position = integer("position").default(0)
}

object StorageMetricsTable : Table("storage_metrics") {
    val schoolId     = long("school_id")
    val totalStorage = varchar("total_storage", 32).default("10 GB")
    val storageUsed  = varchar("storage_used", 32).default("0 B")
    override val primaryKey = PrimaryKey(schoolId)
}

// ---------- Landing CMS + App Config (KV stores) ----------

object LandingContentTable : Table("cms_landing_content") {
    val key   = varchar("key", 100)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

object AppConfigTable : Table("app_config") {
    val key   = varchar("key", 100)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

// ---------- Drawer / Calendar / Holidays / Attendance ----------

object CalendarEventTable : LongIdTable("academic_calendar") {
    val schoolId    = long("school_id").index()
    val eventId     = varchar("event_id", 64).uniqueIndex()
    val date        = varchar("date", 16) // YYYY-MM-DD
    val day         = varchar("day", 16)  // Monday, Tuesday, …
    val title       = varchar("event_title", 255)
    val description = text("event_description")
    val standard    = varchar("standard", 64).nullable() // Grade 5, Grade 10 …
}

object HolidayTable : LongIdTable("holiday_list") {
    val schoolId   = long("school_id").index()
    val date       = varchar("date", 16) // YYYY-MM-DD
    val title      = varchar("title", 128)
    val type       = varchar("type", 32) // Public | School
    val frequency  = varchar("frequency", 16) // weekly | monthly | yearly
}

object FacultyTable : LongIdTable("faculty") {
    val schoolId   = long("school_id").index()
    val externalId = varchar("external_id", 64).uniqueIndex() // FAC_001
    val name       = varchar("name", 128)
    val profilePic = text("profile_pic").nullable()
}

object StudentTable : LongIdTable("students") {
    val schoolId   = long("school_id").index()
    val externalId = varchar("external_id", 64).uniqueIndex() // ST_501
    val name       = varchar("name", 128)
    val grade      = varchar("grade", 64) // Grade 5
    val profilePic = text("profile_pic").nullable()
}

object AttendanceTable : LongIdTable("attendance_records") {
    val schoolId   = long("school_id").index()
    val date       = varchar("date", 16) // YYYY-MM-DD
    val type       = varchar("type", 16) // student | faculty
    val personId   = varchar("person_id", 64) // ST_501 or FAC_001
    val grade      = varchar("grade", 64).nullable() // null for faculty
    val status     = varchar("status", 16) // present | absent
    init {
        uniqueIndex("ux_attendance_unique", schoolId, date, type, personId)
    }
}
