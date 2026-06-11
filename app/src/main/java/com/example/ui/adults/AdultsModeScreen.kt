package com.example.ui.adults

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ContactEntity
import com.example.data.local.MessageEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PinAuthScreen(
    viewModel: AdultsModeViewModel? = null,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }
    
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val correctPin = remember { sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234" }

    var isLocked by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(viewModel) {
        while (true) {
            if (viewModel != null) {
                isLocked = viewModel.isLockedOut()
                remainingSeconds = viewModel.getRemainingLockoutTimeSeconds()
            }
            delay(1000L)
        }
    }

    // Shaking effect simulation for incorrect PIN entry
    var isShaking by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isShaking) {
        if (isShaking) {
            shakeOffset.animateTo(
                targetValue = 15f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = -15f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            isShaking = false
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            if (viewModel != null && viewModel.isLockedOut()) {
                enteredPin = ""
                return@LaunchedEffect
            }
            val isValid = if (viewModel != null) {
                viewModel.verifyAndUnlockTransition(enteredPin, correctPin)
            } else {
                enteredPin == correctPin
            }
            if (isValid) {
                errorMessage = null
                onAuthenticated()
            } else {
                if (viewModel != null && viewModel.isLockedOut()) {
                    errorMessage = "Too many failed attempts! Keypad locked for 60s."
                } else {
                    errorMessage = "Incorrect PIN code. Try again!"
                }
                isShaking = true
                enteredPin = ""
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .offset(x = shakeOffset.value.dp)
        ) {
            // Elegant Back Navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("pin_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lock Symbol
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enter Adults Mode",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Type the 4-digit PIN to access sensitive options",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            AnimatedVisibility(
                visible = isLocked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF37542).copy(alpha = 0.15f))
                            .border(1.5.dp, Color(0xFFF37542), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Lockout Warning",
                            tint = Color(0xFFF37542),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Too many failed attempts! For security, PIN entry is locked for ${remainingSeconds}s. 🦜🔒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF37542),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    val dotColor = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    val scale by animateFloatAsState(if (isFilled) 1.2f else 1.0f, label = "dotScale")
                    
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .padding(2.dp)
                            .clip(CircleShape)
                            // If unfilled, show matching inner background center
                            .background(if (isFilled) Color.Transparent else MaterialTheme.colorScheme.surface)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Simple Hint helper
            Text(
                text = "(Hint: Default PIN is $correctPin)",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rowModifier = Modifier.fillMaxWidth(0.85f)
                
                Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                    KeypadButton("1") { if (enteredPin.length < 4) enteredPin += "1" }
                    KeypadButton("2") { if (enteredPin.length < 4) enteredPin += "2" }
                    KeypadButton("3") { if (enteredPin.length < 4) enteredPin += "3" }
                }
                Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                    KeypadButton("4") { if (enteredPin.length < 4) enteredPin += "4" }
                    KeypadButton("5") { if (enteredPin.length < 4) enteredPin += "5" }
                    KeypadButton("6") { if (enteredPin.length < 4) enteredPin += "6" }
                }
                Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                    KeypadButton("7") { if (enteredPin.length < 4) enteredPin += "7" }
                    KeypadButton("8") { if (enteredPin.length < 4) enteredPin += "8" }
                    KeypadButton("9") { if (enteredPin.length < 4) enteredPin += "9" }
                }
                Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Reset action
                    KeypadActionButton(Icons.Default.Refresh, "Clear") { enteredPin = "" }
                    KeypadButton("0") { if (enteredPin.length < 4) enteredPin += "0" }
                    // Backspace Action
                    KeypadActionButton(Icons.Default.Backspace, "Backspace") {
                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    digit: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("keypad_btn_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun KeypadActionButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .testTag("keypad_action_$contentDesc"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdultsModeDashboard(
    viewModel: AdultsModeViewModel,
    onExitDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Contacts UI", "Message Audit", "PIN Settings")
    
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Loro Guardians Panel", fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    Button(
                        onClick = onExitDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("exit_dashboard_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Locks Screen", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Elegant M3 TabRow
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        modifier = Modifier.testTag("dashboard_tab_$index")
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "tabContent"
            ) { targetTab ->
                when (targetTab) {
                    0 -> ContactsManagementTab(viewModel = viewModel)
                    1 -> MessageAuditTab(viewModel = viewModel)
                    2 -> PinSettingsTab(sharedPreferences = sharedPreferences)
                }
            }
        }
    }
}

