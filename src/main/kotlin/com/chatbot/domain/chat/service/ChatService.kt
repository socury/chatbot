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
            // mongoDB 조회 후 이전 대화 내용이 없다면 thread 생성
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
        val runId = runAssistant(threadId)
        sleep(1000)
        return getStatus(threadId, runId, chatHistory)
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
    fun runAssistant(threadId: String): String {
        val requestBody = mapOf(
            "assistant_id" to assistantId,
        )
        val entity = HttpEntity(requestBody,headers)
        val response = restTemplate.exchange("$apiUrl/$threadId/runs", HttpMethod.POST, entity, String::class.java)
        val objectMapper = ObjectMapper()
        val jsonNode: JsonNode = objectMapper.readTree(response.body)
        val id = jsonNode["id"]?.asText() ?: ""
        return id
    }

    fun getStatus(threadId: String, runId: String, chatHistory: ChatHistory): ChatMessage {
        while (true) {
            try {
                val entity = HttpEntity<String>(headers)
                val response = restTemplate.exchange("$apiUrl/$threadId/runs/$runId", HttpMethod.GET, entity, String::class.java)
                val objectMapper = ObjectMapper()
                val jsonNode: JsonNode = objectMapper.readTree(response.body)
                val status = jsonNode["status"]?.asText() ?: ""

                if (status != "completed") {
                    log.warn("i")
                    sleep(500)
                } else {
                    return getMessage(chatHistory, threadId)
                }
            } catch (e: Exception) {
                log.error(e.message, e)
                throw CustomException(ChatErrorCode.GET_CHAT_ERROR)
            }
        }
    }

    // 메시지 가져오기
    fun getMessage(chatHistory: ChatHistory, threadId: String): ChatMessage{

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

        val assistantMessage = ChatMessage(
            content = value.toString(),
            role = "assistant"
        )
        chatHistory.messages.add(assistantMessage)
        chatRepository.save(chatHistory)
        return assistantMessage
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