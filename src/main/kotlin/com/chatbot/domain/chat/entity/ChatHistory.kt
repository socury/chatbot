package com.chatbot.domain.chat.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document(collection = "chat_history")
data class ChatHistory(
    @Id
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val threadId: String,
    val messages: MutableList<ChatMessage> = mutableListOf()
)
