package com.chatbot.domain.chat.entity

import java.time.LocalDateTime

data class ChatMessage (
    val content: String,
    val role: String, // user or assistant
    val timestamp: LocalDateTime = LocalDateTime.now()
)