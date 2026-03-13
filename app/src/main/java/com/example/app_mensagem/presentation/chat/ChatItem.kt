package com.example.app_mensagem.presentation.chat

import com.example.app_mensagem.data.model.Message

sealed interface ChatItem {
    data class MessageItem(val message: Message) : ChatItem
    data class DateHeader(val date: String) : ChatItem
}