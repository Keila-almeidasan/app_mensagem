package com.example.app_mensagem.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String = "",
    val name: String = "",
    val email: String = "",
    val fcmToken: String = "",
    val profilePictureUrl: String? = null,
    val status: String = "Disponível", // Ex: "Online", "Ocupado", "No trabalho"
    val about: String = "", // Mais informações no perfil
    val blockedUsers: List<String> = emptyList(),
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val lastSeenVisible: Boolean = true // Bloqueio de visto por último
)