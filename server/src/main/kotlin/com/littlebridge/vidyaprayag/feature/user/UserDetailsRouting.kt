/*
 * File: UserDetailsRouting.kt
 * Module: feature.user
 *
 * Endpoints implemented:
 *   GET /api/v1/user/details        (JWT required)
 *
 * Spec ref: vidya_prayag_api_spec.artifact.md §Module: Role-Specific Experience
 *           §Get User Details & Onboarding State
 *
 * Purpose:
 *   "Source of Truth" the app calls after login (or on cold start while a
 *   valid token exists) to decide:
 *     - whether to send the user to onboarding
 *     - which side-drawer features to show
 *     - which themes are unlocked for the account
 *
 * Onboarding step status logic:
 *   Step 1 (BASIC)     COMPLETED  ↔ school.name + boardAffiliation are set
 *   Step 2 (BRANDING)  COMPLETED  ↔ school.logoUrl IS NOT NULL
 *   Step 3 (ACADEMIC)  COMPLETED  ↔ at least 1 row in school_classes
 *   Step 4 (REVIEW)    COMPLETED  ↔ school.onboarding_status = 'COMPLETED'
 *   Subsequent steps become PENDING; later ones LOCKED until the prior is done.
 *
 * Used by mobile UI:
 *   - composeApp/.../ui/screens/admin/SchoolDashboardScreen.kt (and parent equivalent)
 *   - shared/.../presentation/MainViewModel.kt drives the post-login routing
 */
package com.littlebridge.vidyaprayag.feature.user

import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.core.principalUserId
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.ClassTable
import com.littlebridge.vidyaprayag.db.SchoolTable
import com.littlebridge.vidyaprayag.db.UserTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Serializable
data class PersonalDetails(
    val role: String,
    val id: String,
    val name: String,
    @SerialName("profile_pic") val profilePic: String? = null,
    val email: String? = null,
    val mobile: String? = null
)

@Serializable
data class OnboardingStepDto(
    val name: String,
    val description: String,
    val status: String,           // COMPLETED | PENDING | LOCKED
    val icon: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_required") val isRequired: Boolean
)

@Serializable
data class SupportInfo(
    val name: String,
    val description: String,
    @SerialName("contact_number") val contactNumber: String,
    @SerialName("contact_email") val contactEmail: String,
    val icon: String
)