@Composable
fun ContactsManagementTab(
    viewModel: AdultsModeViewModel
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactAvatar by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }
    val correctPin = remember { sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234" }

    var validatedPinForAdd by remember { mutableStateOf("") }

    // Detailed Contact Auditing Dialog
    var selectedContactAudit by remember { mutableStateOf<ContactEntity?>(null) }

    // Multi-Action Pin verification callback-wrapper
    var pendingActionAfterPin by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // Function helper to guard actions transparently
    val requirePinFor = { action: (String) -> Unit ->
        pendingActionAfterPin = action
    }

    if (pendingActionAfterPin != null) {
        InteractivePinVerificationDialog(
            viewModel = viewModel,
            onDismissRequest = { pendingActionAfterPin = null },
            onPinValidated = { pin ->
                val action = pendingActionAfterPin
                pendingActionAfterPin = null
                action?.invoke(pin)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Approved Contacts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${contacts.size} contacts configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        // Secure add operation with Parent PIN Check
                        requirePinFor { pin ->
                            validatedPinForAdd = pin
                            showAddDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("add_contact_fab_trigger")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Contact", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No contacts configured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactAdminCard(
                            contact = contact,
                            onToggleApproval = { approved ->
                                // Secure toggle contact approval with parent PIN check
                                requirePinFor { pin ->
                                    viewModel.secureToggleContactApproval(contact.id, approved, pin, correctPin)
                                }
                            },
                            onDelete = {
                                // Secure deletion with Parent PIN check
                                requirePinFor { pin ->
                                    viewModel.secureDeleteContact(contact.id, pin, correctPin)
                                }
                            },
                            onClick = {
                                selectedContactAudit = contact
                            }
                        )
                    }
                }
            }
        }

        // Add Contact Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Approved Contact", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Guardians must approve family members to let their kids communicate with them securely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            label = { Text("Contact Name (e.g. Grandma)") },
                            modifier = Modifier.fillMaxWidth().testTag("add_contact_name_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newContactAvatar,
                            onValueChange = { newContactAvatar = it },
                            label = { Text("Avatar URL (Optional)") },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth().testTag("add_contact_avatar_input"),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newContactName.isNotBlank()) {
                                viewModel.secureAddContact(newContactName, newContactAvatar, validatedPinForAdd, correctPin)
                                newContactName = ""
                                newContactAvatar = ""
                                validatedPinForAdd = ""
                                showAddDialog = false
                            }
                        },
                        modifier = Modifier.testTag("add_contact_confirm_btn")
                    ) {
                        Text("Save & Approve", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Detailed Contact Audit dialog
        if (selectedContactAudit != null) {
            val liveContact = contacts.find { it.id == selectedContactAudit!!.id }
            if (liveContact != null) {
                AdultContactDetailDialog(
                    contact = liveContact,
                    viewModel = viewModel,
                    onDismissRequest = { selectedContactAudit = null },
                    onPinProtectedAction = { action ->
                        requirePinFor { pin ->
                            action(pin)
                        }
                    }
                )
            } else {
                selectedContactAudit = null
            }
        }
    }
}

