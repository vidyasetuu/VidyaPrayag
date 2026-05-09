package com.littlebridge.vidyaprayag.feature.schools.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class School(
    val id: String,
    val name: String,
    val location: String,
    val board: String,
    val description: String,
    val imageUrl: String
)
