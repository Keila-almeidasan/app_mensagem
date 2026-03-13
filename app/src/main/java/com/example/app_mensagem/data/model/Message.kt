package com.example.app_mensagem.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.app_mensagem.data.local.Converters

@Entity(tableName = "messages")
@TypeConverters(Converters::class)
data class Message(
    @PrimaryKey val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val content: String = "",
    val type: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "STICKER"
    val thumbnailUrl: String? = null,
    val timestamp: Long = 0L,
    var status: String = "SENT",
    val deliveredTimestamp: Long = 0L,
    val readTimestamp: Long = 0L,
    val reactions: Map<String, String> = emptyMap()
)