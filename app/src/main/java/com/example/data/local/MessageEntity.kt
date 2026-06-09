package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val recipientId: String,
    val mediaType: String, // "audio" or "video"
    val localUri: String?, // For playing from local cache
    val remoteUrl: String?,
    val timestamp: Long,
    val isRead: Boolean
)
