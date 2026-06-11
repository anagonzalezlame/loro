package com.example.data.repository

import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.ContactDao
import com.example.data.local.ContactEntity
import com.example.domain.model.Contact
import com.example.domain.model.Message
import com.example.domain.repository.KidsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class KidsDataRepository(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao
) : KidsRepository {

    override fun getApprovedContacts(childId: String): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            if (entities.isEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val initial = listOf(
                        ContactEntity("1", "Mom", "https://i.pravatar.cc/150?u=mom", true),
                        ContactEntity("2", "Dad", "https://i.pravatar.cc/150?u=dad", true),
                        ContactEntity("3", "Grandma", "https://i.pravatar.cc/150?u=grandma", true)
                    )
                    contactDao.insertContacts(initial)

                    val now = System.currentTimeMillis()
                    val initialMessages = listOf(
                        MessageEntity("m1", "1", childId, "video", null, "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4", now - 1000 * 60 * 30, false),
                        MessageEntity("m2", "2", childId, "audio", null, "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", now - 1000 * 60 * 120, false),
                        MessageEntity("m3", "3", childId, "audio", null, "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", now - 1000 * 60 * 600, true)
                    )
                    messageDao.insertMessages(initialMessages)
                }
            }
            entities.map { Contact(it.id, it.name, it.avatarUrl, it.isApproved) }
        }
    }

    override fun getMessages(childId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChild(childId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getMessagesBetween(childId: String, contactId: String): Flow<List<Message>> {
        return messageDao.getMessagesBetween(childId, contactId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun markAsRead(messageId: String) {
        messageDao.markAsRead(messageId)
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
