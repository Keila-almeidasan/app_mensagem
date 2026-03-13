package com.example.app_mensagem.data.model

data class Group(
    val id: String = "",
    val name: String = "",
    val creatorId: String = "",
    val members: Map<String, Boolean> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val profilePictureUrl: String? = null
)