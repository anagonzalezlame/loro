package com.example.data.repository

import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.domain.model.Contact
import com.example.domain.model.Message
import com.example.domain.repository.KidsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class KidsDataRepository(
    private val messageDao: MessageDao
) : KidsRepository {

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

    override fun getMessages(childId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChild(childId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun refreshMessages(childId: String) {
        try {
            // Note: In a real implementation this would fetch by childId and map to MessageEntity.
            // For now, we simulate fetching from Firestore and storing to Room.
            // val snapshot = firestore.collection("messages").whereEqualTo("recipientId", childId).get().await()
            // val entities = snapshot.documents.mapNotNull { ... }
            // messageDao.insertMessages(entities)
        } catch (e: Exception) {
            // Handle error
        }
    }

    override suspend fun uploadVideoMessage(contactId: String, videoFile: File): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun uploadAudioMessage(contactId: String, audioFile: File): Result<Unit> {
        return Result.success(Unit)
    }

    private var mediaRecorder: android.media.MediaRecorder? = null
    // We would also keep a reference to activeRecording here if using CameraX explicitly
    private var activeRecording: androidx.camera.video.Recording? = null

    override fun releaseHardwareResources() {
        try {
            activeRecording?.stop()
            activeRecording?.close()
            activeRecording = null
        } catch (e: Exception) {
            // Ignore
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun MessageEntity.toDomainModel() = Message(
        id = id,
        senderId = senderId,
        recipientId = recipientId,
        mediaType = mediaType,
        localUri = localUri,
        remoteUrl = remoteUrl,
        timestamp = timestamp,
        isRead = isRead
    )
}
