package com.littlebridge.vidyaprayag.feature.auth.data.repository

import com.littlebridge.vidyaprayag.core.network.NetworkResult
import com.littlebridge.vidyaprayag.feature.auth.data.remote.AuthApi
import com.littlebridge.vidyaprayag.feature.auth.domain.model.*
import com.littlebridge.vidyaprayag.feature.auth.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val api: AuthApi
) : AuthRepository {
    private var cachedSession: AuthResponse? = null

    override suspend fun checkUser(identifier: String): NetworkResult<AuthFlow> {
        return when (val result = api.checkUser(identifier)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.flow)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun signup(request: SignupRequest): NetworkResult<AuthResponse> {
        return when (val result = api.signup(request)) {
            is NetworkResult.Success -> {
                saveSession(result.data)
                NetworkResult.Success(result.data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun login(request: LoginRequest): NetworkResult<AuthResponse> {
        return when (val result = api.login(request)) {
            is NetworkResult.Success -> {
                saveSession(result.data)
                NetworkResult.Success(result.data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun sendOtp(contact: String): NetworkResult<String> {
        return when (val result = api.sendOtp(contact)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.message)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun saveSession(response: AuthResponse) {
        cachedSession = response
    }

    override suspend fun getSession(): AuthResponse? = cachedSession

    override suspend fun logout() {
        cachedSession = null
    }
}
