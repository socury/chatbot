package com.authtemplate.global.jwt

import com.authtemplate.domain.auth.dto.response.AuthTokenResponse
import com.authtemplate.domain.user.entity.UserEntity
import com.authtemplate.global.jwt.execption.JwtErrorType
import com.authtemplate.global.redis.RedisService
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import java.security.SignatureException
import javax.crypto.SecretKey

@Component
class JwtUtil (
    @Value("\${jwt.secret}")
    private val secret: String,

    @Value("\${jwt.access-token-expire-time}")
    private val accessTokenExpireTime: Long,

    @Value("\${jwt.refresh-token-expire-time}")
    private val refreshTokenExpireTime: Long,

    private val userDetailsService: UserDetailsService,

    private val redisService: RedisService
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    fun validateToken(token: String): JwtErrorType {
        try {
            val id = getUserId(token)
            val cacheRefreshToken = redisService.getRefreshToken(id)

            if (cacheRefreshToken != null && cacheRefreshToken == token && getType(token) == "refresh")
                return JwtErrorType.IllegalArgumentException

            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
            return JwtErrorType.OK
        } catch (e: ExpiredJwtException) {
            return JwtErrorType.ExpiredJwtException
        } catch (e: SignatureException) {
            return JwtErrorType.SignatureException
        } catch (e: MalformedJwtException) {
            return JwtErrorType.MalformedJwtException
        } catch (e: UnsupportedJwtException) {
            return JwtErrorType.UnsupportedJwtException
        } catch (e: IllegalArgumentException) {
            return JwtErrorType.IllegalArgumentException
        } catch (e: Exception) {
            return JwtErrorType.UNKNOWN_EXCEPTION
        }
    }

    fun getAuthentication(token: String): Authentication {
        val userDetails: UserDetails = userDetailsService.loadUserByUsername(getUsername(token))
        return UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
    }

    fun generateToken(user: UserEntity): AuthTokenResponse {
        val refreshToken = createRefreshToken(user)
        redisService.storeRefreshToken(user.id, refreshToken)
        return AuthTokenResponse(
            accessToken = createToken(user),
            refreshToken = refreshToken,
            tokenType = "Bearer",
        )
    }

    fun createToken(user: UserEntity): String {
        val now = Date().time
        return Jwts.builder()
            .claim("id", user.id)
            .claim("name", user.username)
            .claim("token_type", "access")
            .issuedAt(Date(now))
            .expiration(Date(now + accessTokenExpireTime))
            .signWith(secretKey)
            .compact()
    }

    fun createRefreshToken(user: UserEntity): String {
        val now = Date().time
        return Jwts.builder()
            .claim("id", user.id)
            .claim("name", user.username)
            .claim("token_type", "refresh")
            .issuedAt(Date(now))
            .expiration(Date(now + refreshTokenExpireTime))
            .signWith(secretKey)
            .compact()
    }

    fun getUserId(token: String): Long {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload.get(
            "id",
            Integer::class.java
        ).toLong()
    }

    fun getUsername(token: String): String {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload.get(
            "name",
            String::class.java
        )
    }

    fun getType(token: String): String {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload.get(
            "token_type",
            String::class.java
        )
    }
}