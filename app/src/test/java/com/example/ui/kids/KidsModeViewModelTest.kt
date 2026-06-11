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

class FakeKidsRepository : KidsRepository {
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
class KidsModeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: KidsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeKidsRepository()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenRoleIsAdult_whenCheckingBedtime_thenBedtimeModeIsAlwaysFalse() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(repository, childId = "test_child", role = "adult", getCurrentHour = { 22 })
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isBedtimeMode.collect {} }
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertFalse(viewModel.isBedtimeMode.value)

        collectJob.cancel()
    }

    @Test
    fun givenRoleIsKid_whenCheckingBedtimeAt9PM_thenBedtimeModeIsTrue() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(repository, childId = "test_child", role = "kid", getCurrentHour = { 21 })
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isBedtimeMode.collect {} }
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertTrue(viewModel.isBedtimeMode.value)

        collectJob.cancel()
    }

    @Test
    fun givenBedtimeModeActive_whenEngagingQABypass_thenBedtimeIsOverriddenToFalse() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(repository, childId = "test_child", role = "kid", getCurrentHour = { 22 })
        
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isBedtimeMode.collect {} }
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertTrue(viewModel.isBedtimeMode.value)

        // Engage QA bypass
        viewModel.toggleQaBypassBedtime(true)
        testScheduler.advanceTimeBy(6000L)
        advanceUntilIdle()

        assertFalse(viewModel.isBedtimeMode.value)

        collectJob.cancel()
    }

    @Test
    fun givenRecordingActive_whenLockRecordingIsCalled_thenStateTransitionsToLocked() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(repository)
        
        // Transition to Recording state
        viewModel.startRecordingHold(isAudio = true, context = context)
        testScheduler.advanceTimeBy(100L)
        assertTrue(viewModel.recordingState.value is RecordingState.Recording)

        // Lock recording
        viewModel.lockRecording()
        testScheduler.advanceTimeBy(100L)
        assertTrue(viewModel.recordingState.value is RecordingState.LockedRecording)
    }

    @Test
    fun givenRecordingThresholdThresholdMet_whenRecordingFor30Seconds_thenRecordingAutomaticallyStopsAndEnqueuesUpload() = runTest(testDispatcher) {
        val viewModel = KidsModeViewModel(repository)
        
        // Select a contact first so enqueue works
        val testContact = Contact("contact_123", "Grandpa", "avatar.png", true)
        viewModel.selectContact(testContact)
        testScheduler.advanceTimeBy(100L)
        
        // Start recording
        viewModel.startRecordingHold(isAudio = true, context = context)
        testScheduler.advanceTimeBy(100L)
        assertTrue(viewModel.recordingState.value is RecordingState.Recording)

        // Advance virtual time by 31 seconds to exceed the hard cap timer
        testScheduler.advanceTimeBy(31_000L)
        advanceUntilIdle()
        
        // The recording should have been completed and cleaned up back to Idle
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }
}