@Serializable
data class MenuFeature(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class AppTheme(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class OnboardingDetails(
    @SerialName("onboarding_status") val onboardingStatus: String,
    @SerialName("total_steps") val totalSteps: Int,
    @SerialName("list_of_steps") val listOfSteps: List<OnboardingStepDto>,
    @SerialName("support_info") val supportInfo: SupportInfo,
    @SerialName("tutorial_video_link") val tutorialVideoLink: String,
    @SerialName("menu_features") val menuFeatures: List<MenuFeature>,
    @SerialName("app_themes") val appThemes: List<AppTheme>,
    @SerialName("tos_link") val tosLink: String,
    @SerialName("privacy_policy_link") val privacyPolicyLink: String
)

@Serializable
data class UserDetailsResponse(
    @SerialName("personal_details") val personalDetails: PersonalDetails,
    @SerialName("onboarding_details") val onboardingDetails: OnboardingDetails
)

private val DEFAULT_SUPPORT = SupportInfo(
    name = "VidyaPrayag Success Team",
    description = "Available 9am - 6pm for setup help",
    contactNumber = "+91-9988776655",
    contactEmail = "support@vidyaprayag.com",
    icon = "support_agent"
)

private val DEFAULT_MENU = listOf(
    MenuFeature("Analytics", isEnabled = true, isLive = true),
    MenuFeature("PTM Management", isEnabled = true, isLive = true),
    MenuFeature("Scholarships", isEnabled = false, isLive = false),
    MenuFeature("Attendance", isEnabled = true, isLive = true),
    MenuFeature("Calendar", isEnabled = true, isLive = true)
)

private val DEFAULT_THEMES = listOf(
    AppTheme("LIGHT", isEnabled = true, isLive = true),
    AppTheme("DARK", isEnabled = true, isLive = true),
    AppTheme("MIDNIGHT", isEnabled = true, isLive = true)
)

fun Route.userDetailsRouting() {
    authenticate("jwt") {
        route("/api/v1/user") {
            get("/details") {
                val uid = call.principalUserId() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val userUuid = runCatching { UUID.fromString(uid) }.getOrNull() ?: run {
                    call.fail("Malformed token subject", HttpStatusCode.Unauthorized); return@get
                }

                val payload = dbQuery {
                    val u = UserTable.selectAll().where { UserTable.id eq userUuid }.singleOrNull()
                        ?: return@dbQuery null

                    val personal = PersonalDetails(
                        role = u[UserTable.role],
                        id = u[UserTable.id].toString(),
                        name = u[UserTable.name],
                        profilePic = u[UserTable.profilePic],
                        email = u[UserTable.email],
                        mobile = u[UserTable.phone]
                    )

                    // Find this user's school (if ADMIN). PARENT/TEACHER will simply
                    // get an empty/READY onboarding block.
                    val school = SchoolTable.selectAll()
                        .where { SchoolTable.ownerUserId eq userUuid }
                        .singleOrNull()

                    val schoolId = school?.get(SchoolTable.id)?.value
                    val basicsDone = school != null &&
                        !school[SchoolTable.boardAffiliation].isNullOrBlank() &&
                        school[SchoolTable.name].isNotBlank()
                    val brandingDone = school?.get(SchoolTable.logoUrl)?.isNotBlank() == true
                    val academicDone = schoolId?.let {
                        ClassTable.selectAll().where { ClassTable.schoolId eq it }.count() > 0L
                    } ?: false
                    val finalDone = school?.get(SchoolTable.onboardingStatus) == "COMPLETED"

                    fun statusFor(done: Boolean, prevDone: Boolean) = when {
                        done -> "COMPLETED"
                        prevDone -> "PENDING"
                        else -> "LOCKED"
                    }

                    val steps = listOf(
                        OnboardingStepDto("Institutional Basics", "Core school info and identity",
                            statusFor(basicsDone, true), "school", isEnabled = true, isRequired = true),
                        OnboardingStepDto("Branding & Visuals", "Logo and portal themes",
                            statusFor(brandingDone, basicsDone), "palette", isEnabled = basicsDone, isRequired = true),
                        OnboardingStepDto("Academic Structure", "Grade levels and curricula",
                            statusFor(academicDone, brandingDone), "history_edu", isEnabled = brandingDone, isRequired = true),
                        OnboardingStepDto("Launch & Review", "Final check & go live",
                            statusFor(finalDone, academicDone), "rocket_launch", isEnabled = academicDone, isRequired = true)
                    )

                    val overall = when {
                        finalDone -> "COMPLETED"
                        basicsDone || brandingDone || academicDone -> "IN_PROGRESS"
                        else -> "NOT_STARTED"
                    }

                    val ob = OnboardingDetails(
                        onboardingStatus = overall,
                        totalSteps = steps.size,
                        listOfSteps = steps,
                        supportInfo = DEFAULT_SUPPORT,
                        tutorialVideoLink = "https://vidyaprayag.com/tutorials/onboarding",
                        menuFeatures = DEFAULT_MENU,
                        appThemes = DEFAULT_THEMES,
                        tosLink = "https://vidyaprayag.com/terms",
                        privacyPolicyLink = "https://vidyaprayag.com/privacy"
                    )

                    UserDetailsResponse(personalDetails = personal, onboardingDetails = ob)
                }

                if (payload == null) {
                    call.fail("User not found", HttpStatusCode.NotFound)
                } else {
                    call.ok(payload, message = "User details fetched")
                }
            }
        }
    }
}
