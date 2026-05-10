package com.littlebridge.vidyaprayag.feature.auth.domain.repository

import com.littlebridge.vidyaprayag.core.network.NetworkResult
import com.littlebridge.vidyaprayag.feature.auth.domain.model.*

interface AuthRepository {
    suspend fun checkUser(identifier: String): NetworkResult<AuthFlow>
    suspend fun signup(request: SignupRequest): NetworkResult<AuthResponse>
    suspend fun login(request: LoginRequest): NetworkResult<AuthResponse>
    suspend fun sendOtp(contact: String): NetworkResult<String>
    suspend fun saveSession(response: AuthResponse)
    suspend fun getSession(): AuthResponse?
    suspend fun logout()
}
