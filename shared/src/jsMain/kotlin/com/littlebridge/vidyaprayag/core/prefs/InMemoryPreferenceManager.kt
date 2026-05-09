package com.littlebridge.vidyaprayag.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryPreferenceManager : PreferenceRepository {
    private val themeName = MutableStateFlow("LIGHT")

    override fun getThemeName(): Flow<String> {
        return themeName
    }

    override suspend fun setThemeName(name: String) {
        themeName.value = name
    }
}
