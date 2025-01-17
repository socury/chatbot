package com.chatbot.global.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus

@JsonInclude(JsonInclude.Include.NON_NULL)
class BaseResponse<T> (
    val status: Int = HttpStatus.OK.value(),
    val state: String? = "OK",
    val message: String,
    val data: T? = null
)