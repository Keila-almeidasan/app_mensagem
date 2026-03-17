package com.example.app_mensagem.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val content: String = "",
    val fileName: String? = null, // Nome original do arquivo
    val type: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "STICKER", "DOCUMENT"
    val thumbnailUrl: String? = null,
    val timestamp: Long = 0L,
    var status: String = "SENT",
    val deliveredTimestamp: Long = 0L,
    val readTimestamp: Long = 0L,
    val reactions: Map<String, String> = emptyMap()
)