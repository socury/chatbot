package com.chatbot.domain.chat.repository

import com.chatbot.domain.chat.entity.ChatHistory
import org.springframework.data.mongodb.repository.MongoRepository

interface ChatRepository: MongoRepository<ChatHistory, String> {
    fun existsByUserId(userId: String): Boolean
    fun findByUserId(userId: String): ChatHistory?
}