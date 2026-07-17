package com.messages.app.ui.chat

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messages.core.db.MessageEntity
import com.messages.designsystem.CategoryColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatViewModelFactory(
    private val app: Application,
    private val threadId: Long,
    private val fallbackAddress: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(app, threadId, fallbackAddress) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: Long,
    onBack: () -> Unit,
    onWhy: (Long) -> Unit,
    fallbackAddress: String? = null,
    /** §8.5.3: terms to highlight when opened from a search result. */
    initialSearchTerms: List<String> = emptyList(),
    /** §8.5.3: the matched message to auto-scroll to. */
    targetMessageId: Long? = null,
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, threadId, fallbackAddress)
    )
    val messages by vm.messages.collectAsState()
    val contactName by vm.contactName.collectAsState()
    val address by vm.address.collectAsState()
    val locked by vm.locked.collectAsState()
    val chatUnlocked by vm.chatUnlocked.collectAsState()
    val pendingAttachment by vm.pendingAttachment.collectAsState()
    val sendError by vm.sendError.collectAsState()
    val simOptions by vm.simOptions.collectAsState()
    val selectedSubId by vm.selectedSubId.collectAsState()
    var showSimMenu by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Attachment sources: gallery (photo picker) and camera (FileProvider target).
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.attach(uri) }
    var cameraTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraTarget?.let { vm.attach(it) } }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showDeleteThreadConfirm by remember { mutableStateOf(false) }

    // ---- In-conversation search (§8.5.3) ----
    var chatSearchActive by remember { mutableStateOf(initialSearchTerms.isNotEmpty()) }
    var chatSearchQuery by remember { mutableStateOf(initialSearchTerms.joinToString(" ")) }
    // The 3-char guard applies to the whole typed query (§8.5.1).
    val searchTerms = remember(chatSearchQuery) {
        if (chatSearchQuery.trim().length >= 3)
            chatSearchQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        else emptyList()
    }
    val matchIndices = remember(messages, searchTerms) {
        if (searchTerms.isEmpty()) emptyList()
        else messages.indices.filter { i ->
            searchTerms.any { t -> messages[i].body.contains(t, ignoreCase = true) }
        }
    }
    var currentMatch by remember { mutableStateOf(0) } // index into matchIndices
    // One-shot jump to the search result this chat was opened from.
    var pendingTarget by remember { mutableStateOf(targetMessageId) }
    LaunchedEffect(messages, matchIndices) {
        val target = pendingTarget ?: return@LaunchedEffect
        val listIndex = messages.indexOfFirst { it.id == target }
        if (listIndex >= 0) {
            pendingTarget = null
            matchIndices.indexOf(listIndex).takeIf { it >= 0 }?.let { currentMatch = it }
            listState.animateScrollToItem(listIndex)
        }
    }
    LaunchedEffect(currentMatch, chatSearchActive) {
        if (chatSearchActive && pendingTarget == null) {
            matchIndices.getOrNull(currentMatch)?.let { listState.animateScrollToItem(it) }
        }
    }
    LaunchedEffect(matchIndices) {
        if (currentMatch >= matchIndices.size) currentMatch = 0
    }

    // Locked-conversation gate (§8.2): nothing renders until authenticated.
    if (locked && !chatUnlocked) {
        com.messages.app.ui.lock.LockScreen(
            title = "This conversation is locked",
            onRequestUnlock = {
                (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
                    com.messages.app.security.AppLock.authenticate(
                        activity, "Unlock conversation",
                        onSuccess = { vm.markChatUnlocked() },
                        onFailure = { onBack() },
                    )
                } ?: vm.markChatUnlocked() // no auth host available — don't strand the user
            },
        )
        return
    }

    LaunchedEffect(messages.size) {
        // Don't fight the search jump/navigation (§8.5.3).
        if (messages.isNotEmpty() && pendingTarget == null && !chatSearchActive) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSendError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (chatSearchActive) {
                // In-conversation search bar (§8.5.3): live query, match count,
                // next/previous arrows.
                TopAppBar(
                    title = {
                        TextField(
                            value = chatSearchQuery,
                            onValueChange = { chatSearchQuery = it; currentMatch = 0 },
                            placeholder = { Text("Search in conversation") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            chatSearchActive = false
                            chatSearchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (searchTerms.isNotEmpty()) {
                            Text(
                                if (matchIndices.isEmpty()) "0/0"
                                else "${currentMatch + 1}/${matchIndices.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatch = (currentMatch - 1 + matchIndices.size) % matchIndices.size
                                }
                            },
                            enabled = matchIndices.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                        }
                        IconButton(
                            onClick = {
                                if (matchIndices.isNotEmpty()) {
                                    currentMatch = (currentMatch + 1) % matchIndices.size
                                }
                            },
                            enabled = matchIndices.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                        }
                    },
                )
            } else {
            TopAppBar(
                title = {
                    Column {
                        Text(contactName ?: address, style = MaterialTheme.typography.titleMedium)
                        if (contactName != null) {
                            Text(
                                address, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { chatSearchActive = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search in conversation")
                    }
                    if (locked) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = "Locked conversation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(18.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (locked) "Unlock conversation" else "Lock conversation") },
                                onClick = {
                                    showChatMenu = false
                                    vm.setConversationLocked(!locked)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete conversation") },
                                onClick = {
                                    showChatMenu = false
                                    showDeleteThreadConfirm = true
                                },
                            )
                        }
                    }
                },
            )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        highlightTerms = if (chatSearchActive) searchTerms else emptyList(),
                        onWhy = { onWhy(msg.id) },
                        onNotSpam = { vm.moveToInbox(msg.id) },
                        onResend = { vm.resend(msg) },
                        onSendNow = { vm.sendScheduledNow(msg.id) },
                        onCancelScheduled = { vm.cancelScheduled(msg.id) },
                        onSnooze = { remindAt -> vm.snooze(msg.id, remindAt) },
                        onStar = { vm.star(msg.id, !msg.starred) },
                        onDelete = { vm.delete(msg.id) },
                    )
                }
            }

            // Pending attachment preview
            if (pendingAttachment != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = pendingAttachment,
                        contentDescription = "Attachment preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(72.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.attach(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
                    }
                }
            }

            // Composer
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { showAttachSheet = true }) {
                    Icon(
                        Icons.Filled.Attachment,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Text message") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                // Scheduled send (§8.2) — text-only, so hidden while an attachment is staged
                if (draft.isNotBlank() && pendingAttachment == null) {
                    IconButton(onClick = { showScheduleDialog = true }) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = "Schedule send",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Dual-SIM indicator + per-chat picker (§8.1) — only with 2+ SIMs
                if (simOptions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showSimMenu = true }) {
                            Icon(
                                Icons.Filled.SimCard,
                                contentDescription = "Choose SIM",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showSimMenu,
                            onDismissRequest = { showSimMenu = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (selectedSubId == null) "• Default SIM" else "Default SIM") },
                                onClick = { vm.selectSim(null); showSimMenu = false },
                            )
                            simOptions.forEach { sim ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (selectedSubId == sim.subId) "• " else "") +
                                                "SIM ${sim.slotIndex + 1} — ${sim.displayName}"
                                        )
                                    },
                                    onClick = { vm.selectSim(sim.subId); showSimMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { vm.sendWithAttachment(draft); draft = "" },
                    enabled = draft.isNotBlank() || pendingAttachment != null,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (showScheduleDialog) {
            ScheduleSendDialog(
                onDismiss = { showScheduleDialog = false },
                onPick = { sendAt ->
                    showScheduleDialog = false
                    vm.scheduleSend(draft, sendAt)
                    draft = ""
                },
            )
        }

        // §6.4: conversation deletion goes to Trash — say so, offer the way back.
        if (showDeleteThreadConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteThreadConfirm = false },
                title = { Text("Delete this conversation?") },
                text = {
                    Text(
                        "All its messages move to Trash and can be restored for " +
                            "60 days (Settings → Trash)."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteThreadConfirm = false
                        vm.deleteThread(onDone = onBack)
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteThreadConfirm = false }) { Text("Cancel") }
                },
            )
        }

        if (showAttachSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    AttachOption(Icons.Filled.Image, "Gallery") {
                        showAttachSheet = false
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AttachOption(Icons.Filled.PhotoCamera, "Camera") {
                        showAttachSheet = false
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file,
                        )
                        cameraTarget = uri
                        cameraCapture.launch(uri)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val OTP_EXTRACT = Regex("""\b(\d{4,8})\b""")

/** Quick time presets shared by schedule-send and snooze ("this evening" = 18:00). */
private fun timePresets(): List<Pair<String, Long>> {
    val now = System.currentTimeMillis()
    fun at(hour: Int, addDays: Int): Long = Calendar.getInstance().run {
        add(Calendar.DAY_OF_YEAR, addDays)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    val evening = at(18, 0).let { if (it > now + 60_000) it else at(18, 1) }
    val morning = at(8, 0).let { if (it > now + 60_000) it else at(8, 1) }
    return listOf(
        "In 1 hour" to now + 60 * 60 * 1000,
        "This evening (6:00 PM)" to evening,
        "Tomorrow morning (8:00 AM)" to morning,
    )
}

private val SCHEDULED_FMT = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSendDialog(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    var step by remember { mutableStateOf("presets") } // presets | date | time
    val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val timeState = rememberTimePickerState(is24Hour = false)

    when (step) {
        "presets" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send later") },
            text = {
                Column {
                    timePresets().forEach { (label, time) ->
                        TextButton(onClick = { onPick(time) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = { step = "date" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pick date & time…", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        "date" -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { step = "time" },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
        "time" -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send at") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker returns UTC midnight; rebuild in the local zone.
                    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        .apply { timeInMillis = dateState.selectedDateMillis ?: return@TextButton }
                    val local = Calendar.getInstance().apply {
                        set(
                            utc.get(Calendar.YEAR), utc.get(Calendar.MONTH),
                            utc.get(Calendar.DAY_OF_MONTH),
                            timeState.hour, timeState.minute, 0,
                        )
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPick(local.timeInMillis.coerceAtLeast(System.currentTimeMillis() + 60_000))
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageEntity,
    highlightTerms: List<String> = emptyList(),
    onWhy: () -> Unit,
    onNotSpam: () -> Unit,
    onResend: () -> Unit,
    onSendNow: () -> Unit,
    onCancelScheduled: () -> Unit,
    onSnooze: (Long) -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isOut = msg.isOutgoing
    val isScheduled = msg.sendStatus == "SCHEDULED"
    var showMenu by remember { mutableStateOf(false) }
    var showSnoozeMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
    ) {
        // Red fraud-warning banner (Stage 2 exception / dangerous label)
        if (msg.fraudWarning || msg.dangerous) {
            Row(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CategoryColors.FraudContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning, contentDescription = null,
                    tint = CategoryColors.Fraud,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (msg.dangerous) "Dangerous — likely fraud. Links are disabled."
                    else "Caution: suspicious link from unverified sender",
                    style = MaterialTheme.typography.labelMedium,
                    color = CategoryColors.Fraud,
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isOut) 18.dp else 4.dp,
                        bottomEnd = if (isOut) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (isOut) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true },
                ),
        ) {
            Column {
                // MMS media attachment
                if (msg.mediaUri != null) {
                    if (msg.mediaMimeType?.startsWith("image/") == true) {
                        AsyncImage(
                            model = File(msg.mediaUri!!),
                            contentDescription = "MMS image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Attachment, contentDescription = null,
                                modifier = Modifier.width(18.dp),
                                tint = if (isOut) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg.mediaMimeType ?: "Attachment",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOut) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (msg.body.isNotBlank()) {
                    // §8.5.3: in-conversation search highlights terms inside the bubble.
                    val bodyText = if (highlightTerms.isEmpty()) AnnotatedString(msg.body)
                    else com.messages.app.ui.search.SearchHighlight.annotate(
                        msg.body, highlightTerms,
                        androidx.compose.ui.text.SpanStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                    Text(
                        bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOut) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            // Long-press actions: copy, star, snooze (§8.2), delete
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.body))
                        showMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (msg.starred) "Unstar" else "Star") },
                    onClick = { onStar(); showMenu = false },
                )
                if (!isScheduled) {
                    DropdownMenuItem(
                        text = { Text("Remind me…") },
                        onClick = { showMenu = false; showSnoozeMenu = true },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { onDelete(); showMenu = false },
                )
            }
            DropdownMenu(expanded = showSnoozeMenu, onDismissRequest = { showSnoozeMenu = false }) {
                timePresets().forEach { (label, time) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSnooze(time); showSnoozeMenu = false },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    isScheduled -> "Scheduled · " + SCHEDULED_FMT.format(Date(msg.timestamp))
                    else -> SimpleDateFormat("HH:mm", Locale.US).format(Date(msg.timestamp)) +
                        if (msg.sendStatus == "FAILED") " · Failed" else ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isScheduled -> MaterialTheme.colorScheme.primary
                    msg.sendStatus == "FAILED" -> CategoryColors.Fraud
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            if (msg.sendStatus == "FAILED") {
                TextButton(onClick = onResend) { Text("Resend") }
            }
        }
        if (isScheduled) {
            Row {
                TextButton(onClick = onSendNow) { Text("Send now") }
                TextButton(onClick = onCancelScheduled) { Text("Cancel") }
            }
        }

        // One-tap OTP copy chip (§8.2)
        if (msg.protectedLabel == "OTP") {
            OTP_EXTRACT.find(msg.body)?.groupValues?.get(1)?.let { code ->
                AssistChip(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    label = { Text("Copy OTP $code") },
                    leadingIcon = {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                    },
                )
            }
        }

        // Filtered-message actions
        if (msg.category in listOf("SPAM", "PROMOTIONS", "REVIEW", "BLOCKED")) {
            Row {
                TextButton(onClick = onNotSpam) { Text("Not spam") }
                TextButton(onClick = onWhy) { Text("Why?") }
            }
        }
    }
}
