package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isApproved: Boolean
)
