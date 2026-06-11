package com.example.ui.kids

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Contact
import com.example.domain.model.Message
import com.example.domain.repository.KidsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

class EnvironmentFakeKidsRepositoryUnit : KidsRepository {
    override fun getApprovedContacts(childId: String): Flow<List<Contact>> = flowOf(emptyList())
    override fun getMessages(childId: String): Flow<List<Message>> = flowOf(emptyList())
    override fun getMessagesBetween(childId: String, contactId: String): Flow<List<Message>> = flowOf(emptyList())
    override suspend fun markAsRead(messageId: String) {}
    override suspend fun refreshMessages(childId: String) {}
    override suspend fun uploadVideoMessage(contactId: String, videoFile: File): Result<Unit> = Result.success(Unit)
    override suspend fun uploadAudioMessage(contactId: String, audioFile: File): Result<Unit> = Result.success(Unit)
    override fun releaseHardwareResources() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnvironmentRulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: KidsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = EnvironmentFakeKidsRepositoryUnit()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenSystemClockInDowntime_whenCheckingBedtimeMode_thenEmitsTrueState() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(
            repository = repository,
            childId = "kid_user",
            role = "kid",
            getCurrentHour = { 22 }
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isBedtimeMode.collect {}
        }
        
        // Let WhileSubscribed and init flow complete
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertTrue(viewModel.isBedtimeMode.value)

        collectJob.cancel()
    }

    @Test
    fun givenActiveBedtime_whenQABypassToggledTrue_thenBypassesRulesAndReturnsFalse() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(
            repository = repository,
            childId = "kid_user",
            role = "kid",
            getCurrentHour = { 23 }
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isBedtimeMode.collect {}
        }
        
        // Let WhileSubscribed and init flow complete
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertTrue(viewModel.isBedtimeMode.value)

        // Toggle QA Bypass to true
        viewModel.toggleQaBypassBedtime(true)
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertFalse(viewModel.isBedtimeMode.value)

        // Disengage QA Bypass
        viewModel.toggleQaBypassBedtime(false)
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertTrue(viewModel.isBedtimeMode.value)

        collectJob.cancel()
    }
}
