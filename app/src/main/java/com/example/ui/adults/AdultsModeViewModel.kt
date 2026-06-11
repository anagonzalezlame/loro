package com.example.ui.adults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ContactDao
import com.example.data.local.ContactEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.repository.AdultsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AdultsModeViewModel(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val repository: AdultsRepository = AdultsRepository()
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    private val errorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("AdultsModeViewModel", "Globally caught coroutine throwable: ${throwable.message}", throwable)
        _errorMessage.value = throwable.localizedMessage ?: "A remote or database operation error occurred"
    }

    val contacts: StateFlow<List<ContactEntity>> = contactDao.getAllContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val messageLogs: StateFlow<List<MessageEntity>> = messageDao.getMessagesForChild("default_child_id")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addContact(name: String, avatarUrl: String? = null) {
        viewModelScope.launch(errorHandler) {
            val id = UUID.randomUUID().toString()
            val url = avatarUrl?.takeIf { it.isNotEmpty() } ?: "https://i.pravatar.cc/150?u=$name"
            contactDao.insertContact(ContactEntity(id, name, url, true))
        }
    }

    fun saveContact(contact: ContactEntity) {
        viewModelScope.launch(errorHandler) {
            contactDao.insertContact(contact)
        }
    }

    fun getMessagesBetween(childId: String, contactId: String): kotlinx.coroutines.flow.Flow<List<MessageEntity>> {
        return messageDao.getMessagesBetween(childId, contactId)
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch(errorHandler) {
            contactDao.deleteContact(contactId)
        }
    }

    fun toggleContactApproval(contactId: String, approved: Boolean) {
        viewModelScope.launch(errorHandler) {
            contactDao.updateApproval(contactId, approved)
        }
    }

    fun deleteMessageLog(messageId: String) {
        viewModelScope.launch(errorHandler) {
            messageDao.deleteMessage(messageId)
        }
    }

    /**
     * Called when the parent dismisses a media message in the UI after watching/listening to it.
     */
    fun onMediaMessageDismissed(messageId: String, storageUrl: String?, familyId: String) {
        if (storageUrl.isNullOrEmpty()) return
        
        viewModelScope.launch(errorHandler) {
            val result = repository.deleteMediaMessage(messageId, storageUrl, familyId)
            if (result.isSuccess) {
                // UI state could be updated here to reflect the deletion
            } else {
                _errorMessage.value = "Failed to delete remote file: " + (result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    /**
     * Triggered periodically or upon app launch to perform a fail-safe cleanup
     * of forgotten/old messages over the 48-hour threshold.
     */
    fun performStorageCleanup(familyId: String) {
        viewModelScope.launch(errorHandler) {
            val result = repository.cleanupOldMediaFiles(familyId)
            if (result.isSuccess) {
                // Clean-up succeeded
            } else {
                _errorMessage.value = "Cleanup warning: " + (result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    /**
     * Logs every instance a child's device enters or exits Bedtime Mode.
     * These events are stored in the 'system_logs' collection in Firestore.
     */
    fun logBedtimeModeEvent(childId: String, isEnteringBedtime: Boolean) {
        viewModelScope.launch(errorHandler) {
            val result = repository.logBedtimeModeEvent(childId, isEnteringBedtime)
            if (result.isFailure) {
                android.util.Log.w("AdultsModeViewModel", "Failed to log Bedtime Mode transition to remote database")
            }
        }
    }

    // --- SECURE PIN TRANSITION AND CRUD FLOW STATE ---
    private val _isAdultModeUnlocked = MutableStateFlow(false)
    val isAdultModeUnlocked: StateFlow<Boolean> = _isAdultModeUnlocked.asStateFlow()

    private val _lastVerificationTime = MutableStateFlow(0L)
    val lastVerificationTime: StateFlow<Long> = _lastVerificationTime.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _lockoutEndTime = MutableStateFlow(0L)
    val lockoutEndTime: StateFlow<Long> = _lockoutEndTime.asStateFlow()

    fun incrementFailedAttempts() {
        val nextAttempts = _failedAttempts.value + 1
        _failedAttempts.value = nextAttempts
        if (nextAttempts >= 3) {
            _lockoutEndTime.value = System.currentTimeMillis() + 60_000L
        }
    }

    fun resetFailedAttempts() {
        _failedAttempts.value = 0
        _lockoutEndTime.value = 0L
    }

    fun isLockedOut(): Boolean {
        val now = System.currentTimeMillis()
        val locked = now < _lockoutEndTime.value
        if (!locked && _failedAttempts.value >= 3) {
            _failedAttempts.value = 0
            _lockoutEndTime.value = 0L
        }
        return locked
    }

    fun getRemainingLockoutTimeSeconds(): Long {
        val remainingMs = _lockoutEndTime.value - System.currentTimeMillis()
        return if (remainingMs > 0) remainingMs / 1000L else 0L
    }

    /**
     * Verifies the entered PIN code against the correct stored PIN code
     */
    fun verifyPin(enteredPin: String, actualPin: String): Boolean {
        return enteredPin == actualPin
    }

    fun verifyPinWithRateLimit(enteredPin: String, actualPin: String): Boolean {
        if (isLockedOut()) return false
        val isValid = verifyPin(enteredPin, actualPin)
        if (isValid) {
            resetFailedAttempts()
        } else {
            incrementFailedAttempts()
        }
        return isValid
    }

    /**
     * Verifies PIN and unlocks the Adult Management Dashboard securely
     */
    fun verifyAndUnlockTransition(enteredPin: String, actualPin: String): Boolean {
        if (isLockedOut()) return false
        val isValid = verifyPinWithRateLimit(enteredPin, actualPin)
        _isAdultModeUnlocked.value = isValid
        if (isValid) {
            _lastVerificationTime.value = System.currentTimeMillis()
        }
        return isValid
    }

    /**
     * Re-locks Adult mode and secure operations
     */
    fun lockAdultMode() {
        _isAdultModeUnlocked.value = false
        _lastVerificationTime.value = 0L
    }

    /**
     * Safe execution helper wrapper for any CRUD operations inside the ViewModel.
     * Returns true if authorization succeeded and operation executed, false otherwise.
     */
    fun executeSecuredAction(enteredPin: String, actualPin: String, action: () -> Unit): Boolean {
        return if (verifyPin(enteredPin, actualPin)) {
            action()
            true
        } else {
            false
        }
    }

    // --- SECURED CRUD PROXIES FOR INTEGRITY ---
    fun secureAddContact(name: String, avatarUrl: String? = null, enteredPin: String, actualPin: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}): Boolean {
        return executeSecuredAction(enteredPin, actualPin) {
            addContact(name, avatarUrl)
            onSuccess()
        }
    }

    fun secureSaveContact(contact: ContactEntity, enteredPin: String, actualPin: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}): Boolean {
        return executeSecuredAction(enteredPin, actualPin) {
            saveContact(contact)
            onSuccess()
        }
    }

    fun secureDeleteContact(contactId: String, enteredPin: String, actualPin: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}): Boolean {
        return executeSecuredAction(enteredPin, actualPin) {
            deleteContact(contactId)
            onSuccess()
        }
    }

    fun secureToggleContactApproval(contactId: String, approved: Boolean, enteredPin: String, actualPin: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}): Boolean {
        return executeSecuredAction(enteredPin, actualPin) {
            toggleContactApproval(contactId, approved)
            onSuccess()
        }
    }

    fun secureDeleteMessageLog(messageId: String, enteredPin: String, actualPin: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}): Boolean {
        return executeSecuredAction(enteredPin, actualPin) {
            deleteMessageLog(messageId)
            onSuccess()
        }
    }
}
