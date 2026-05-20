/*
 * File: UserTable.kt
 * Module: db
 * Purpose:
 *   Exposed table for users (ADMIN | PARENT | TEACHER). The original schema
 *   (id, name, contact, email, phone, password_hash, role) is preserved for
 *   backward compatibility with the existing /auth routes; new optional
 *   columns are added (nullable + defaults) so SchemaUtils can ALTER the
 *   existing SQLite/Postgres tables in place without a destructive migration.
 *
 * New columns:
 *   - profile_pic        : avatar URL shown in /user/details
 *   - profile_completed  : false → app routes to onboarding after login
 *   - refresh_token      : opaque rotating refresh token from JwtConfig
 *
 * Used by:
 *   - feature/auth/AuthRouting.kt     (signup, login, check-user)
 *   - feature/user/UserRouting.kt     (GET /user/details)
 *   - core/SecurityModule.kt          (JWT subject = user.id.toString())
 */
package com.littlebridge.vidyaprayag.db

import org.jetbrains.exposed.sql.Table

object UserTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val contact = varchar("contact", 255).uniqueIndex() // Primary identifier (email or phone)
    val email = varchar("email", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val role = varchar("role", 50)
    val isPhoneVerified = bool("is_phone_verified").default(false)
    val isEmailVerified = bool("is_email_verified").default(false)
    val profilePic = text("profile_pic").nullable()
    val profileCompleted = bool("profile_completed").default(false)
    val refreshToken = text("refresh_token").nullable()

    override val primaryKey = PrimaryKey(id)
}
