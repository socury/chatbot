package com.chatbot.global.jwt

import com.chatbot.global.dto.BaseResponse
import com.chatbot.global.jwt.execption.JwtErrorCode
import com.chatbot.global.jwt.execption.JwtErrorType
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtFilter (
    private val jwtUtil: JwtUtil,
    private val objectMapper: ObjectMapper
): OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path: String = request.servletPath

        if (path.startsWith("/auth") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response)
            return
        }

        val token: String? = request.getHeader("Authorization")

        if (token.isNullOrEmpty() || !token.startsWith("Bearer ")) {
            setErrorResponse(response, JwtErrorCode.JWT_EMPTY_EXCEPTION)
        } else {
            when (jwtUtil.validateToken(token.removePrefix("Bearer "))) {
                JwtErrorType.OK -> {
                    val name = jwtUtil.getAuthentication(token.removePrefix("Bearer "));
                    SecurityContextHolder.getContext().authentication = name
                    doFilter(request, response, filterChain)
                }
                JwtErrorType.ExpiredJwtException -> setErrorResponse(response, JwtErrorCode.JWT_TOKEN_EXPIRED)
                JwtErrorType.SignatureException -> setErrorResponse(response, JwtErrorCode.JWT_TOKEN_SIGNATURE_ERROR)
                JwtErrorType.MalformedJwtException -> setErrorResponse(response, JwtErrorCode.JWT_TOKEN_ERROR)
                JwtErrorType.UnsupportedJwtException -> setErrorResponse(
                    response,
                    JwtErrorCode.JWT_TOKEN_UNSUPPORTED_ERROR
                )

                JwtErrorType.IllegalArgumentException -> setErrorResponse(
                    response,
                    JwtErrorCode.JWT_TOKEN_ILL_EXCEPTION
                )

                JwtErrorType.UNKNOWN_EXCEPTION -> setErrorResponse(response, JwtErrorCode.JWT_UNKNOWN_EXCEPTION)
            }
        }
    }

    private fun setErrorResponse(
        response: HttpServletResponse,
        errorCode: JwtErrorCode
    ) {
        response.status = errorCode.status.value()
        response.contentType = "application/json;charset=UTF-8"

        response.writer.write(
            objectMapper.writeValueAsString(
                BaseResponse<String>(
                    status = errorCode.status.value(),
                    state = errorCode.state,
                    message = errorCode.message
                )
            )
        )
    }
}