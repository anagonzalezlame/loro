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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class FakeContactDao : ContactDao {
    val contactsList = mutableListOf<ContactEntity>()
    val flow = MutableStateFlow<List<ContactEntity>>(emptyList())

    override fun getAllContacts(): Flow<List<ContactEntity>> = flow

    override suspend fun insertContact(contact: ContactEntity) {
        val index = contactsList.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contactsList[index] = contact
        } else {
            contactsList.add(contact)
        }
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

class FakeMessageDao : MessageDao {
    val messagesList = mutableListOf<MessageEntity>()
    val childFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

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

    override suspend fun markAsRead(messageId: String) {
        val index = messagesList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val old = messagesList[index]
            messagesList[index] = MessageEntity(
                id = old.id,
                senderId = old.senderId,
                recipientId = old.recipientId,
                mediaType = old.mediaType,
                localUri = old.localUri,
                remoteUrl = old.remoteUrl,
                timestamp = old.timestamp,
                isRead = true
            )
            childFlow.value = messagesList.toList()
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        messagesList.removeAll { it.id == messageId }
        childFlow.value = messagesList.toList()
    }

    override suspend fun deleteOldMessages(timestamp: Long) {
        messagesList.removeAll { it.timestamp < timestamp }
        childFlow.value = messagesList.toList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdultsModeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var contactDao: FakeContactDao
    private lateinit var messageDao: FakeMessageDao
    private lateinit var repository: AdultsRepository
    private lateinit var viewModel: AdultsModeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        contactDao = FakeContactDao()
        messageDao = FakeMessageDao()
        repository = AdultsRepository(null, null)
        viewModel = AdultsModeViewModel(messageDao, contactDao, repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when PIN code is correct, verifyPin returns true`() {
        assertTrue(viewModel.verifyPin("1234", "1234"))
    }

    @Test
    fun `when PIN code is incorrect, verifyPin returns false`() {
        assertFalse(viewModel.verifyPin("4321", "1234"))
    }

    @Test
    fun `when verifyAndUnlockTransition is called with correct PIN, dashboard transitions unlock successfully`() = runTest {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.isAdultModeUnlocked.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.isAdultModeUnlocked.value)

        val success = viewModel.verifyAndUnlockTransition("1234", "1234")
        assertTrue(success)
        assertTrue(viewModel.isAdultModeUnlocked.value)
        assertNotEquals(0L, viewModel.lastVerificationTime.value)

        collectJob.cancel()
    }

    @Test
    fun `when verifyAndUnlockTransition is called with incorrect PIN, dashboard transitions do not unlock`() = runTest {
        val collectJob = launch(Dispatchers.Unconfined) { viewModel.isAdultModeUnlocked.collect {} }
        advanceUntilIdle()

        val success = viewModel.verifyAndUnlockTransition("error_pin", "1234")
        assertFalse(success)
        assertFalse(viewModel.isAdultModeUnlocked.value)
        assertEquals(0L, viewModel.lastVerificationTime.value)

        collectJob.cancel()
    }

    @Test
    fun `when lockAdultMode is triggered, transition states should immediately clear`() = runTest {
        val collectJob1 = launch(Dispatchers.Unconfined) { viewModel.isAdultModeUnlocked.collect {} }
        val collectJob2 = launch(Dispatchers.Unconfined) { viewModel.lastVerificationTime.collect {} }
        advanceUntilIdle()

        viewModel.verifyAndUnlockTransition("1234", "1234")
        assertTrue(viewModel.isAdultModeUnlocked.value)

        viewModel.lockAdultMode()
        assertFalse(viewModel.isAdultModeUnlocked.value)
        assertEquals(0L, viewModel.lastVerificationTime.value)

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun `when executing secured actions, bad PIN should fail execution and correct PIN should pass`() = runTest {
        var actionExecuted = false
        val badResult = viewModel.executeSecuredAction("9999", "1234") {
            actionExecuted = true
        }
        assertFalse(badResult)
        assertFalse(actionExecuted)

        val successResult = viewModel.executeSecuredAction("1234", "1234") {
            actionExecuted = true
        }
        assertTrue(successResult)
        assertTrue(actionExecuted)
    }

    @Test
    fun `when secured CRUD proxies are called with correct PIN, operations are executed and repository mutations occur`() = runTest {
        // Test secure add contact
        val addSuccess = viewModel.secureAddContact("Grandma", null, "1234", "1234")
        assertTrue(addSuccess)
        advanceUntilIdle()

        assertEquals(1, contactDao.contactsList.size)
        val contact = contactDao.contactsList[0]
        assertEquals("Grandma", contact.name)

        // Test secure toggle contact approval
        val toggleSuccess = viewModel.secureToggleContactApproval(contact.id, false, "1234", "1234")
        assertTrue(toggleSuccess)
        advanceUntilIdle()
        assertFalse(contactDao.contactsList[0].isApproved)

        // Test secure save contact
        val updatedContact = ContactEntity(contact.id, "Grandma Sue", contact.avatarUrl, contact.isApproved)
        val saveSuccess = viewModel.secureSaveContact(updatedContact, "1234", "1234")
        assertTrue(saveSuccess)
        advanceUntilIdle()
        assertEquals("Grandma Sue", contactDao.contactsList[0].name)

        // Test secure delete contact
        val deleteSuccess = viewModel.secureDeleteContact(contact.id, "1234", "1234")
        assertTrue(deleteSuccess)
        advanceUntilIdle()
        assertTrue(contactDao.contactsList.isEmpty())
    }
}