@Composable
fun InteractivePinVerificationDialog(
    onDismissRequest: () -> Unit,
    onPinValidated: (String) -> Unit,
    viewModel: AdultsModeViewModel? = null
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }
    val correctPin = remember { sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234" }
    
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isLocked by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(viewModel) {
        while (true) {
            if (viewModel != null) {
                isLocked = viewModel.isLockedOut()
                remainingSeconds = viewModel.getRemainingLockoutTimeSeconds()
            }
            delay(1000L)
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            if (viewModel != null && viewModel.isLockedOut()) {
                enteredPin = ""
                return@LaunchedEffect
            }
            val isValid = if (viewModel != null) {
                viewModel.verifyPinWithRateLimit(enteredPin, correctPin)
            } else {
                enteredPin == correctPin
            }
            if (isValid) {
                onPinValidated(enteredPin)
            } else {
                if (viewModel != null && viewModel.isLockedOut()) {
                    errorMessage = "Too many failed attempts! locked for 60s."
                } else {
                    errorMessage = "Incorrect PIN. Try again!"
                }
                enteredPin = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Adult Verification Plan", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Please verify your 4-digit Parent PIN to authorize contact changes.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                AnimatedVisibility(
                    visible = isLocked,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF37542).copy(alpha = 0.15f))
                            .border(1.5.dp, Color(0xFFF37542), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Lockout Warning",
                            tint = Color(0xFFF37542),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Locked out! Please wait ${remainingSeconds}s. 🦜🔒",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF37542),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        val color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Small keypad grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    val rowModifier = Modifier.fillMaxWidth(0.9f)
                    
                    Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogKeypadButton("1") { if (enteredPin.length < 4) enteredPin += "1" }
                        DialogKeypadButton("2") { if (enteredPin.length < 4) enteredPin += "2" }
                        DialogKeypadButton("3") { if (enteredPin.length < 4) enteredPin += "3" }
                    }
                    Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogKeypadButton("4") { if (enteredPin.length < 4) enteredPin += "4" }
                        DialogKeypadButton("5") { if (enteredPin.length < 4) enteredPin += "5" }
                        DialogKeypadButton("6") { if (enteredPin.length < 4) enteredPin += "6" }
                    }
                    Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogKeypadButton("7") { if (enteredPin.length < 4) enteredPin += "7" }
                        DialogKeypadButton("8") { if (enteredPin.length < 4) enteredPin += "8" }
                        DialogKeypadButton("9") { if (enteredPin.length < 4) enteredPin += "9" }
                    }
                    Row(rowModifier, horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { enteredPin = "" }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.Refresh, "Clear")
                        }
                        DialogKeypadButton("0") { if (enteredPin.length < 4) enteredPin += "0" }
                        IconButton(
                            onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.Backspace, "Backspace")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun DialogKeypadButton(
    digit: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("dialog_keypad_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AdultContactDetailDialog(
    contact: ContactEntity,
    viewModel: AdultsModeViewModel,
    onDismissRequest: () -> Unit,
    onPinProtectedAction: ((String) -> Unit) -> Unit
) {
    val messagesState = viewModel.getMessagesBetween("default_child_id", contact.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = messagesState.value
    
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }
    val correctPin = remember { sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234" }

    var validatedPinForEdit by remember { mutableStateOf("") }
    
    // Edit contact inputs
    var showEditFields by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(contact.name) }
    var editAvatarUrl by remember { mutableStateOf(contact.avatarUrl ?: "") }

    val sdf = remember { SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("${contact.name} Guardians Audit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile & Modification pane
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Profile Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        if (!showEditFields) {
                            Text("Name: ${contact.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Avatar URL: ${contact.avatarUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        // Request PIN validation before showing Edit inputs
                                        onPinProtectedAction { pin ->
                                            validatedPinForEdit = pin
                                            showEditFields = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("edit_contact_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Default.Edit, "Edit info", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontWeight = FontWeight.Bold)
                                }
                                
                                Button(
                                    onClick = {
                                        // Request PIN validation before Deleting
                                        onPinProtectedAction { pin ->
                                            viewModel.secureDeleteContact(contact.id, pin, correctPin)
                                            onDismissRequest()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("dialog_delete_contact_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, "Delete contact", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Edit mode active after PIN validation
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Contact Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("edit_contact_name_input")
                            )

                            OutlinedTextField(
                                value = editAvatarUrl,
                                onValueChange = { editAvatarUrl = it },
                                label = { Text("Avatar URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("edit_contact_avatar_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (editName.isNotBlank()) {
                                            viewModel.secureSaveContact(ContactEntity(contact.id, editName, editAvatarUrl, contact.isApproved), validatedPinForEdit, correctPin)
                                            showEditFields = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("save_contact_changes_btn")
                                ) {
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showEditFields = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Message History Title
                Text(
                    text = "Loro Messages Chronology (${messages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Scrollable chronological feed
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No messages logged with this contact.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            val isFromMe = message.senderId == "default_child_id"
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (message.mediaType == "video") Icons.Default.Videocam else Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFromMe) "Sent by Child" else "Received from Relative",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = "Type: ${message.mediaType.uppercase()} • Duration: 12s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = sdf.format(Date(message.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    
                                    // Deletion trigger with on Pin check or standard immediate log delete
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteMessageLog(message.id)
                                        },
                                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "On-Demand Delete storage log",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close Audit Panel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ContactAdminCard(
    contact: ContactEntity,
    onToggleApproval: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("contact_admin_card_${contact.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Simulated Avatar Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (contact.isApproved) "Communication Status: Approved" else "Communication Status: Locked",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contact.isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Switch approved state
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Approve",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Switch(
                    checked = contact.isApproved,
                    onCheckedChange = onToggleApproval,
                    modifier = Modifier.testTag("contact_switch_${contact.id}")
                )
            }

            // Delete item
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                    .testTag("contact_delete_${contact.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete contact",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MessageAuditTab(
    viewModel: AdultsModeViewModel
) {
    val messageLogs by viewModel.messageLogs.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault()) }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("loro_prefs", Context.MODE_PRIVATE) }
    val correctPin = remember { sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234" }

    var pendingActionAfterPin by remember { mutableStateOf<((String) -> Unit)?>(null) }

    if (pendingActionAfterPin != null) {
        InteractivePinVerificationDialog(
            viewModel = viewModel,
            onDismissRequest = { pendingActionAfterPin = null },
            onPinValidated = { pin ->
                val action = pendingActionAfterPin
                pendingActionAfterPin = null
                action?.invoke(pin)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Child Conversation Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "As family safety guardians, you have full custody and oversight of recorded communication.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Audited Communications Logs",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Showing ${messageLogs.size} recorded messages in DB",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (messageLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No communication details logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(messageLogs, key = { it.id }) { log ->
                        MessageLogItemCard(
                            log = log,
                            formattedTime = remember(log.timestamp) { sdf.format(Date(log.timestamp)) },
                            onDelete = {
                                pendingActionAfterPin = { pin ->
                                    viewModel.secureDeleteMessageLog(log.id, pin, correctPin)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageLogItemCard(
    log: MessageEntity,
    formattedTime: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_log_card_${log.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon to indicate the communication medium
            val icon = if (log.mediaType == "video") Icons.Default.Videocam else Icons.Default.Mic
            val iconColor = if (log.mediaType == "video") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (log.senderId == "default_child_id") "Sent by Kid" else "Sourced from relative (${log.senderId})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!log.isRead) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type: ${log.mediaType.uppercase()} Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Remove/Clear Log capability
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), CircleShape)
                    .testTag("log_delete_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Delete communication log",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PinSettingsTab(
    sharedPreferences: SharedPreferencesWrapper
) {
    var currentPin by remember { mutableStateOf(sharedPreferences.getString("adult_mode_pin", "1234") ?: "1234") }
    var inputNewPin by remember { mutableStateOf("") }
    var inputConfirmPin by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("") }
    var isSuccessStatus by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Adults Mode PIN Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "For security reasons, we strongly recommend changing the default PIN passcode ('1234') to verify adults entering secure settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "Current PIN active: $currentPin",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = inputNewPin,
            onValueChange = { if (it.length <= 4) inputNewPin = it },
            label = { Text("New 4-Digit PIN") },
            placeholder = { Text("E.g., 2580") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
        )

        OutlinedTextField(
            value = inputConfirmPin,
            onValueChange = { if (it.length <= 4) inputConfirmPin = it },
            label = { Text("Confirm New PIN") },
            placeholder = { Text("E.g., 2580") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
        )

        Button(
            onClick = {
                if (inputNewPin.length != 4) {
                    feedbackMessage = "Error: PIN must be exactly 4 digits."
                    isSuccessStatus = false
                } else if (inputNewPin != inputConfirmPin) {
                    feedbackMessage = "Error: PIN match confirmation failed."
                    isSuccessStatus = false
                } else {
                    sharedPreferences.putString("adult_mode_pin", inputNewPin)
                    currentPin = inputNewPin
                    inputNewPin = ""
                    inputConfirmPin = ""
                    feedbackMessage = "PIN Code changed successfully!"
                    isSuccessStatus = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_new_pin_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Update PIN", fontWeight = FontWeight.Bold)
        }

        if (feedbackMessage.isNotEmpty()) {
            Text(
                text = feedbackMessage,
                color = if (isSuccessStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

// Wrapper interface to easily pass shared preferences cleanly to avoid raw android content mock exceptions if loaded inside mock runner environments
class SharedPreferencesWrapper(private val prefs: android.content.SharedPreferences) {
    fun getString(key: String, defaultValue: String): String? {
        return prefs.getString(key, defaultValue)
    }
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

@Composable
fun PinSettingsTab(sharedPreferences: android.content.SharedPreferences) {
    val wrapper = remember(sharedPreferences) { SharedPreferencesWrapper(sharedPreferences) }
    PinSettingsTab(sharedPreferences = wrapper)
}
