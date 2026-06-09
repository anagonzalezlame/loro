package com.example.data.repository

import com.example.domain.model.Contact
import com.example.domain.repository.KidsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class MockKidsRepository : KidsRepository {
    override fun getApprovedContacts(childId: String): Flow<List<Contact>> = flow {
        // Mock data to demonstrate UI
        emit(
            listOf(
                Contact("1", "Mom", "https://i.pravatar.cc/150?u=mom", true),
                Contact("2", "Dad", "https://i.pravatar.cc/150?u=dad", true),
                Contact("3", "Grandma", "https://i.pravatar.cc/150?u=grandma", true)
            )
        )
    }

    override fun getMessages(childId: String): Flow<List<com.example.domain.model.Message>> = flow {
        emit(emptyList())
    }

    override suspend fun refreshMessages(childId: String) {}

    override suspend fun uploadVideoMessage(contactId: String, videoFile: File): Result<Unit> {
        delay(2000) // Simulate network delay
        return Result.success(Unit)
    }

    override suspend fun uploadAudioMessage(contactId: String, audioFile: File): Result<Unit> {
        delay(1500)
        return Result.success(Unit)
    }

    override fun releaseHardwareResources() {
        // Mock release
    }
}
