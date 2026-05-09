package com.littlebridge.vidyaprayag.di

import com.littlebridge.vidyaprayag.core.prefs.InMemoryPreferenceManager
import com.littlebridge.vidyaprayag.core.prefs.PreferenceRepository
import com.littlebridge.vidyaprayag.feature.schools.data.local.InMemorySchoolLocalDataSource
import com.littlebridge.vidyaprayag.feature.schools.data.local.SchoolLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SchoolLocalDataSource> { InMemorySchoolLocalDataSource() }
    single<PreferenceRepository> { InMemoryPreferenceManager() }
}
