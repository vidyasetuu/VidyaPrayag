package com.littlebridge.vidyaprayag.feature.schools.domain.usecase

import com.littlebridge.vidyaprayag.feature.schools.domain.model.School
import com.littlebridge.vidyaprayag.feature.schools.domain.repository.SchoolRepository
import kotlinx.coroutines.flow.Flow

class GetSchoolsUseCase(
    private val repository: SchoolRepository
) {
    operator fun invoke(): Flow<List<School>> {
        return repository.getSchools()
    }

    suspend fun refresh() {
        repository.refreshSchools()
    }
}
