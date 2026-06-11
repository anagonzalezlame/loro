package com.example.ui.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Contact
import com.example.domain.repository.KidsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.worker.UploadWorker
import java.util.Calendar
import java.io.File
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
}

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val isAudio: Boolean) : RecordingState()
    data class LockedRecording(val isAudio: Boolean) : RecordingState()
}

class KidsModeViewModel(
    private val repository: KidsRepository,
    private val childId: String = "default_child_id", // Mocked for now
    private val role: String = "kid",
    private val getCurrentHour: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) },
    private val streakRepository: com.example.data.repository.StreakRepository? = null
) : ViewModel() {

    private val errorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("KidsModeViewModel", "Globally caught coroutine throwable: ${throwable.message}", throwable)
        com.example.util.LoroFirebaseLogger.logNonFatal(throwable, "KidsModeViewModel global coroutine crash")
        _uploadState.value = UploadState.Error(throwable.localizedMessage ?: "Network or database error occurred")
    }

    val messages: StateFlow<List<com.example.domain.model.Message>> = repository.getMessages(childId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch(errorHandler) {
            repository.markAsRead(messageId)
        }
    }

    private val _approvedContacts = MutableStateFlow<List<Contact>>(emptyList())
    val approvedContacts: StateFlow<List<Contact>> = _approvedContacts.asStateFlow()

    private val _streakCount = MutableStateFlow(0)
    val streakCount: StateFlow<Int> = _streakCount.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _isBedtimeMode = MutableStateFlow(false)
    private val _qaBypassBedtime = MutableStateFlow(false)
    val qaBypassBedtime: StateFlow<Boolean> = _qaBypassBedtime.asStateFlow()

    val isBedtimeMode: StateFlow<Boolean> = combine(_isBedtimeMode, _qaBypassBedtime) { bedtime, bypass ->
        if (bypass) false else bedtime
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedContact = MutableStateFlow<Contact?>(null)
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedContactMessages: StateFlow<List<com.example.domain.model.Message>> = _selectedContact
        .flatMapLatest { contact ->
            if (contact != null) {
                repository.getMessagesBetween(childId, contact.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var recordingJob: Job? = null

    // For concurrency and race-condition prevention (Debouncing)
    private var lastSelectContactTime = 0L
    private var lastStartRecordingTime = 0L
    private var lastFinishRecordingTime = 0L

    init {
        loadContacts()
        refreshMessages()
        checkBedtimeMode() // E.g., check between 21:00 (9PM) and 7:00 (7AM)
        
        // Load streak counter and register daily app opening check
        viewModelScope.launch {
            streakRepository?.checkAndIncrementStreak()
            streakRepository?.streakFlow?.collect { count ->
                _streakCount.value = count
            }
        }
    }

    fun recordActiveStreak() {
        viewModelScope.launch {
            streakRepository?.checkAndIncrementStreak()
        }
    }

    private var downtimeStartHour = 21
    private var downtimeEndHour = 7

    private fun checkBedtimeMode() {
        if (role != "kid") {
            _isBedtimeMode.value = false
            return
        }
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("settings").document("downtime")
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            downtimeStartHour = document.getLong("downtime_start")?.toInt() ?: 21
                            downtimeEndHour = document.getLong("downtime_end")?.toInt() ?: 7
                        }
                        updateBedtimeState()
                    }
                    .addOnFailureListener {
                        updateBedtimeState()
                    }
            } catch (e: Exception) {
                android.util.Log.w("KidsModeViewModel", "Firestore not initialized or accessible (using defaults): ${e.message}")
                updateBedtimeState()
            }
        }
    }

    private fun updateBedtimeState() {
        val currentHour = getCurrentHour()
        val isBedtime = if (downtimeStartHour > downtimeEndHour) {
            currentHour >= downtimeStartHour || currentHour < downtimeEndHour
        } else {
            currentHour in downtimeStartHour until downtimeEndHour
        }
        _isBedtimeMode.value = isBedtime
    }

    fun toggleQaBypassBedtime(bypass: Boolean) {
        _qaBypassBedtime.value = bypass
        val params = android.os.Bundle().apply {
            putBoolean("bypass_state", bypass)
        }
        com.example.util.LoroFirebaseLogger.logEvent("qa_bypass_used", params)
    }

    private fun refreshMessages() {
        viewModelScope.launch(errorHandler) {
            repository.refreshMessages(childId)
        }
    }

    private fun loadContacts() {
        viewModelScope.launch(errorHandler) {
            repository.getApprovedContacts(childId)
                .catch { e ->
                    android.util.Log.e("KidsModeViewModel", "Failed to retrieve approved contacts", e)
                }
                .collect { contacts ->
                    _approvedContacts.value = contacts
                }
        }
    }

    fun selectContact(contact: Contact) {
        val now = System.currentTimeMillis()
        if (now - lastSelectContactTime < 500L) return
        lastSelectContactTime = now

        _selectedContact.value = contact
        _uploadState.value = UploadState.Idle
        _recordingState.value = RecordingState.Idle
    }

    fun clearSelectedContact() {
        _selectedContact.value = null
        _uploadState.value = UploadState.Idle
        _recordingState.value = RecordingState.Idle
        cancelRecording()
    }

    fun startRecordingHold(isAudio: Boolean, context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastStartRecordingTime < 800L) return
        lastStartRecordingTime = now

        _recordingState.value = RecordingState.Recording(isAudio)
        startTimer(isAudio, context)
    }

    fun lockRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording) {
            _recordingState.value = RecordingState.LockedRecording(currentState.isAudio)
        }
    }

    fun releaseRecordingHold(context: Context) {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording) {
            finishRecording(currentState.isAudio, context)
        }
    }

    fun cancelRecording() {
        _recordingState.value = RecordingState.Idle
        recordingJob?.cancel()
    }

    fun pauseAndReleaseResources() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording || currentState is RecordingState.LockedRecording) {
            cancelRecording()
            _uploadState.value = UploadState.Error("Recording canceled to save battery/resources.")
        }
        repository.releaseHardwareResources()
    }

    fun finishRecording(isAudio: Boolean, context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastFinishRecordingTime < 1000L) return
        lastFinishRecordingTime = now

        _recordingState.value = RecordingState.Idle
        recordingJob?.cancel()
        val file = if (isAudio) File(context.cacheDir, "dummy_audio.mp3") else File(context.cacheDir, "dummy_video.mp4")
        enqueueOfflineUpload(context, isAudio, file)
    }

    private fun enqueueOfflineUpload(context: Context, isAudio: Boolean, file: File) {
        try {
            val contactId = _selectedContact.value?.id ?: return
            
            // Cache the file locally (simulated by using context.cacheDir above)
            if (!file.exists()) {
                file.createNewFile() // ensure it exists for demo
            }

            val msgId = "local_msg_" + java.util.UUID.randomUUID().toString()

            // Save immediately in local database for Offline-First visual representation
            viewModelScope.launch(errorHandler) {
                val db = com.example.data.local.AppDatabase.getDatabase(context)
                db.messageDao().insertMessage(
                    com.example.data.local.MessageEntity(
                        id = msgId,
                        senderId = childId,
                        recipientId = contactId,
                        mediaType = if (isAudio) "audio" else "video",
                        localUri = file.absolutePath,
                        remoteUrl = null,
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            }

            val data = Data.Builder()
                .putString(UploadWorker.KEY_CHILD_ID, childId)
                .putString(UploadWorker.KEY_CONTACT_ID, contactId)
                .putString(UploadWorker.KEY_FILE_PATH, file.absolutePath)
                .putBoolean(UploadWorker.KEY_IS_AUDIO, isAudio)
                .putString("msg_id", msgId)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(uploadWork)
            _uploadState.value = UploadState.Success // Optimistic UI update

            // Track Firebase Analytics events
            val sentParams = android.os.Bundle().apply {
                putString("media_type", if (isAudio) "audio" else "video")
                putString("recipient_id", contactId)
            }
            com.example.util.LoroFirebaseLogger.logEvent("message_sent", sentParams)

            val queueParams = android.os.Bundle().apply {
                putString("msg_id", msgId)
                putBoolean("is_audio", isAudio)
            }
            com.example.util.LoroFirebaseLogger.logEvent("offline_queue_activated", queueParams)

            // Dynamic locally active daily checked streak
            recordActiveStreak()
        } catch (e: Exception) {
            android.util.Log.e("KidsModeViewModel", "Failed to enqueue upload worker in WorkManager", e)
            com.example.util.LoroFirebaseLogger.logNonFatal(e, "WorkManager offline queue activation failure")
            _uploadState.value = UploadState.Error("Offline uploading setup failed.")
        }
    }

    private fun startTimer(isAudio: Boolean, context: Context) {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(errorHandler) {
            delay(30_000)
            if (_recordingState.value !is RecordingState.Idle) {
                finishRecording(isAudio, context)
            }
        }
    }

    fun sendVideoMessage(videoFile: File) {
        val contactId = _selectedContact.value?.id ?: return
        viewModelScope.launch(errorHandler) {
            _uploadState.value = UploadState.Uploading
            val result = repository.uploadVideoMessage(contactId, videoFile)
            if (result.isSuccess) {
                _uploadState.value = UploadState.Success
            } else {
                _uploadState.value = UploadState.Error("Failed to upload video.")
            }
        }
    }

    fun sendAudioMessage(audioFile: File) {
        val contactId = _selectedContact.value?.id ?: return
        viewModelScope.launch(errorHandler) {
            _uploadState.value = UploadState.Uploading
            val result = repository.uploadAudioMessage(contactId, audioFile)
            if (result.isSuccess) {
                _uploadState.value = UploadState.Success
            } else {
                _uploadState.value = UploadState.Error("Failed to upload audio.")
            }
        }
    }
}
