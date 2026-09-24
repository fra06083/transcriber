package com.android.transcriber.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.transcriber.data.model.ScheduledMessage
import com.android.transcriber.data.model.ScheduledMessageStatus
import com.android.transcriber.domain.contact.ContactHelper
import com.android.transcriber.domain.contact.ContactItem
import com.android.transcriber.ui.viewmodel.SchedulerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(
    viewModel: SchedulerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.scheduledMessages.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: In programma, 1: Cronologia, 2: Tutti

    var isAccessibilityActive by remember {
        mutableStateOf(ContactHelper.isAccessibilityServiceEnabled(context))
    }

    // Refresh accessibility status on resume
    DisposableEffect(Unit) {
        isAccessibilityActive = ContactHelper.isAccessibilityServiceEnabled(context)
        onDispose { }
    }

    val pendingMessages = remember(messages) {
        messages.filter { it.status == ScheduledMessageStatus.PENDING }
    }
    val historyMessages = remember(messages) {
        messages.filter { it.status != ScheduledMessageStatus.PENDING }
    }

    val displayedMessages = when (selectedFilterTab) {
        0 -> pendingMessages
        1 -> historyMessages
        else -> messages
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuovo Messaggio", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Messaggi Programmati",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Invio automatico puntuale su WhatsApp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Service Status Card
            if (isAccessibilityActive) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Servizio invio automatico attivo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showAccessibilityHelpDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Guida", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Invio automatico da abilitare",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Attiva l'Accessibilità per inviare senza dover premere Invio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { showAccessibilityHelpDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Attiva", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Exact Alarm permission check (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !viewModel.canScheduleExactAlarms()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Abilita 'Allarmi e promemoria' per spaccare il minuto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Abilita", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Tabs
            SecondaryTabRow(
                selectedTabIndex = selectedFilterTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedFilterTab == 0,
                    onClick = { selectedFilterTab = 0 },
                    text = {
                        Text(
                            "In programma (${pendingMessages.size})",
                            fontWeight = if (selectedFilterTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedFilterTab == 1,
                    onClick = { selectedFilterTab = 1 },
                    text = {
                        Text(
                            "Cronologia (${historyMessages.size})",
                            fontWeight = if (selectedFilterTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedFilterTab == 2,
                    onClick = { selectedFilterTab = 2 },
                    text = {
                        Text(
                            "Tutti (${messages.size})",
                            fontWeight = if (selectedFilterTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content List or Empty State
            if (displayedMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (selectedFilterTab == 0) Icons.Outlined.Schedule else Icons.Outlined.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (selectedFilterTab == 0) "Nessun messaggio in programma"
                            else if (selectedFilterTab == 1) "Nessun messaggio nella cronologia"
                            else "Nessun messaggio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tocca 'Nuovo Messaggio' per programmare un invio WhatsApp e selezionare i contatti direttamente dalla rubrica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(displayedMessages, key = { it.id }) { message ->
                        MessageItemCard(
                            message = message,
                            onSendNow = {
                                viewModel.sendNow(message, context)
                            },
                            onDelete = {
                                viewModel.deleteMessage(message)
                            }
                        )
                    }
                }
            }
        }
    }

    // Accessibility Guide Dialog
    if (showAccessibilityHelpDialog) {
        AccessibilityGuideDialog(
            onDismiss = {
                showAccessibilityHelpDialog = false
                isAccessibilityActive = ContactHelper.isAccessibilityServiceEnabled(context)
            },
            onOpenAppInfo = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onOpenAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
    }

    // Add / Schedule Message ModalBottomSheet
    if (showAddSheet) {
        AddScheduledMessageSheet(
            viewModel = viewModel,
            onDismiss = { showAddSheet = false },
            onConfirm = { phone, name, text, timeMillis ->
                viewModel.scheduleMessage(phone, name, text, timeMillis)
                showAddSheet = false
            }
        )
    }
}

@Composable
fun MessageItemCard(
    message: ScheduledMessage,
    onSendNow: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.ITALIAN)
    val formattedTime = dateFormat.format(Date(message.scheduledTimeMillis))
    val relativeTime = formatRelativeScheduleTime(message.scheduledTimeMillis)

    val displayName = message.contactName?.ifBlank { null } ?: message.phoneNumber
    val initial = (message.contactName?.firstOrNull()?.uppercase()
        ?: message.phoneNumber.firstOrNull { it.isLetterOrDigit() }?.toString()
        ?: "W")

    val avatarColor = remember(displayName) {
        val colors = listOf(
            Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935),
            Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00897B)
        )
        colors[Math.abs(displayName.hashCode()) % colors.size]
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Avatar, Contact info, Status Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = avatarColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Phone
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (message.contactName != null && message.contactName.isNotBlank()) {
                        Text(
                            message.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(message.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message Body Bubble
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Scheduled Time info & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            formattedTime,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (message.status == ScheduledMessageStatus.PENDING) {
                            Text(
                                relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.status == ScheduledMessageStatus.PENDING) {
                        FilledTonalButton(
                            onClick = onSendNow,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Invia ora", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Elimina",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: ScheduledMessageStatus) {
    val (dotColor, bg, fg, label) = when (status) {
        ScheduledMessageStatus.PENDING -> Quadruple(
            Color(0xFFFF9800), Color(0xFFFFF3E0), Color(0xFFE65100), "In attesa"
        )
        ScheduledMessageStatus.SENT -> Quadruple(
            Color(0xFF4CAF50), Color(0xFFE8F5E9), Color(0xFF2E7D32), "Inviato"
        )
        ScheduledMessageStatus.FAILED -> Quadruple(
            Color(0xFFF44336), Color(0xFFFFEBEE), Color(0xFFC62828), "Non riuscito"
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduledMessageSheet(
    viewModel: SchedulerViewModel,
    onDismiss: () -> Unit,
    onConfirm: (phone: String, name: String?, text: String, timeMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var phoneInput by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }

    var selectedMinutesPreset by remember { mutableIntStateOf(5) }
    var customScheduleMillis by remember { mutableStateOf<Long?>(null) }

    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var pendingDateCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    var contactSearchQuery by remember { mutableStateOf("") }
    var isContactSearchOpen by remember { mutableStateOf(false) }

    var hasContactsPermission by remember {
        mutableStateOf(ContactHelper.hasContactsPermission(context))
    }

    val contactSearchResults by viewModel.contactSearchResults.collectAsState()
    val isSearchingContacts by viewModel.isSearchingContacts.collectAsState()

    // Permission launcher for READ_CONTACTS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) {
            viewModel.searchContacts("")
        }
    }

    // System native contact picker launcher (requires zero permission!)
    val systemContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data
            if (contactUri != null) {
                val contact = ContactHelper.getContactFromUri(context, contactUri)
                if (contact != null) {
                    phoneInput = contact.cleanNumber
                    selectedContactName = contact.name
                    isContactSearchOpen = false
                }
            }
        }
    }

    // Trigger initial search or search when query changes
    LaunchedEffect(contactSearchQuery, hasContactsPermission, isContactSearchOpen) {
        if (hasContactsPermission && isContactSearchOpen) {
            viewModel.searchContacts(contactSearchQuery)
        }
    }

    // Preset minute options
    val presetMinutes = listOf(
        Pair("+5m", 5),
        Pair("+15m", 15),
        Pair("+30m", 30),
        Pair("+1h", 60),
        Pair("+3h", 180)
    )

    // Material 3 TimePicker state
    val initialCal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    // Material 3 DatePicker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    // TimePicker Dialog
    if (showTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCal = pendingDateCalendar.apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        customScheduleMillis = targetCal.timeInMillis
                        showTimePickerDialog = false
                    }
                ) {
                    Text("Conferma Orario")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Annulla")
                }
            },
            title = {
                Text("Imposta orario preciso", fontWeight = FontWeight.Bold)
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    // DatePicker Dialog
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcMillis ->
                            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedUtcMillis
                            }
                            pendingDateCalendar = Calendar.getInstance().apply {
                                set(Calendar.YEAR, calUtc.get(Calendar.YEAR))
                                set(Calendar.MONTH, calUtc.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, calUtc.get(Calendar.DAY_OF_MONTH))
                            }
                        }
                        showDatePickerDialog = false
                        showTimePickerDialog = true // Proceed to pick time for that date!
                    }
                ) {
                    Text("Continua all'orario")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Annulla")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Programma Messaggio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            // 1. RECIPIENT SECTION
            Text(
                "Destinatario WhatsApp",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (selectedContactName != null) {
                // Selected Contact Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                selectedContactName?.firstOrNull()?.uppercase() ?: "C",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedContactName ?: "",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                ContactHelper.formatForDisplay(phoneInput),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedContactName = null
                                phoneInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Rimuovi contatto", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                // Contact Search & Pick Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            contactSearchQuery = it
                            isContactSearchOpen = it.isNotBlank()
                        },
                        label = { Text("Numero o Cerca Contatto") },
                        placeholder = { Text("Es. Marco o +39 340...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (phoneInput.isNotBlank()) {
                                IconButton(onClick = {
                                    phoneInput = ""
                                    contactSearchQuery = ""
                                    isContactSearchOpen = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Cancella")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )

                    // Address book button
                    FilledTonalIconButton(
                        onClick = {
                            if (hasContactsPermission) {
                                isContactSearchOpen = !isContactSearchOpen
                                if (isContactSearchOpen) viewModel.searchContacts(contactSearchQuery)
                            } else {
                                // Try launching permission, or fallback directly to system picker
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = "Cerca in Rubrica",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Native System Picker Quick Button
                    IconButton(
                        onClick = {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            systemContactPickerLauncher.launch(pickIntent)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = "Sfoglia rubrica di sistema",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Inline Contact Search Suggestions
                AnimatedVisibility(
                    visible = isContactSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        if (!hasContactsPermission) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Consenti l'accesso ai contatti per cercarli direttamente qui:",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Consenti Contatti")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                            systemContactPickerLauncher.launch(pickIntent)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Sfoglia Rubrica")
                                    }
                                }
                            }
                        } else if (isSearchingContacts) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (contactSearchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nessun contatto trovato con questo nome o numero",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(contactSearchResults, key = { it.id + it.cleanNumber }) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                phoneInput = contact.cleanNumber
                                                selectedContactName = contact.name
                                                isContactSearchOpen = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                contact.name.firstOrNull()?.uppercase() ?: "C",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                contact.name,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                contact.number,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            // 2. MESSAGE TEXT SECTION
            Text(
                "Messaggio",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Scrivi il testo da inviare...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("${messageText.length} caratteri")
                    }
                }
            )

            // Quick Phrase suggestions
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val templates = listOf(
                    "Sto arrivando!",
                    "Ti chiamo tra poco",
                    "Ricordati di...",
                    "Tanti auguri! 🎉",
                    "Ci vediamo più tardi"
                )
                items(templates) { phrase ->
                    SuggestionChip(
                        onClick = {
                            messageText = if (messageText.isBlank()) phrase else "$messageText $phrase"
                        },
                        label = { Text(phrase, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 3. SCHEDULE TIME SECTION
            Text(
                "Orario di Invio",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Quick minute preset chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presetMinutes) { (label, mins) ->
                    FilterChip(
                        selected = customScheduleMillis == null && selectedMinutesPreset == mins,
                        onClick = {
                            customScheduleMillis = null
                            selectedMinutesPreset = mins
                        },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Smart Time buttons: Stasera, Domani mattina, o Personalizzato
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tonight (20:30)
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 30)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        customScheduleMillis = cal.timeInMillis
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Stasera (20:30)", style = MaterialTheme.typography.labelSmall)
                }

                // Tomorrow Morning (09:00)
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        customScheduleMillis = cal.timeInMillis
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Domani (09:00)", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Custom Date & Time Picker button
            FilledTonalButton(
                onClick = {
                    showDatePickerDialog = true
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val targetSchedule = customScheduleMillis ?: (System.currentTimeMillis() + selectedMinutesPreset * 60_000L)
                val sdf = SimpleDateFormat("EEEE d MMMM, HH:mm", Locale.ITALIAN)
                Text(
                    if (customScheduleMillis != null) "Data personalizzata: ${sdf.format(Date(targetSchedule))}"
                    else "Scegli Data e Ora precisa...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Schedule Preview Banner
            val previewTargetTime = customScheduleMillis ?: (System.currentTimeMillis() + selectedMinutesPreset * 60_000L)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Verrà inviato: ${formatScheduleFullDateTime(previewTargetTime)} (${formatRelativeScheduleTime(previewTargetTime)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Confirm Action Button
            val isFormValid = phoneInput.isNotBlank() && messageText.isNotBlank()
            Button(
                onClick = {
                    val targetTime = customScheduleMillis ?: (System.currentTimeMillis() + selectedMinutesPreset * 60_000L)
                    val finalPhone = ContactHelper.formatForDisplay(phoneInput)
                    onConfirm(finalPhone, selectedContactName, messageText, targetTime)
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Programma Invio WhatsApp", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatScheduleFullDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy 'alle' HH:mm", Locale.ITALIAN)
    return sdf.format(Date(millis))
}

private fun formatRelativeScheduleTime(millis: Long): String {
    val diffMillis = millis - System.currentTimeMillis()
    if (diffMillis <= 0) return "adesso"

    val diffMinutes = (diffMillis / 60_000L).toInt()
    val diffHours = diffMinutes / 60
    val remainingMinutes = diffMinutes % 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "tra meno di un minuto"
        diffMinutes < 60 -> "tra $diffMinutes min"
        diffHours < 24 -> {
            if (remainingMinutes > 0) "tra $diffHours h e $remainingMinutes min"
            else "tra $diffHours h"
        }
        diffDays == 1 -> "domani"
        else -> "tra $diffDays giorni"
    }
}

@Composable
fun AccessibilityGuideDialog(
    onDismiss: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                "Sblocco Invio Automatico",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Per inviare il messaggio WhatsApp all'orario esatto senza richiedere il tocco manuale del tasto Invia, Android richiede il servizio di Accessibilità:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("1. Tocca \"1. Apri Info App\" qui sotto", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("2. Tocca i 3 puntini in alto a destra (⋮)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("3. Scegli \"Consenti impostazioni con restrizioni\" e conferma con impronta/PIN (necessario su Android 13+)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("4. Tocca \"2. Apri Accessibilità\" e attiva Transcriber", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("1. Apri Info App (per i 3 puntini)")
                }

                FilledTonalButton(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("2. Apri Accessibilità")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Ho capito", fontWeight = FontWeight.Bold)
            }
        }
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
