package com.example.ui.kids

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.Contact
import java.io.File
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import com.example.domain.model.Message

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.BroadcastReceiver
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NightsStay
import androidx.work.WorkManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsModeScreen(
    viewModel: KidsModeViewModel,
    onExitKidsMode: () -> Unit
) {
    val contacts by viewModel.approvedContacts.collectAsStateWithLifecycle()
    val selectedContact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val isBedtime by viewModel.isBedtimeMode.collectAsStateWithLifecycle()
    val qaBypassBedtime by viewModel.qaBypassBedtime.collectAsStateWithLifecycle()

    var showDebugOverlay by remember { mutableStateOf(false) }
    var pendingUploads by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pauseAndReleaseResources()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Track WorkManager queue status
        val workManager = WorkManager.getInstance(context)
        val flow = workManager.getWorkInfosByTagFlow("com.example.data.worker.UploadWorker")
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            flow.collect { workInfos ->
                pendingUploads = workInfos?.count { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING } ?: 0
            }
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            job.cancel()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (scale > 0) {
                        val batteryPct = level * 100 / scale.toFloat()
                        if (batteryPct < 15f) {
                            viewModel.pauseAndReleaseResources()
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "My Family", 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var tapCount = 1
                                var lastTapTime = down.uptimeMillis
                                
                                while (tapCount < 3) {
                                    val nextEvent = withTimeoutOrNull(300) { awaitFirstDown() }
                                    if (nextEvent != null) {
                                        tapCount++
                                        lastTapTime = nextEvent.uptimeMillis
                                    } else {
                                        break
                                    }
                                }
                                if (tapCount == 3) {
                                    showDebugOverlay = !showDebugOverlay
                                }
                            }
                        }
                    ) 
                },
                actions = {
                    TextButton(onClick = onExitKidsMode) {
                        Text("Exit", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isBedtime) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E))
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = "Sleeping",
                        tint = Color(0xFFF3C623),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Shh... It's bedtime.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.secondary)
            ) {
                if (selectedContact == null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                    if (messages.isNotEmpty()) {
                        Text(
                            text = "New Messages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            lazyItems(messages, key = { it.id }) { message ->
                                MessageCard(message = message, onClick = { /* Play logic */ })
                            }
                        }
                    }
                    
                    Text(
                        text = "Send a Message",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                    )
                    ContactGrid(
                        contacts = contacts,
                        onContactSelected = { viewModel.selectContact(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                RecordMessageScreen(
                    contact = selectedContact!!,
                    uploadState = uploadState,
                    recordingState = recordingState,
                    onClose = { viewModel.clearSelectedContact() },
                    onStartRecordingHold = { isAudio -> viewModel.startRecordingHold(isAudio, context) },
                    onLockRecording = { viewModel.lockRecording() },
                    onReleaseRecordingHold = { viewModel.releaseRecordingHold(context) },
                    onCancelRecording = { viewModel.cancelRecording() },
                    onSendRecording = { isAudio -> viewModel.finishRecording(isAudio, context) }
                )
            }
            if (showDebugOverlay) {
                QADebugOverlay(
                    uid = "mockKidId1",
                    role = "kid",
                    isOffline = pendingUploads > 0,
                    pendingUploads = pendingUploads,
                    bypassBedtime = qaBypassBedtime,
                    onBypassToggle = { viewModel.toggleQaBypassBedtime(it) }
                )
            }
        }
    }
}
}

@Composable
fun ContactGrid(
    contacts: List<Contact>,
    onContactSelected: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(contacts, key = { it.id }) { contact ->
            ContactCard(contact = contact, onClick = { onContactSelected(contact) })
        }
    }
}

