/*
 * File: ErrorHandling.kt
 * Module: core
 * Purpose:
 *   Installs Ktor's StatusPages so that any uncaught exception from a route
 *   becomes a well-formed { success:false, message } envelope instead of an
 *   HTML stack trace. Keeps mobile clients happy and matches the spec's
 *   error response shape.
 *
 * Maps:
 *   - ContentTransformationException → 400 Bad Request ("Invalid request body")
 *   - IllegalArgumentException       → 400 Bad Request (message preserved)
 *   - NotFoundException              → 404 Not Found
 *   - Throwable (catch-all)          → 500 Internal Server Error
 *
 * Used by:
 *   - Application.kt → install(StatusPages) { configureErrorHandling() }
 */
package com.littlebridge.vidyaprayag.core

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun StatusPagesConfig.configureErrorHandling() {
    exception<ContentTransformationException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            ApiError(message = "Invalid request body: ${cause.message ?: "malformed JSON"}")
        )
    }
    exception<IllegalArgumentException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            ApiError(message = cause.message ?: "Bad request")
        )
    }
    exception<NotFoundException> { call, cause ->
        call.respond(
            HttpStatusCode.NotFound,
            ApiError(message = cause.message ?: "Resource not found")
        )
    }
    exception<Throwable> { call, cause ->
        // Log to stderr; production should pipe this to a real logger / Sentry.
        System.err.println("[VidyaPrayag] Unhandled error on ${call.request.uri}: ${cause.message}")
        cause.printStackTrace()
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiError(message = "Something went wrong. Please try again later.")
        )
    }

    status(HttpStatusCode.NotFound) { call, _ ->
        call.respond(
            HttpStatusCode.NotFound,
            ApiError(message = "Endpoint not found: ${call.request.uri}")
        )
    }
}
