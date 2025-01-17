package com.chatbot.domain.user.repository

import com.chatbot.domain.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun existsByUsername(username: String): Boolean
}