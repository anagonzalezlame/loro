package com.example.ui.adults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AdultsRepository
import kotlinx.coroutines.launch

class AdultsModeViewModel(
    private val repository: AdultsRepository = AdultsRepository()
) : ViewModel() {

    /**
     * Called when the parent dismisses a media message in the UI after watching/listening to it.
     */
    fun onMediaMessageDismissed(messageId: String, storageUrl: String?, familyId: String) {
        if (storageUrl.isNullOrEmpty()) return
        
        viewModelScope.launch {
            val result = repository.deleteMediaMessage(messageId, storageUrl, familyId)
            if (result.isSuccess) {
                // UI state could be updated here to reflect the deletion
            } else {
                // Handle deletion failure, perhaps enqueueing a retry mechanism later
                // The 48-hour TTL fallback will catch anything that fails here.
            }
        }
    }

    /**
     * Triggered periodically or upon app launch to perform a fail-safe cleanup
     * of forgotten/old messages over the 48-hour threshold.
     */
    fun performStorageCleanup(familyId: String) {
        viewModelScope.launch {
            val result = repository.cleanupOldMediaFiles(familyId)
            if (result.isSuccess) {
                // Clean-up succeeded (result.getOrNull() gives the deletedCount)
            }
        }
    }
}
