package com.chatbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class chatbotApplication

fun main(args: Array<String>) {
    runApplication<chatbotApplication>(*args)
}
