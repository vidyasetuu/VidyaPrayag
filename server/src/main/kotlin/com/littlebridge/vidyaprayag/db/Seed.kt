/*
 * File: Seed.kt
 * Module: db
 * Purpose:
 *   Idempotent first-run data seeder. Populates:
 *     - cms_landing_content      (Common Landing Page strings)
 *     - app_config               (Splash / version / flags)
 *     - one demo SchoolTable row + its philosophy, media, storage, classes
 *     - 3 demo announcements, 5 demo enquiries, 2 calendar events, 4 holidays
 *     - 6 demo students + 2 faculty + today's attendance
 *
 *   Each block runs only if its target table is empty, so re-running the
 *   server on an existing DB is safe and non-destructive.
 *
 * Used by: DatabaseFactory.init() → Seed.populateIfEmpty()
 *
 * Why JSON blobs in KV stores?
 *   The Landing and AppConfig responses are nested objects. Storing the
 *   serialised JSON for each "section" (e.g. parent_info, version_check)
 *   under a single key keeps the table trivially simple. The Routing layer
 *   parses the JSON back into typed DTOs before serving.
 */
package com.littlebridge.vidyaprayag.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

object Seed {

    fun populateIfEmpty() = transaction {
        seedLanding()
        seedAppConfig()
        seedDemoSchool()
    }

    // ---------- Landing CMS ----------
    private fun seedLanding() {
        if (LandingContentTable.selectAll().count() > 0L) return
        val pairs = mapOf(
            "top_tagline"          to "Education with Trust.",
            "sub_tagline"          to "Progress with Purpose.",
            "tos_link"             to "https://vidyaprayag.com/terms",
            "privacy_policy_link"  to "https://vidyaprayag.com/privacy",
            "login_modes"          to """["EMAIL","MOBILE","GOOGLE","APPLE"]""",
            "parent_info"          to """{
                "top_tagline": "FOR PARENTS",
                "sub_tagline": "Find the perfect school for your child's unique journey",
                "list_of_features": ["Data-driven insights","Verified institutional profiles"],
                "list_of_sub_features": ["Match matching score","Direct inquiry"]
            }""".trimIndent(),
            "school_info"          to """{
                "top_tagline": "FOR SCHOOLS",
                "sub_tagline": "Scale excellence with intelligence.",
                "list_of_features": ["Institutional management tools","Growth tracking"],
                "list_of_sub_features": ["Predictive analysis","Automated workflows"]
            }""".trimIndent(),
            "list_of_offerings"    to """[
                {"icon_url":"https://cdn.vidyaprayag.com/icons/intel.png","heading":"Next-Gen Intelligence","description":"Proprietary systems powering the ecosystem.","is_live":true},
                {"icon_url":"https://cdn.vidyaprayag.com/icons/secure.png","heading":"Secure Infrastructure","description":"End-to-end encrypted data pipeline.","is_live":true}
            ]""".trimIndent(),
            "list_of_portals"      to """[
                {"icon_url":"https://cdn.vidyaprayag.com/icons/parent.png","heading":"Parent Portal","description":"Monitor your child's holistic growth.","is_live":true},
                {"icon_url":"https://cdn.vidyaprayag.com/icons/admin.png","heading":"Admin Portal","description":"Manage institutional performance and analytics.","is_live":true}
            ]""".trimIndent()
        )
        pairs.forEach { (k, v) ->
            LandingContentTable.insert {
                it[key] = k
                it[value] = v
            }
        }
    }

    // ---------- App config / Splash ----------
    private fun seedAppConfig() {
        if (AppConfigTable.selectAll().count() > 0L) return
        val pairs = mapOf(
            "version_check" to """{
                "current_version": "2.4.0",
                "minimum_required_version": "2.3.5",
                "force_update": false,
                "update_url": "https://play.google.com/store/apps/details?id=com.littlebridge.vidyaprayag",
                "update_message": "A new version with performance improvements is available."
            }""".trimIndent(),
            "maintenance" to """{
                "is_under_maintenance": false,
                "estimated_end_time": "2024-10-24T10:00:00Z",
                "message": "We're upgrading our servers. We'll be back shortly."
            }""".trimIndent(),
            "flags" to """{
                "is_whatsapp_sync_enabled": true,
                "show_scholarships": true,
                "is_ai_narrative_live": true,
                "theme_mode_override": "SYSTEM",
                "support_contact": "+91-9876543210"
            }""".trimIndent()
        )
        pairs.forEach { (k, v) ->
            AppConfigTable.insert {
                it[key] = k
                it[value] = v
            }
        }
    }

