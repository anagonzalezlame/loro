package com.example.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val mediaType: String,
    val localUri: String?,
    val remoteUrl: String?,
    val timestamp: Long,
    val isRead: Boolean
)
