package com.littlebridge.vidyaprayag.core.prefs

import kotlinx.coroutines.flow.Flow

interface PreferenceRepository {
    fun getThemeName(): Flow<String>
    suspend fun setThemeName(name: String)
}
