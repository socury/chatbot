package com.chatbot.domain.chat.service

import com.chatbot.domain.chat.entity.ChatHistory
import com.chatbot.domain.chat.entity.ChatMessage
import com.chatbot.domain.chat.exception.ChatErrorCode
import com.chatbot.domain.chat.repository.ChatRepository
import com.chatbot.global.exception.CustomException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.hibernate.query.sqm.tree.SqmNode.log
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.lang.Thread.sleep

@Service
class ChatService (
    private val objectMapper: ObjectMapper,

    @Value("\${spring.ai.openai.api-key}")
    private val apiKey: String,

    @Value("\${spring.ai.openai.assistantId}")
    private val assistantId: String,

    private val chatRepository: ChatRepository,
) {
    private val restTemplate = RestTemplate()
    private val apiUrl = "https://api.openai.com/v1/threads"

    val headers = HttpHeaders().apply {
        set("Authorization", "Bearer $apiKey")
        set("Content-Type", "application/json")
        set("OpenAI-Beta", "assistants=v2")
    }

    fun sendMessage(userId: String, message: String): ChatMessage {
        if (!chatRepository.existsByUserId(userId)) {
            val chatHistory = ChatHistory(
                userId = userId,
                threadId = createThread(),
            )
            chatRepository.save(chatHistory)
        }
        val chatHistory = chatRepository.findByUserId(userId)
        val threadId = chatHistory!!.threadId

        val userMessage = ChatMessage(content = message, role = "user")
        chatHistory.messages.add(userMessage)

        createMessage(threadId, message)
        runAssistant(threadId)
        sleep(3000)
        return getMessage(chatHistory, threadId)
    }

    // thread 발급
    fun createThread(): String {
        val entity = HttpEntity<String>(headers)
        val response: ResponseEntity<String> = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String::class.java)
        val rootNode: JsonNode = objectMapper.readTree(response.body)
        return rootNode["id"].asText()
    }

    // 메시지 생성
    fun createMessage(threadId: String, message: String) {
        val requestBody = mapOf(
            "role" to "user",
            "content" to message
        )
        val entity = HttpEntity(requestBody,headers)
        restTemplate.exchange("$apiUrl/$threadId/messages", HttpMethod.POST, entity, String::class.java)
    }

    // assistant 실행하기
    fun runAssistant(threadId: String) {
        val requestBody = mapOf(
            "assistant_id" to assistantId,
        )
        val entity = HttpEntity(requestBody,headers)
        restTemplate.exchange("$apiUrl/$threadId/runs", HttpMethod.POST, entity, String::class.java)
    }

    // 메시지 가져오기
    fun getMessage(chatHistory: ChatHistory, threadId: String): ChatMessage{
        while (true) {
            try {
                val entity = HttpEntity<String>(headers)
                val response =
                    restTemplate.exchange("$apiUrl/$threadId/messages", HttpMethod.GET, entity, String::class.java)
                val responseBody = objectMapper.readValue<Map<String, Any>>(response.body!!)

                val data = responseBody["data"] as? List<Map<String, Any>>
                val firstMessage = data?.firstOrNull()

                val role = firstMessage?.get("role") as? String ?: ""
                val contentList = firstMessage?.get("content") as? List<Map<String, Any>>

                val value = contentList?.firstOrNull()?.let { contentItem ->
                    val textMap = contentItem["text"] as? Map<String, Any>
                    textMap?.get("value") as? String
                }

                if (role == "user" && value != null) {
                    sleep(1000)
                } else {
                    val assistantMessage = ChatMessage(
                        content = value.toString(),
                        role = "assistant"
                    )
                    chatHistory.messages.add(assistantMessage)
                    chatRepository.save(chatHistory)
                    return assistantMessage
                }
            } catch (e: Exception) {
                throw CustomException(ChatErrorCode.GET_CHAT_ERROR)
            }
        }
    }


    fun clearHistory(userId: String) {
        val history = chatRepository.findByUserId(userId)
        history?.messages?.clear()
        history?.let { chatRepository.save(it) }
    }

    fun getHistory(userId: String): List<ChatMessage> {
        return chatRepository.findByUserId(userId)?.messages ?: emptyList()
    }
}