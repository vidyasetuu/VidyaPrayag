/*
 * File: DatabaseFactory.kt
 * Module: db
 * Purpose:
 *   - Initialises the JDBC connection pool (HikariCP).
 *   - Picks Postgres (Supabase / Render) when DATABASE_URL env is set,
 *     otherwise falls back to local SQLite (data.db) so the server boots on
 *     a fresh clone without any configuration.
 *   - Registers ALL Exposed tables via SchemaUtils.createMissingTablesAndColumns
 *     so adding columns to a Table object automatically migrates existing rows
 *     (Postgres) or recreates the local SQLite file.
 *   - Calls Seed.populateIfEmpty() so the CMS / config / demo-school endpoints
 *     return realistic data the first time the app is launched.
 *
 * Used by: Application.kt → DatabaseFactory.init()
 *
 * NOTE for DevOps (manual step):
 *   Set DATABASE_URL (postgres://… or jdbc:postgresql://…) in .env or the
 *   Render dashboard. See .env.example.
 */
package com.littlebridge.vidyaprayag.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    /** All registered tables. Add new Table objects here so they get migrated. */
    private val allTables = arrayOf(
        UserTable,
        SchoolTable,
        OnboardingDraftTable,
        ClassTable,
        SubjectTable,
        AnnouncementTable,
        WhatsappLogTable,
        AdmissionEnquiryTable,
        SchoolPhilosophyTable,
        SchoolMediaTable,
        StorageMetricsTable,
        LandingContentTable,
        AppConfigTable,
        CalendarEventTable,
        HolidayTable,
        FacultyTable,
        StudentTable,
        AttendanceTable
    )

    fun init() {

        val dotenv = dotenv {
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }

        val databaseUrl =
            dotenv["DATABASE_URL"]?.takeIf { it.isNotBlank() }
                ?: System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() }

        val dataSource = if (databaseUrl != null) {
            createPostgresDataSource(databaseUrl)
        } else {
            createSqliteDataSource()
        }

        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(*allTables)
        }

        // Populate landing CMS, app-config, demo school, demo announcements etc.
        // Idempotent — only writes when target tables are empty.
        Seed.populateIfEmpty()
    }

    private fun createPostgresDataSource(databaseUrl: String): HikariDataSource {

        val username = System.getenv("DATABASE_USER")
            ?: dotenv { ignoreIfMalformed = true; ignoreIfMissing = true }["DATABASE_USER"]

        val password = System.getenv("DATABASE_PASSWORD")
            ?: dotenv { ignoreIfMalformed = true; ignoreIfMissing = true }["DATABASE_PASSWORD"]

        val jdbcUrl = if (databaseUrl.startsWith("jdbc:postgresql://")) databaseUrl else "jdbc:$databaseUrl"

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    private fun createSqliteDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:data.db"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            validate()
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
