package com.example.domain.model

data class Contact(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isApproved: Boolean
)
