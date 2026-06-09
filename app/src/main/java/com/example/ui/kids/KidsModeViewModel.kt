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
    private val role: String = "kid"
) : ViewModel() {

    val messages: StateFlow<List<com.example.domain.model.Message>> = repository.getMessages(childId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _approvedContacts = MutableStateFlow<List<Contact>>(emptyList())
    val approvedContacts: StateFlow<List<Contact>> = _approvedContacts.asStateFlow()

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

    private var recordingJob: Job? = null

    init {
        loadContacts()
        refreshMessages()
        checkBedtimeMode() // E.g., check between 21:00 (9PM) and 7:00 (7AM)
    }

    private fun checkBedtimeMode() {
        if (role != "kid") {
            _isBedtimeMode.value = false
            return
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // Hardcoded for demonstration, ideally fetched from Firestore down_time config
        val isBedtime = currentHour >= 21 || currentHour < 7
        _isBedtimeMode.value = isBedtime
    }

    fun toggleQaBypassBedtime(bypass: Boolean) {
        _qaBypassBedtime.value = bypass
    }

    private fun refreshMessages() {
        viewModelScope.launch {
            repository.refreshMessages(childId)
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            repository.getApprovedContacts(childId)
                .catch { e ->
                    // Handle error if needed (e.g., show empty state)
                }
                .collect { contacts ->
                    _approvedContacts.value = contacts
                }
        }
    }

    fun selectContact(contact: Contact) {
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
        _recordingState.value = RecordingState.Idle
        recordingJob?.cancel()
        val file = if (isAudio) File(context.cacheDir, "dummy_audio.mp3") else File(context.cacheDir, "dummy_video.mp4")
        enqueueOfflineUpload(context, isAudio, file)
    }

    private fun enqueueOfflineUpload(context: Context, isAudio: Boolean, file: File) {
        val contactId = _selectedContact.value?.id ?: return
        
        // Cache the file locally (simulated by using context.cacheDir above)
        if (!file.exists()) {
            file.createNewFile() // ensure it exists for demo
        }

        val data = Data.Builder()
            .putString(UploadWorker.KEY_CHILD_ID, childId)
            .putString(UploadWorker.KEY_CONTACT_ID, contactId)
            .putString(UploadWorker.KEY_FILE_PATH, file.absolutePath)
            .putBoolean(UploadWorker.KEY_IS_AUDIO, isAudio)
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
    }

    private fun startTimer(isAudio: Boolean, context: Context) {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            delay(30_000)
            if (_recordingState.value !is RecordingState.Idle) {
                finishRecording(isAudio, context)
            }
        }
    }

    fun sendVideoMessage(videoFile: File) {
        val contactId = _selectedContact.value?.id ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
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
