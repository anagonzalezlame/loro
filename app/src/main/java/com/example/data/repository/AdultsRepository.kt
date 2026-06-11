package com.example.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Calendar

open class AdultsRepository(
    private val firestore: FirebaseFirestore? = null,
    private val storage: FirebaseStorage? = null
) {
    private val db: FirebaseFirestore?
        get() = try {
            firestore ?: FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("AdultsRepository", "Firebase Firestore getInstance failed: ${e.message}")
            null
        }

    private val bucket: FirebaseStorage?
        get() = try {
            storage ?: FirebaseStorage.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("AdultsRepository", "Firebase Storage getInstance failed: ${e.message}")
            null
        }

    /**
     * On-Demand Deletion (Client-Side):
     * Immediately after a media message is played and dismissed, delete it from Cloud Storage 
     * to save space on the Firebase Spark Plan, then mark the Firestore doc as deleted.
     */
    suspend fun deleteMediaMessage(messageId: String, storageUrl: String, familyId: String): Result<Unit> {
        return try {
            val currentBucket = bucket ?: return Result.failure(IllegalStateException("Firebase Storage is unavailable"))
            val currentDb = db ?: return Result.failure(IllegalStateException("Firebase Firestore is unavailable"))

            // 1. Delete the media file directly from Firebase Cloud Storage
            val storageRef = currentBucket.getReferenceFromUrl(storageUrl)
            storageRef.delete().await()

            // 2. Update the Firestore message document to reflect the "deleted_media" status
            currentDb.collection("families")
                .document(familyId)
                .collection("messages")
                .document(messageId)
                .update("mediaStatus", "deleted_media", "storageUrl", null)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Time-To-Live (TTL) Maintenance Failsafe:
     * Queries and deletes any media messages older than 48 hours to prevent Cloud Storage bloat.
     */
    suspend fun cleanupOldMediaFiles(familyId: String): Result<Int> {
        return try {
            val currentBucket = bucket ?: return Result.failure(IllegalStateException("Firebase Storage is unavailable"))
            val currentDb = db ?: return Result.failure(IllegalStateException("Firebase Firestore is unavailable"))

            // Calculate timestamp for 48 hours ago
            val fortyEightHoursAgo = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -48)
            }.timeInMillis

            // Query Firestore for old media messages that have not been deleted yet
            val oldMessagesSnapshot = currentDb.collection("families")
                .document(familyId)
                .collection("messages")
                .whereLessThan("timestamp", fortyEightHoursAgo)
                .whereNotEqualTo("mediaStatus", "deleted_media")
                .get()
                .await()

            var deletedCount = 0

            for (document in oldMessagesSnapshot.documents) {
                val storageUrl = document.getString("storageUrl")
                if (!storageUrl.isNullOrEmpty()) {
                    try {
                        val storageRef = currentBucket.getReferenceFromUrl(storageUrl)
                        storageRef.delete().await()

                        // Update Firestore doc
                        document.reference.update("mediaStatus", "deleted_media", "storageUrl", null).await()
                        deletedCount++
                    } catch (e: Exception) {
                        // Log or handle individual file deletion failure. 
                        // Continuing the loop ensures one failure doesn't stop the whole cleanup.
                    }
                }
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logBedtimeModeEvent(childId: String, isEnteringBedtime: Boolean): Result<Unit> {
        return try {
            val currentDb = db ?: return Result.failure(IllegalStateException("Firebase Firestore is unavailable"))

            val event = hashMapOf(
                "childId" to childId,
                "event" to if (isEnteringBedtime) "ENTERED_BEDTIME_MODE" else "EXITED_BEDTIME_MODE",
                "timestamp" to System.currentTimeMillis()
            )
            currentDb.collection("system_logs").add(event).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
