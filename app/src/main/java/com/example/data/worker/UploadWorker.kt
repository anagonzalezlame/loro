package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.MockKidsRepository
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val childId = inputData.getString(KEY_CHILD_ID) ?: return Result.failure()
        val contactId = inputData.getString(KEY_CONTACT_ID) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)

        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        // Note: Using MockKidsRepository for demonstration.
        // In a production app, use Hilt/Dagger or pass repository through custom WorkerFactory.
        val repository = MockKidsRepository()

        return try {
            val result = if (isAudio) {
                repository.uploadAudioMessage(contactId, file)
            } else {
                repository.uploadVideoMessage(contactId, file)
            }
            
            if (result.isSuccess) {
                file.delete() // Clean up local cache
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_CHILD_ID = "child_id"
        const val KEY_CONTACT_ID = "contact_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_IS_AUDIO = "is_audio"
    }
}