@Composable
fun MessageCard(
    message: Message,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(120.dp)
            .clickable(onClick = onClick)
            .testTag("message_card_${message.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (message.mediaType == "video") Icons.Default.Videocam else Icons.Default.Mic,
                contentDescription = "Play ${message.mediaType} message",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(48.dp)
            )
            androidx.compose.animation.AnimatedContent(
                targetState = message.isRead,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                transitionSpec = {
                    androidx.compose.animation.scaleIn() togetherWith androidx.compose.animation.scaleOut()
                },
                label = "read_receipt_animation"
            ) { isRead ->
                if (isRead) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Seen Receipt",
                        tint = Color(0xFFFFD700), // Gold Star
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .semantics { contentDescription = "Unread message" }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .testTag("contact_card_${contact.id}")
            .semantics {
                contentDescription = "Contact ${contact.name}"
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = contact.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LockableRecordButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onStart: () -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(containerColor, CircleShape)
            .semantics {
                this.contentDescription = contentDescription
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onStart()
                    var locked = false
                    var isDown = true
                    while (isDown) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            isDown = false
                            if (!locked) onRelease()
                        } else {
                            val yOffset = down.position.y - change.position.y
                            if (yOffset > 150f && !locked) {
                                locked = true
                                onLock()
                            }
                        }
                    }
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(20.dp).padding(bottom = 4.dp))
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun RecordMessageScreen(
    contact: Contact,
    uploadState: UploadState,
    recordingState: RecordingState,
    onClose: () -> Unit,
    onStartRecordingHold: (isAudio: Boolean) -> Unit,
    onLockRecording: () -> Unit,
    onReleaseRecordingHold: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: (isAudio: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AsyncImage(
            model = contact.avatarUrl,
            contentDescription = "Avatar of ${contact.name}",
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Message ${contact.name}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondary
        )

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(visible = uploadState is UploadState.Uploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = uploadState is UploadState.Success) {
            Text(
                text = "Sent!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
        }

        AnimatedVisibility(visible = uploadState is UploadState.Idle || uploadState is UploadState.Error) {
            when (recordingState) {
                is RecordingState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LockableRecordButton(
                            icon = Icons.Default.Mic,
                            contentDescription = "Hold to record audio",
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            testTag = "record_audio_button",
                            onStart = { onStartRecordingHold(true) },
                            onLock = { onLockRecording() },
                            onRelease = { onReleaseRecordingHold() }
                        )

                        LockableRecordButton(
                            icon = Icons.Default.Videocam,
                            contentDescription = "Hold to record video",
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            testTag = "record_video_button",
                            onStart = { onStartRecordingHold(false) },
                            onLock = { onLockRecording() },
                            onRelease = { onReleaseRecordingHold() }
                        )
                    }
                }
                is RecordingState.Recording -> {
                    val icon = if (recordingState.isAudio) Icons.Default.Mic else Icons.Default.Videocam
                    val desc = if (recordingState.isAudio) "Recording audio. Swipe up to lock." else "Recording video. Swipe up to lock."
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Swipe up", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelSmall)
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(16.dp))
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(48.dp))
                        }
                    }
                }
                is RecordingState.LockedRecording -> {
                    val isAudio = recordingState.isAudio
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Recording Locked",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingActionButton(
                                onClick = { onCancelRecording() },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Icon(Icons.Default.Cancel, "Cancel recording")
                            }
                            FloatingActionButton(
                                onClick = { onSendRecording(isAudio) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send message", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun QADebugOverlay(
    uid: String,
    role: String,
    isOffline: Boolean,
    pendingUploads: Int,
    bypassBedtime: Boolean,
    onBypassToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 80.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text("QA DEBUG OVERLAY", color = Color.Red, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Current UID: $uid", color = Color.White)
            Text("Role: $role", color = Color.White)
            Text("Network Status: ${if (isOffline) "Offline" else "Online"}", color = Color.White)
            Text("Pending Uploads (WorkManager): $pendingUploads", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bypass Bedtime Restriction", color = Color.White, modifier = Modifier.weight(1f))
                Switch(
                    checked = bypassBedtime,
                    onCheckedChange = onBypassToggle
                )
            }
        }
    }
}
