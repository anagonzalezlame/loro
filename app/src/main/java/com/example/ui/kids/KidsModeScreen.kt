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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun rememberDebouncedAction(
    debounceMs: Long = 500L,
    onClick: () -> Unit
): () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return remember(onClick, debounceMs) {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceMs) {
                lastClickTime = currentTime
                onClick()
            }
        }
    }
}

@Composable
fun <T> rememberDebouncedCallback(
    debounceMs: Long = 500L,
    onClick: (T) -> Unit
): (T) -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return remember(onClick, debounceMs) {
        { item ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceMs) {
                lastClickTime = currentTime
                onClick(item)
            }
        }
    }
}

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

    val contactMessages by viewModel.selectedContactMessages.collectAsStateWithLifecycle()
    var playingMessage by remember { mutableStateOf<com.example.domain.model.Message?>(null) }

    var showDebugOverlay by remember { mutableStateOf(false) }
    var pendingUploads by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val debouncedExit = rememberDebouncedAction { onExitKidsMode() }
    val debouncedSelectContact = rememberDebouncedCallback<Contact> { viewModel.selectContact(it) }
    val debouncedClearContact = rememberDebouncedAction { viewModel.clearSelectedContact() }
    val debouncedPlayMessage = rememberDebouncedCallback<com.example.domain.model.Message> { playingMessage = it }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
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
                    com.example.ui.components.LoroLogo(
                        sizePoints = 28.dp,
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
                    TextButton(onClick = debouncedExit) {
                        Text("Exit", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (isBedtime) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E))
                    .padding(paddingValues)
                    .pointerInput(Unit) {
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
                                viewModel.toggleQaBypassBedtime(!qaBypassBedtime)
                            }
                        }
                    },
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
                    .background(MaterialTheme.colorScheme.tertiary)
            ) {
                if (selectedContact == null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (messages.isNotEmpty()) {
                            Text(
                                text = "Look who sent you a message! 🌟",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    MessageCard(
                                        message = message,
                                        onClick = { debouncedPlayMessage(message) }
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "Who do you want to talk to? 😊",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                        ContactGrid(
                            contacts = contacts,
                            onContactSelected = debouncedSelectContact,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    RecordMessageScreen(
                        contact = selectedContact!!,
                        messages = contactMessages,
                        uploadState = uploadState,
                        recordingState = recordingState,
                        onClose = debouncedClearContact,
                        onStartRecordingHold = { isAudio -> viewModel.startRecordingHold(isAudio, context) },
                        onLockRecording = { viewModel.lockRecording() },
                        onReleaseRecordingHold = { viewModel.releaseRecordingHold(context) },
                        onCancelRecording = { viewModel.cancelRecording() },
                        onSendRecording = { isAudio -> viewModel.finishRecording(isAudio, context) },
                        onPlayMessage = debouncedPlayMessage
                    )
                }

                if (playingMessage != null) {
                    VisualMessagePlayerDialog(
                        message = playingMessage!!,
                        onDismissRequest = { playingMessage = null },
                        onMarkAsRead = { viewModel.markMessageAsRead(it) }
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
    if (contacts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = Color(0xFFFDE82F), // ParrotYellow
                    trackColor = Color(0xFF3465B4) // ParrotPrimaryBlue
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading your family guardians... 💛",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (message.mediaType == "video") Icons.Default.Videocam else Icons.Default.Mic,
                contentDescription = "Play ${message.mediaType} message",
                tint = MaterialTheme.colorScheme.onSecondary,
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
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
                    .background(Color.White), // Provide a clean background for images
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { 50.dp.toPx() }
    var lastGestureTime by remember { mutableStateOf(0L) }

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
                    
                    val now = System.currentTimeMillis()
                    if (now - lastGestureTime < 800L) {
                        return@awaitEachGesture
                    }
                    lastGestureTime = now

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
                            if (yOffset > thresholdPx && !locked) {
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
    messages: List<com.example.domain.model.Message>,
    uploadState: UploadState,
    recordingState: RecordingState,
    onClose: () -> Unit,
    onStartRecordingHold: (isAudio: Boolean) -> Unit,
    onLockRecording: () -> Unit,
    onReleaseRecordingHold: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: (isAudio: Boolean) -> Unit,
    onPlayMessage: (com.example.domain.model.Message) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row with Close button on top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = "Avatar of ${contact.name}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                    Text(
                        text = "Your Family Member",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History container in Kids Mode: Beautiful, scrollable chronological timeline message feed!
        Text(
            text = "Your past messages with ${contact.name}: 💕",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet. Send a first recording to say Hello! 👋",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isFromMe = message.senderId == "default_child_id"
                    Card(
                        onClick = { onPlayMessage(message) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("kids_chat_row_msg_${message.id}"),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Kids-friendly Play/Audio Thumbnails
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (message.isRead) Icons.Default.PlayArrow else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFromMe) "Done by You" else "From ${contact.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (message.mediaType == "video") "🎥 Video message" else "🎙️ Voice message",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            // Read receipt status indicator (e.g. blue dot for unread, checkmark/star for read)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.isRead) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Seen",
                                        tint = Color(0xFFFFD700), // golden star!
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = " Seen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2196F3))
                                    )
                                    Text(
                                        text = " New",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2196F3),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recording Action Area
        AnimatedVisibility(visible = uploadState is UploadState.Uploading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = Color(0xFFFDE82F), // ParrotYellow
                    trackColor = Color(0xFF3465B4) // ParrotPrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sending voice/video and optimizing media... 🦜☁️",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(visible = uploadState is UploadState.Success) {
            Text(
                text = "Sent! 🎉",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4CAF50)
            )
        }

        AnimatedVisibility(visible = uploadState is UploadState.Error) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF37542).copy(alpha = 0.15f)) // Beautiful translucent ParrotOrange!
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning, // Orange warning icon!
                    contentDescription = "Upload issue icon",
                    tint = Color(0xFFF37542), // ParrotOrange!
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Oh-oh! Send had a hiccup, but Loro saved it to send later when you're online! 🦜☁️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
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
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Swipe up", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall)
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(36.dp))
                        }
                    }
                }
                is RecordingState.LockedRecording -> {
                    val isAudio = recordingState.isAudio
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Recording Locked 🛑",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send message", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualMessagePlayerDialog(
    message: com.example.domain.model.Message,
    onDismissRequest: () -> Unit,
    onMarkAsRead: (String) -> Unit
) {
    // Automatically mark the message as read when we open the player!
    LaunchedEffect(message.id) {
        if (!message.isRead) {
            onMarkAsRead(message.id)
        }
    }

    var playProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (playProgress < 1f) {
            delay(100)
            playProgress += 0.03f
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (message.mediaType == "video") "Playing Video Message 🎬" else "Listening to Voice Message 🔊",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive Visual Animation depending on MediaType
                if (message.mediaType == "video") {
                    Box(
                        modifier = Modifier
                            .size(220.dp, 150.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseGroup"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF4C29).copy(alpha = 0.7f),
                                            Color(0xFF330066)
                                        )
                                    )
                                )
                        )
                        
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(64.dp)
                                .aspectRatio(1f)
                                .scale(scale)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(220.dp, 100.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val times = listOf(800, 1000, 600, 1200, 700)
                            times.forEachIndexed { idx, duration ->
                                val heightScale by rememberInfiniteTransition(label = "wave_$idx").animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(duration, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulseWave"
                                )
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .height((60 * heightScale).dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }

                // Progress Indicator
                LinearProgressIndicator(
                    progress = { playProgress.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = if (playProgress >= 1f) "Playback Finished!" else "Playing... ${((playProgress.coerceAtMost(1f)) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Close Player 🌈", fontWeight = FontWeight.Bold)
            }
        }
    )
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
            Text("QA DEBUG OVERLAY", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
                    onCheckedChange = onBypassToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}