    // ---------- Demo school + everything tied to it ----------
    private fun seedDemoSchool() {
        if (SchoolTable.selectAll().count() > 0L) return

        // Demo admin owner (if not present). Contact intentionally distinct to
        // avoid colliding with manually-created accounts.
        val ownerId: UUID = UserTable.selectAll()
            .where { UserTable.contact eq "demo.admin@vidyaprayag.com" }
            .singleOrNull()
            ?.get(UserTable.id)
            ?: UserTable.insert {
                it[name] = "Demo Admin"
                it[contact] = "demo.admin@vidyaprayag.com"
                it[email] = "demo.admin@vidyaprayag.com"
                it[role] = "ADMIN"
                it[profileCompleted] = true
            } get UserTable.id

        val now = LocalDateTime.now()
        val schoolId = SchoolTable.insertAndGetId {
            it[ownerUserId] = ownerId
            it[name] = "St. Xavier Academy"
            it[boardAffiliation] = "CBSE"
            it[officialEmail] = "info@stxavier.edu"
            it[contactNumber] = "9876543210"
            it[address] = "Education Lane, Knowledge Hub, Sector 42, New Delhi - 110001"
            it[isVerified] = true
            it[onboardingStatus] = "COMPLETED"
            it[createdAt] = now
            it[updatedAt] = now
        }.value

        // Philosophy
        SchoolPhilosophyTable.insert {
            it[SchoolPhilosophyTable.schoolId] = schoolId
            it[coreMission] = "Empowering young minds through holistic education."
            it[learningModel] = "Inquiry-based collaborative learning"
            it[primaryLanguage] = "English"
            it[publicProfile] = true
        }

        // Media
        listOf(
            "https://assets.vidyaprayag.com/gallery/img1.jpg" to "IMAGE",
            "https://assets.vidyaprayag.com/gallery/img2.jpg" to "IMAGE",
            "https://vidyaprayag.com/videos/tour1.mp4"        to "VIDEO",
            "https://vidyaprayag.com/videos/lab_intro.mp4"    to "VIDEO"
        ).forEachIndexed { idx, (url, k) ->
            SchoolMediaTable.insert {
                it[SchoolMediaTable.schoolId] = schoolId
                it[kind] = k
                it[SchoolMediaTable.url] = url
                it[position] = idx
            }
        }

        StorageMetricsTable.insert {
            it[StorageMetricsTable.schoolId] = schoolId
            it[totalStorage] = "10 GB"
            it[storageUsed] = "2.4 GB"
        }

        // Classes + subjects
        val class10 = ClassTable.insertAndGetId {
            it[ClassTable.schoolId] = schoolId
            it[code] = "C10"
            it[name] = "Grade 10"
            it[sections] = "A,B"
        }.value
        listOf(
            Triple("Modern Physics", "PHY-10", "Dr. Robert Chen"),
            Triple("Calculus", "MAT-10", "Dr. Sarah Henderson"),
            Triple("World History", "HIS-10", "Mr. Anil Verma"),
            Triple("English Literature", "ENG-10", "Ms. Priya Iyer"),
            Triple("Computer Science", "CS-10", "Mr. Karan Mehta")
        ).forEach { (n, c, t) ->
            SubjectTable.insert {
                it[classId] = class10
                it[subName] = n
                it[subCode] = c
                it[teacherAssigned] = t
            }
        }
        ClassTable.insertAndGetId {
            it[ClassTable.schoolId] = schoolId
            it[code] = "C05"
            it[name] = "Grade 5"
            it[sections] = "A,B,C"
        }

        // Announcements
        listOf(
            listOf("Holidays","EVT_101","Summer Vacation","Starting from 1st June",
                "The school will remain closed for summer vacation from June 1st to June 30th. Enjoy your holidays!",
                "https://assets.vidyaprayag.com/images/summer_vacation.png","2024-06-01"),
            listOf("PTM","EVT_102","Parent Teacher Meeting","Quarterly Result Discussion",
                "Discuss the academic progress of your ward with the class teacher in the upcoming PTM.",
                null,"2024-05-25"),
            listOf("Events","EVT_103","Annual Sports Day","Let the games begin",
                "Join us for a day filled with athletic competitions and school spirit.",
                "https://assets.vidyaprayag.com/images/sports_day.jpg","2024-11-15")
        ).forEach { row ->
            AnnouncementTable.insert {
                it[AnnouncementTable.schoolId] = schoolId
                it[type] = row[0] as String
                it[eventId] = row[1] as String
                it[title] = row[2] as String
                it[subTitle] = row[3] as String?
                it[description] = row[4] as String
                it[eventImage] = row[5] as String?
                it[date] = row[6] as String
                it[createdAt] = now
            }
        }

        // Admission enquiries
        listOf(
            listOf("Aarav Sharma","Rajesh Sharma","Grade 5","2024-05-18","new","https://assets.vidyaprayag.com/profiles/student1.jpg"),
            listOf("Ishita Verma","Sanjay Verma","Grade 1","2024-05-15","followup",null),
            listOf("Rohan Gupta","Vikas Gupta","Grade 8","2024-05-12","converted",null),
            listOf("Meera Joshi","Suresh Joshi","Grade 3","2024-05-10","new",null),
            listOf("Kabir Khan","Imran Khan","Grade 10","2024-05-08","followup",null)
        ).forEach { row ->
            AdmissionEnquiryTable.insert {
                it[AdmissionEnquiryTable.schoolId] = schoolId
                it[studentName] = row[0] as String
                it[parentName] = row[1] as String
                it[grade] = row[2] as String
                it[date] = row[3] as String
                it[status] = row[4] as String
                it[profilePic] = row[5] as String?
                it[createdAt] = now
            }
        }

        // Calendar
        listOf(
            listOf("CAL_101","2024-05-01","Wednesday","May Day","Public holiday on account of International Labour Day.", null),
            listOf("CAL_102","2024-05-15","Wednesday","Unit Test 1","First periodic assessment starts for Grade 5.","Grade 5")
        ).forEach { row ->
            CalendarEventTable.insert {
                it[CalendarEventTable.schoolId] = schoolId
                it[eventId] = row[0] as String
                it[date] = row[1] as String
                it[day] = row[2] as String
                it[title] = row[3] as String
                it[description] = row[4] as String
                it[standard] = row[5] as String?
            }
        }

        // Holidays
        listOf(
            listOf("2024-01-26","Republic Day","Public","yearly"),
            listOf("2024-08-15","Independence Day","Public","yearly"),
            listOf("2024-10-02","Gandhi Jayanti","Public","yearly"),
            listOf("2024-12-25","Christmas","Public","yearly")
        ).forEach { row ->
            HolidayTable.insert {
                it[HolidayTable.schoolId] = schoolId
                it[date] = row[0] as String
                it[title] = row[1] as String
                it[type] = row[2] as String
                it[frequency] = row[3] as String
            }
        }

        // Faculty
        listOf(
            "FAC_001" to "Dr. Robert Chen",
            "FAC_002" to "Dr. Sarah Henderson"
        ).forEach { (extId, n) ->
            FacultyTable.insert {
                it[FacultyTable.schoolId] = schoolId
                it[externalId] = extId
                it[name] = n
                it[profilePic] = "https://assets.vidyaprayag.com/profiles/${extId.lowercase()}.jpg"
            }
        }

        // Students (Grade 5)
        listOf(
            "ST_501" to "Aarav Sharma",
            "ST_502" to "Isha Kapoor",
            "ST_503" to "Rohit Singh",
            "ST_504" to "Priya Patel",
            "ST_505" to "Aman Khan",
            "ST_506" to "Sneha Rao"
        ).forEachIndexed { idx, (extId, n) ->
            StudentTable.insert {
                it[StudentTable.schoolId] = schoolId
                it[externalId] = extId
                it[name] = n
                it[grade] = "Grade 5"
                it[profilePic] = "https://assets.vidyaprayag.com/profiles/${extId.lowercase()}.jpg"
            }
            AttendanceTable.insert {
                it[AttendanceTable.schoolId] = schoolId
                it[date] = java.time.LocalDate.now().toString()
                it[type] = "student"
                it[personId] = extId
                it[grade] = "Grade 5"
                it[status] = if (idx == 1) "absent" else "present"
            }
        }
    }

}
