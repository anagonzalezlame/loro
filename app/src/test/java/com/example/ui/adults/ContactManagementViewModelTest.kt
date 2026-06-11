package com.example.ui.adults

import com.example.data.local.ContactDao
import com.example.data.local.ContactEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.repository.AdultsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContactTestFakeContactDao : ContactDao {
    private val contactsList = mutableListOf<ContactEntity>()
    private val flow = MutableStateFlow<List<ContactEntity>>(emptyList())

    override fun getAllContacts(): Flow<List<ContactEntity>> = flow

    override suspend fun insertContact(contact: ContactEntity) {
        contactsList.add(contact)
        flow.value = contactsList.toList()
    }

    override suspend fun insertContacts(contacts: List<ContactEntity>) {
        contactsList.addAll(contacts)
        flow.value = contactsList.toList()
    }

    override suspend fun deleteContact(contactId: String) {
        contactsList.removeAll { it.id == contactId }
        flow.value = contactsList.toList()
    }

    override suspend fun updateApproval(contactId: String, approved: Boolean) {
        val index = contactsList.indexOfFirst { it.id == contactId }
        if (index != -1) {
            val old = contactsList[index]
            contactsList[index] = ContactEntity(old.id, old.name, old.avatarUrl, approved)
            flow.value = contactsList.toList()
        }
    }
}

class ContactTestFakeMessageDao : MessageDao {
    private val messagesList = mutableListOf<MessageEntity>()
    private val childFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

    override fun getMessagesForChild(childId: String): Flow<List<MessageEntity>> = childFlow

    override fun getMessagesBetween(childId: String, contactId: String): Flow<List<MessageEntity>> {
        return flowOf(messagesList.filter { 
            (it.senderId == childId && it.recipientId == contactId) || 
            (it.senderId == contactId && it.recipientId == childId) 
        })
    }

    override suspend fun insertMessage(message: MessageEntity) {
        messagesList.add(message)
        childFlow.value = messagesList.toList()
    }

    override suspend fun insertMessages(messages: List<MessageEntity>) {
        messagesList.addAll(messages)
        childFlow.value = messagesList.toList()
    }

    override suspend fun markAsRead(messageId: String) {}
    override suspend fun deleteMessage(messageId: String) {}
    override suspend fun deleteOldMessages(timestamp: Long) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContactManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var contactDao: ContactTestFakeContactDao
    private lateinit var messageDao: ContactTestFakeMessageDao
    private lateinit var repository: AdultsRepository
    private lateinit var viewModel: AdultsModeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        contactDao = ContactTestFakeContactDao()
        messageDao = ContactTestFakeMessageDao()
        repository = AdultsRepository(null, null)
        viewModel = AdultsModeViewModel(messageDao, contactDao, repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenIncorrectPin3Times_whenValidating_thenLocksInputFor60Seconds() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.lockoutEndTime.collect {}
        }

        // Initially not locked out
        assertFalse(viewModel.isLockedOut())

        // 1st wrong attempt
        var result = viewModel.verifyAndUnlockTransition("wrong", "1234")
        assertFalse(result)
        assertFalse(viewModel.isLockedOut())

        // 2nd wrong attempt
        result = viewModel.verifyAndUnlockTransition("wrong", "1234")
        assertFalse(result)
        assertFalse(viewModel.isLockedOut())

        // 3rd wrong attempt
        result = viewModel.verifyAndUnlockTransition("wrong", "1234")
        assertFalse(result)
        
        // Locked out now
        assertTrue(viewModel.isLockedOut())
        assertTrue(viewModel.getRemainingLockoutTimeSeconds() > 0)

        collectJob.cancel()
    }

    @Test
    fun givenLockedState_when60SecondsElapses_thenLockStateIsRemoved() = runTest(testDispatcher) {
        viewModel.incrementFailedAttempts()
        viewModel.incrementFailedAttempts()
        viewModel.incrementFailedAttempts()
        
        assertTrue(viewModel.isLockedOut())

        viewModel.resetFailedAttempts()
        assertFalse(viewModel.isLockedOut())
        assertEquals(0, viewModel.failedAttempts.value)
    }
}
