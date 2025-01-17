package com.chatbot.domain.auth.service

import com.chatbot.domain.auth.dto.request.AuthLoginRequest
import com.chatbot.domain.auth.dto.request.AuthRefreshRequest
import com.chatbot.domain.auth.dto.request.AuthSignupRequest
import com.chatbot.domain.auth.dto.response.AuthTokenResponse
import com.chatbot.domain.user.entity.UserEntity
import com.chatbot.domain.user.exception.UserErrorCode
import com.chatbot.domain.user.repository.UserRepository
import com.chatbot.global.dto.BaseResponse
import com.chatbot.global.exception.CustomException
import com.chatbot.global.jwt.JwtUtil
import com.chatbot.global.jwt.execption.JwtErrorCode
import com.chatbot.global.redis.RedisService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService (
    private val userRepository: UserRepository,
    private val jwtUtil: JwtUtil,
    private val redisService: RedisService
) {
    fun signup(authSignupRequest: AuthSignupRequest) : BaseResponse<Unit>  {
        if (userRepository.existsByUsername(authSignupRequest.username))
            throw CustomException(UserErrorCode.USER_ALREADY_EXIST)

        val user = UserEntity(
            username = authSignupRequest.username,
            password = BCryptPasswordEncoder().encode(authSignupRequest.password),
        )
        userRepository.save(user)

        return BaseResponse(
            message = "회원가입 성공"
        )
    }

    fun login(authLoginRequest: AuthLoginRequest) : BaseResponse<AuthTokenResponse> {
        var user = userRepository.findByUsername(authLoginRequest.username).orElseThrow {
            throw CustomException(UserErrorCode.USER_NOT_FOUND)
        }

        if (!BCryptPasswordEncoder().matches(authLoginRequest.password, user.password))
            throw CustomException(UserErrorCode.USER_NOT_MATCH)

        return BaseResponse(
            message = "로그인 성공",
            data = jwtUtil.generateToken(user)
        )
    }

    fun refresh(authRefreshRequest: AuthRefreshRequest) : BaseResponse<AuthTokenResponse> {
        if (authRefreshRequest.refreshToken.isEmpty())
            throw CustomException(JwtErrorCode.JWT_EMPTY_EXCEPTION)

        val id: Long = jwtUtil.getUserId(authRefreshRequest. refreshToken)
        val user = userRepository.findById(id).orElseThrow {
            throw CustomException(UserErrorCode.USER_NOT_FOUND)
        }

        val cachedToken = redisService.getRefreshToken(id)

        if (cachedToken != authRefreshRequest.refreshToken)
            throw CustomException(JwtErrorCode.JWT_TOKEN_ERROR)

        return BaseResponse(
            message = "리프레시 성공",
            data = jwtUtil.generateToken(user)
        )
    }
}