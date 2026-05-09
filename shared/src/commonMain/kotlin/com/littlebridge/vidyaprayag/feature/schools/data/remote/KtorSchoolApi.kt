package com.littlebridge.vidyaprayag.feature.schools.data.remote

import com.littlebridge.vidyaprayag.feature.schools.domain.model.School
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class KtorSchoolApi(
    private val client: HttpClient
) {
    // In a real app, this would be an actual URL.
    // For now, we simulate a network response.
    suspend fun fetchSchools(): List<School> {
        // Mocking behavior for demonstration
        return listOf(
            School("1", "Academix Primary", "Mumbai", "CBSE", "Excellence in early education.", "https://example.com/school1.jpg"),
            School("2", "GlobalView International", "Delhi", "IB", "A window to the world.", "https://example.com/school2.jpg")
        )
    }
}
