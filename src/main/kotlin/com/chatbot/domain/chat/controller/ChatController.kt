package com.chatbot.domain.chat.controller

import com.chatbot.domain.chat.dto.request.MessageRequest
import com.chatbot.domain.chat.entity.ChatMessage
import com.chatbot.domain.chat.service.ChatService
import com.chatbot.domain.user.entity.UserEntity
import com.chatbot.domain.user.exception.UserErrorCode
import com.chatbot.domain.user.repository.UserRepository
import com.chatbot.global.exception.CustomException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.security.Principal

@Tag(name = "ChatBot", description = "챗봇")
@RestController
@RequestMapping("/chatbot")
class ChatController (
    private val chatService: ChatService,
    private val userRepository: UserRepository
) {
    @Operation(summary = "채팅 보내기")
    @PostMapping("/send")
    fun sendMessage(principal: Principal, @RequestBody request: MessageRequest): ChatMessage {
        val user: UserEntity = userRepository.findByUsername(principal.name).orElseThrow{ CustomException(UserErrorCode.USER_NOT_FOUND) }
        return chatService.sendMessage(user.id.toString(), request.message)
    }

    @Operation(summary = "채팅 초기화")
    @DeleteMapping("/clear")
    fun clearHistory(principal: Principal) {
        val user: UserEntity = userRepository.findByUsername(principal.name).orElseThrow{CustomException(UserErrorCode.USER_NOT_FOUND)}
        chatService.clearHistory(user.id.toString())
    }

    @Operation(summary = "채팅기록 조회하기")
    @GetMapping("/history")
    fun getHistory(principal: Principal): List<ChatMessage> {
        val user: UserEntity = userRepository.findByUsername(principal.name).orElseThrow{CustomException(UserErrorCode.USER_NOT_FOUND)}
        return chatService.getHistory(user.id.toString())
    }
}