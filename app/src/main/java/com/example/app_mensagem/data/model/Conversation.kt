package com.example.app_mensagem.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.PropertyName

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val profilePictureUrl: String? = null,
    val lastMessage: String = "",
    val timestamp: Long = 0L,
    val pinnedMessageId: String? = null,

    // A anotação que garante a leitura/escrita correta do Firebase
    @get:PropertyName("isGroup")
    val isGroup: Boolean = false
)