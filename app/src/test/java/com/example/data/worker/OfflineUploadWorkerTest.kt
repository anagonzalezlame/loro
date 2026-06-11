package com.example.data.worker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineUploadWorkerTest {

    private lateinit var context: Context
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Create a real temporary file on local disk to satisfy file.exists check
        tempFile = File.createTempFile("test_audio", ".mp3")
        tempFile.writeText("sample voice message data")
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun givenValidLocalFile_whenUploadSucceeds_thenWorkerReturnsSuccessAndDeletesLocalCache() = runTest {
        // Prepare input data
        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_CHILD_ID, "kid_abc")
            .putString(UploadWorker.KEY_CONTACT_ID, "guardian_xyz")
            .putString(UploadWorker.KEY_FILE_PATH, tempFile.absolutePath)
            .putBoolean(UploadWorker.KEY_IS_AUDIO, true)
            .build()

        val worker = TestListenableWorkerBuilder<UploadWorker>(context)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()

        // Assert success result
        assertEquals(Result.success(), result)
        // Assert that the temporary file has been deleted to save disk space
        assertFalse(tempFile.exists())
    }

    @Test
    fun givenNetworkFailureDuringUpload_whenWorkerExecutes_thenWorkerReturnsRetry() = runTest {
        // Create failing context wrapper to raise exception in database / outer block execution
        val failingContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context {
                throw RuntimeException("Simulated database/network error during worker upload")
            }
        }

        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_CHILD_ID, "kid_abc")
            .putString(UploadWorker.KEY_CONTACT_ID, "guardian_xyz")
            .putString(UploadWorker.KEY_FILE_PATH, tempFile.absolutePath)
            .putBoolean(UploadWorker.KEY_IS_AUDIO, true)
            .build()

        val worker = TestListenableWorkerBuilder<UploadWorker>(failingContext)
            .setInputData(inputData)
            .build()

        val result = worker.doWork()

        // Assert retry result because database setup crashed the doWork outer try-catch
        assertEquals(Result.retry(), result)
    }
}
