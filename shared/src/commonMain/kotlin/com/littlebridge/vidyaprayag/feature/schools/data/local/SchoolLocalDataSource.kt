package com.littlebridge.vidyaprayag.feature.schools.data.local

import com.littlebridge.vidyaprayag.feature.schools.domain.model.School
import kotlinx.coroutines.flow.Flow

interface SchoolLocalDataSource {
    fun getAllSchools(): Flow<List<School>>
    suspend fun saveSchools(schools: List<School>)
    suspend fun getSchoolById(id: String): School?
    suspend fun deleteAll()
}
