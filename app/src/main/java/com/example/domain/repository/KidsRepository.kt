package com.example.domain.repository

import com.example.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import java.io.File

interface KidsRepository {
    fun getApprovedContacts(childId: String): Flow<List<Contact>>
    fun getMessages(childId: String): Flow<List<com.example.domain.model.Message>>
    suspend fun refreshMessages(childId: String)
    suspend fun uploadVideoMessage(contactId: String, videoFile: File): Result<Unit>
    suspend fun uploadAudioMessage(contactId: String, audioFile: File): Result<Unit>
    fun releaseHardwareResources()
}
