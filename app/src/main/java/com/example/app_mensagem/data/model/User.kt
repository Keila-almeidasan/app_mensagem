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
    val status: String = ""
)