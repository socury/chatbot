package com.authtemplate.domain.auth.dto.response

data class AuthTokenResponse (
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)