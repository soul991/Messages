package com.messages.app.ui.chat

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messages.app.ui.common.ContactAvatar
import com.messages.app.ui.common.sharedThreadAvatar
import com.messages.core.db.MessageEntity
import com.messages.designsystem.Haptics
import com.messages.designsystem.Motion
import com.messages.designsystem.categoryPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatViewModelFactory(
    private val app: Application,
    private val threadId: Long,
    private val fallbackAddress: String? = null,
) : androidx.lifecycle.AbstractSavedStateViewModelFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: androidx.lifecycle.SavedStateHandle,
    ): T = ChatViewModel(app, threadId, fallbackAddress, handle) as T
}

/**
 * Chat list rows (§9): messages grouped when consecutive, same direction and
 * < 3 min apart; date pills between days. `first`/`last` are group edges and
 * drive the bubble corner shape (tail on the last bubble of a group).
 */
private sealed interface ChatItem {
    val key: String

    data class DateHeader(val ts: Long) : ChatItem {
        override val key get() = "d$ts"
    }

    data class Msg(val m: MessageEntity, val first: Boolean, val last: Boolean) : ChatItem {
        override val key get() = "m${m.id}"
    }
}

private const val GROUP_GAP_MS = 3 * 60 * 1000L

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun groupsTogether(a: MessageEntity, b: MessageEntity): Boolean =
    a.isOutgoing == b.isOutgoing &&
        sameDay(a.timestamp, b.timestamp) &&
        b.timestamp - a.timestamp < GROUP_GAP_MS &&
        a.sendStatus != "SCHEDULED" && b.sendStatus != "SCHEDULED"

private fun buildChatItems(messages: List<MessageEntity>): List<ChatItem> {
    val out = ArrayList<ChatItem>(messages.size + 8)
    for (i in messages.indices) {
        val m = messages[i]
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        if (prev == null || !sameDay(prev.timestamp, m.timestamp)) {
            out += ChatItem.DateHeader(m.timestamp)
        }
        out += ChatItem.Msg(
            m = m,
            first = prev == null || !groupsTogether(prev, m),
            last = next == null || !groupsTogether(m, next),
        )
    }
    return out
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
    /** Direct share (§8.2): pre-filled composer text. */
    initialDraft: String = "",
    /** Tap on the top-bar name/number → contact detail page. */
    onOpenContact: () -> Unit = {},
    /** Phase 4 item 14: forward selected text into another conversation. */
    onForward: (threadId: Long, text: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, threadId, fallbackAddress)
    )
    val messages by vm.messages.collectAsState()
    val contactName by vm.contactName.collectAsState()
    val address by vm.address.collectAsState()
    val category by vm.category.collectAsState()
    val locked by vm.locked.collectAsState()
    val chatUnlocked by vm.chatUnlocked.collectAsState()
    val pendingAttachment by vm.pendingAttachment.collectAsState()
    val sendError by vm.sendError.collectAsState()
    val simOptions by vm.simOptions.collectAsState()
    val selectedSubId by vm.selectedSubId.collectAsState()
    val bubbleStyleId by vm.bubbleStyleId.collectAsState()
    val wallpaperId by vm.wallpaperId.collectAsState()
    val wallpaperVersion by vm.wallpaperVersion.collectAsState()
    var showSimMenu by remember { mutableStateOf(false) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // Verified-sender badge (Phase 2): decided by the engine from the sender
    // type + the LATEST INCOMING message's fraud/protected state — a scam
    // impersonating a bank suppresses all trust chrome.
    var showBadgeSheet by remember { mutableStateOf(false) }
    val senderBadge = remember(messages, address, contactName) {
        val latestIn = messages.lastOrNull { !it.isOutgoing }
        com.messages.protection.SenderBadges.badgeFor(
            address = address,
            isContact = contactName != null,
            dangerous = latestIn?.dangerous == true || latestIn?.fraudWarning == true,
            protectedLabel = latestIn?.protectedLabel,
        )
    }
    // Drafts (§8.1): restore the saved draft unless direct-share provided one.
    var draft by remember {
        mutableStateOf(
            initialDraft.ifEmpty { com.messages.app.ui.common.DraftStore.get(context, threadId) }
        )
    }
    // Debounced write-through; sending sets draft = "" which clears it.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { draft }.collectLatest { text ->
            kotlinx.coroutines.delay(400)
            com.messages.app.ui.common.DraftStore.save(context, threadId, text)
        }
    }
    val latestDraft = androidx.compose.runtime.rememberUpdatedState(draft)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.messages.app.ui.common.DraftStore.save(context, threadId, latestDraft.value)
        }
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val items = remember(messages) { buildChatItems(messages) }

    // Attachment sources: gallery (photo picker) and camera (FileProvider target).
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.attach(uri) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.importWallpaper(uri) }
    // Camera target lives in the VM's SavedStateHandle: the camera app kills
    // our process at will, and TakePicture only reports success — the URI must
    // survive so the result can still be attached after a cold restart.
    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) vm.cameraTarget.value?.let { vm.attach(it) } }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showDeleteThreadConfirm by remember { mutableStateOf(false) }
    var showQuickReplies by remember { mutableStateOf(false) }

    // ---- Message multi-select (Phase 4 item 14) ----
    var selectedMsgIds by remember { mutableStateOf(setOf<Long>()) }
    val msgSelectionActive = selectedMsgIds.isNotEmpty()
    var showForwardPicker by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = msgSelectionActive) {
        selectedMsgIds = emptySet()
    }
    fun selectedTexts(): String = messages
        .filter { it.id in selectedMsgIds }
        .sortedBy { it.timestamp }
        .joinToString("\n") { it.body }
        .trim()

    // Conversation export (Phase 4 item 16).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) vm.exportConversation(uri) { ok ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "Conversation exported" else "Export failed"
                )
            }
        }
    }

    // In-app message text size (Phase 4 item 15).
    val textScale = remember {
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getFloat("message_text_scale", 1f)
    }

    // Link previews (Phase 4 item 9): opt-in, read once per screen entry.
    val linkPreviewsEnabled = remember { LinkPreview.enabled(context) }

    // Pinned messages (Phase 4 item 6): local-only ids from PinStore.
    val allPins by PinStore.pins.collectAsState()
    LaunchedEffect(Unit) { PinStore.pinsFor(context, threadId) } // triggers initial load
    val pinnedIds = allPins[threadId].orEmpty()
    val pinnedMessages = remember(messages, pinnedIds) {
        messages.filter { it.id in pinnedIds }
    }
    // Which pinned message the banner currently points to (tap cycles).
    var pinnedCursor by remember { mutableStateOf(0) }

    // SIM name resolver for the per-message info sheet (Phase 4 item 7).
    val simNameFor: (Int?) -> String = remember(simOptions) {
        { subId ->
            when {
                subId == null -> "Default"
                else -> simOptions.firstOrNull { it.subId == subId }
                    ?.let { "SIM ${it.slotIndex + 1} — ${it.displayName}" }
                    ?: "SIM (id $subId)"
            }
        }
    }

    // ---- In-conversation search (§8.5.3) ----
    var chatSearchActive by remember { mutableStateOf(initialSearchTerms.isNotEmpty()) }
    var chatSearchQuery by remember { mutableStateOf(initialSearchTerms.joinToString(" ")) }
    // The 3-char guard applies to the whole typed query (§8.5.1).
    val searchTerms = remember(chatSearchQuery) {
        if (chatSearchQuery.trim().length >= 3)
            chatSearchQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        else emptyList()
    }
    // §8.5 debt fix: match on the engine-normalized text too, so obfuscated
    // spam ("F.R.E.E") is findable in-conversation just like in global search.
    val normalizedTerms = remember(searchTerms) {
        searchTerms.map { t ->
            runCatching { com.messages.protection.Normalizer.normalize(t).normalizedText.trim() }
                .getOrDefault(t.lowercase())
        }
    }
    // Indices into `items` (date headers never match).
    val matchIndices = remember(items, searchTerms, normalizedTerms) {
        if (searchTerms.isEmpty()) emptyList()
        else items.indices.filter { i ->
            val msg = (items[i] as? ChatItem.Msg)?.m ?: return@filter false
            searchTerms.indices.any { k ->
                msg.body.contains(searchTerms[k], ignoreCase = true) ||
                    (normalizedTerms[k].isNotBlank() &&
                        msg.normalizedBody.contains(normalizedTerms[k]))
            }
        }
    }
    var currentMatch by remember { mutableStateOf(0) } // index into matchIndices
    // One-shot jump to the search result this chat was opened from.
    var pendingTarget by remember { mutableStateOf(targetMessageId) }
    LaunchedEffect(items, matchIndices) {
        val target = pendingTarget ?: return@LaunchedEffect
        val listIndex = items.indexOfFirst { (it as? ChatItem.Msg)?.m?.id == target }
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

    // First composition lands at the bottom instantly; new messages animate.
    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(items.size) {
        // Don't fight the search jump/navigation (§8.5.3).
        if (items.isNotEmpty() && pendingTarget == null && !chatSearchActive) {
            if (firstScroll) listState.scrollToItem(items.size - 1)
            else listState.animateScrollToItem(items.size - 1)
        }
        if (items.isNotEmpty()) firstScroll = false
    }
    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSendError()
        }
    }
    // Failed-send feedback: when a message flips to FAILED while this chat is
    // open, surface the mapped reason immediately. The first emission seeds
    // the baseline so pre-existing failures don't re-announce on entry.
    val failedIds = remember(messages) {
        messages.filter { it.sendStatus == "FAILED" }.map { it.id }.toSet()
    }
    var seenFailedIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(failedIds) {
        val baseline = seenFailedIds
        seenFailedIds = failedIds
        if (baseline == null) return@LaunchedEffect
        val freshId = (failedIds - baseline).maxOrNull() ?: return@LaunchedEffect
        val fresh = messages.firstOrNull { it.id == freshId } ?: return@LaunchedEffect
        // Short = standard M3 auto-dismiss (~4s); swipe still dismisses early.
        val result = snackbarHostState.showSnackbar(
            message = com.messages.core.send.SendFailure.reasonFor(fresh.sendResultCode),
            actionLabel = "Resend",
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            vm.resend(fresh)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (msgSelectionActive) {
                // Message multi-select bar (Phase 4 item 14).
                TopAppBar(
                    title = { Text("${selectedMsgIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedMsgIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val text = selectedTexts()
                            if (text.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(text))
                            }
                            selectedMsgIds = emptySet()
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            selectedMsgIds.forEach { vm.star(it, true) }
                            selectedMsgIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Star, contentDescription = "Star")
                        }
                        IconButton(
                            onClick = { showForwardPicker = true },
                            enabled = selectedTexts().isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "Forward")
                        }
                        IconButton(onClick = {
                            val text = selectedTexts()
                            if (text.isNotBlank()) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                                            },
                                            "Share messages",
                                        )
                                    )
                                }
                            }
                            selectedMsgIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            selectedMsgIds.forEach { vm.delete(it) }
                            val n = selectedMsgIds.size
                            selectedMsgIds = emptySet()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "$n message${if (n == 1) "" else "s"} moved to Trash"
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    },
                )
            } else if (chatSearchActive) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        if (matchIndices.isEmpty()) "No matches"
                                        else "Match ${currentMatch + 1} of ${matchIndices.size}"
                                },
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
            // One-time "tap for contact info" hint (plan §2, WA affordance):
            // shown as the subtitle until the header is tapped once, ever.
            val hintPrefs = remember {
                context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            }
            var contactHintDone by remember {
                mutableStateOf(hintPrefs.getBoolean("contact_info_hint_done", false))
            }
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // Google-Messages-style: header tap opens contact detail.
                        modifier = Modifier.clickable {
                            if (!contactHintDone) {
                                contactHintDone = true
                                hintPrefs.edit().putBoolean("contact_info_hint_done", true).apply()
                            }
                            onOpenContact()
                        },
                    ) {
                        // Shared element with the list row's avatar (§9).
                        ContactAvatar(
                            contactName ?: address,
                            category,
                            size = 38.dp,
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.sharedThreadAvatar(threadId),
                            photoUri = com.messages.app.ui.common.rememberContactPhoto(address.ifBlank { null }),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Single line + ellipsis so the badge never
                                // wraps the header (queued Phase 2 cosmetic).
                                Text(
                                    contactName ?: address,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                senderBadge?.let { b ->
                                    Spacer(Modifier.width(4.dp))
                                    com.messages.app.ui.common.SenderBadgeIcon(
                                        b,
                                        onClick = { showBadgeSheet = true },
                                    )
                                }
                            }
                            when {
                                !contactHintDone -> Text(
                                    "Tap here for contact info",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                contactName != null -> Text(
                                    address, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                                text = { Text("Customize chat") },
                                onClick = {
                                    showChatMenu = false
                                    showCustomizeSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (locked) "Unlock conversation" else "Lock conversation") },
                                onClick = {
                                    showChatMenu = false
                                    vm.setConversationLocked(!locked)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export conversation") },
                                onClick = {
                                    showChatMenu = false
                                    val safeName = (contactName ?: address).replace(
                                        Regex("""[^A-Za-z0-9+_-]"""), "_",
                                    ).take(40)
                                    exportLauncher.launch("Messages-$safeName.txt")
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
            // Pinned-messages banner (Phase 4 item 6): tap scrolls to the
            // pinned message and cycles when several are pinned. Local-only.
            if (pinnedMessages.isNotEmpty()) {
                val current = pinnedMessages[pinnedCursor.coerceIn(pinnedMessages.indices)]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val idx = items.indexOfFirst { (it as? ChatItem.Msg)?.m?.id == current.id }
                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            pinnedCursor = (pinnedCursor + 1) % pinnedMessages.size
                        },
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PushPin, contentDescription = "Pinned message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            current.body.ifBlank { "Pinned message" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (pinnedMessages.size > 1) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(pinnedCursor % pinnedMessages.size) + 1}/${pinnedMessages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Per-chat wallpaper (§8.2): gradient preset or an imported photo
            // under a soft surface scrim so bubbles stay legible.
            val wallpaperBrush = ChatStyle.wallpaperBrush(wallpaperId)
            val outBubbleColors = ChatStyle.bubbleColors(bubbleStyleId)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (wallpaperBrush != null) Modifier.background(wallpaperBrush) else Modifier)
            ) {
                if (wallpaperId == ChatStyle.WALLPAPER_PHOTO) {
                    val photo = remember(wallpaperVersion) { ChatStyle.photoFile(context, threadId) }
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(photo)
                            .memoryCacheKey("wp_$threadId-$wallpaperVersion")
                            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items,
                        key = { it.key },
                        contentType = { if (it is ChatItem.Msg) "msg" else "date" },
                    ) { item ->
                        val itemModifier = Modifier.animateItem(
                            fadeInSpec = Motion.effectsDefault(),
                            placementSpec = Motion.spatialDefault(),
                            fadeOutSpec = Motion.effectsFast(),
                        )
                        when (item) {
                            is ChatItem.DateHeader -> DatePill(item.ts, itemModifier)
                            is ChatItem.Msg -> MessageBubble(
                                msg = item.m,
                                firstInGroup = item.first,
                                lastInGroup = item.last,
                                outBubbleColors = outBubbleColors,
                                highlightTerms = if (chatSearchActive) searchTerms else emptyList(),
                                onWhy = { onWhy(item.m.id) },
                                onNotSpam = {
                                    // §9: satisfying "message moved" moment.
                                    Haptics.confirm(view)
                                    vm.moveToInbox(item.m.id)
                                    scope.launch { snackbarHostState.showSnackbar("Moved to Inbox") }
                                },
                                onResend = { vm.resend(item.m) },
                                onSendNow = { vm.sendScheduledNow(item.m.id) },
                                onCancelScheduled = { vm.cancelScheduled(item.m.id) },
                                onSnooze = { remindAt -> vm.snooze(item.m.id, remindAt) },
                                onStar = { vm.star(item.m.id, !item.m.starred) },
                                onDelete = { vm.delete(item.m.id) },
                                pinned = item.m.id in pinnedIds,
                                onPinToggle = {
                                    Haptics.tick(view)
                                    PinStore.setPinned(
                                        context, threadId, item.m.id,
                                        pinned = item.m.id !in pinnedIds,
                                    )
                                },
                                linkPreviewsEnabled = linkPreviewsEnabled,
                                simNameFor = simNameFor,
                                textScale = textScale,
                                selectionMode = msgSelectionActive,
                                selected = item.m.id in selectedMsgIds,
                                onToggleSelect = {
                                    Haptics.tick(view)
                                    selectedMsgIds =
                                        if (item.m.id in selectedMsgIds) selectedMsgIds - item.m.id
                                        else selectedMsgIds + item.m.id
                                },
                                modifier = itemModifier,
                            )
                        }
                    }
                }

                // Floating scroll-to-bottom (§9), springs in when scrolled up.
                val showJump by remember { derivedStateOf { listState.canScrollForward } }
                // Fully qualified: the ColumnScope.AnimatedVisibility extension
                // otherwise shadows the top-level overload inside this Box.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showJump,
                    enter = scaleIn(Motion.spatialFast()) + fadeIn(Motion.effectsDefault()),
                    exit = scaleOut(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest")
                    }
                }
            }

            // Replyability (SenderAnalyzer): alphanumeric sender IDs are
            // one-way — no composer, explain instead (Google Messages parity).
            val canReply = remember(address) {
                address.isBlank() || address.split(";").all { recipient ->
                    runCatching {
                        com.messages.protection.SenderAnalyzer.canReceiveReplies(recipient)
                    }.getOrDefault(true)
                }
            }
            if (!canReply) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "You can't reply to this conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
            // Pending attachment preview
            if (canReply && pendingAttachment != null) {
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

            // Composer — pill field, spring-scaled send button (§9).
            if (canReply) Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { showAttachSheet = true }) {
                    Icon(
                        Icons.Filled.Attachment,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Refs' pill anatomy (plan §2): 40dp rx-18 field with the ⚡
                // quick-replies button living inside the pill's right edge.
                ComposerField(
                    value = draft,
                    onValueChange = { draft = it },
                    onQuickReplies = { showQuickReplies = true },
                    modifier = Modifier.weight(1f),
                )
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
                        DropdownMenu(
                            expanded = showSimMenu,
                            onDismissRequest = { showSimMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (selectedSubId == null) "• Default SIM" else "Default SIM") },
                                onClick = { vm.selectSim(null); showSimMenu = false },
                            )
                            simOptions.forEach { sim ->
                                DropdownMenuItem(
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
                Spacer(Modifier.width(4.dp))
                AnimatedVisibility(
                    visible = draft.isNotBlank() || pendingAttachment != null,
                    enter = scaleIn(Motion.spatialFast()) + fadeIn(Motion.effectsDefault()),
                    exit = scaleOut(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
                ) {
                    FilledIconButton(
                        onClick = {
                            Haptics.confirm(view)
                            vm.sendWithAttachment(draft)
                            draft = ""
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }

        if (showBadgeSheet && senderBadge != null) {
            com.messages.app.ui.common.SenderBadgeSheet(
                senderBadge,
                onDismiss = { showBadgeSheet = false },
            )
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

        // Per-chat customization sheet (§8.2).
        if (showCustomizeSheet) {
            CustomizeChatSheet(
                currentBubble = bubbleStyleId,
                currentWallpaper = wallpaperId,
                onBubble = { Haptics.tick(view); vm.setBubbleStyle(it) },
                onWallpaper = { Haptics.tick(view); vm.setWallpaper(it) },
                onPickPhoto = {
                    wallpaperPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDismiss = { showCustomizeSheet = false },
            )
        }

        // Forward-to-conversation picker (Phase 4 item 14): recents + search.
        if (showForwardPicker) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showForwardPicker = false }) {
                var query by remember { mutableStateOf("") }
                val candidates by androidx.compose.runtime.produceState(
                    initialValue = emptyList<com.messages.core.db.ConversationEntity>(), query,
                ) {
                    value = vm.conversationsForForward(query)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                    Text("Forward to…", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search conversations") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { conv ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val text = selectedTexts()
                                    showForwardPicker = false
                                    selectedMsgIds = emptySet()
                                    if (text.isNotBlank()) onForward(conv.threadId, text)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ContactAvatar(
                                conv.contactName ?: conv.address,
                                conv.category,
                                size = 40.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                conv.contactName ?: conv.address,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (candidates.isEmpty()) {
                        Text(
                            "No conversations found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }

        // Quick-reply templates sheet (Phase 4 item 8): tap fills the composer.
        if (showQuickReplies) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showQuickReplies = false }) {
                val templates by QuickReplies.templates.collectAsState()
                LaunchedEffect(Unit) { QuickReplies.load(context) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                    Text("Quick replies", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Manage templates in Settings → Conversations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    templates.forEach { template ->
                        Text(
                            template,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = if (draft.isBlank()) template else "$draft $template"
                                    showQuickReplies = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    if (templates.isEmpty()) {
                        Text(
                            "No templates yet — add some in Settings → Conversations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
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
                        vm.setCameraTarget(uri)
                        cameraCapture.launch(uri)
                    }
                }
            }
        }
    }
}

/**
 * Composer pill (plan §2): 40dp-min rx-18 field on surfaceContainerHigh,
 * growing to 5 lines; the ⚡ quick-replies affordance sits inside the pill's
 * right edge (the refs' sticker-icon slot) so it reads native, not bolted-on.
 */
@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    onQuickReplies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = 5,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .heightIn(min = 40.dp)
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            "Text message",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                IconButton(onClick = onQuickReplies, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "Quick replies",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

/** Per-chat customization sheet (§8.2): bubble color swatches + wallpapers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeChatSheet(
    currentBubble: String,
    currentWallpaper: String,
    onBubble: (String) -> Unit,
    onWallpaper: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Customize chat", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            Text(
                "Bubble color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChatStyle.bubblePresets.forEach { preset ->
                    val fill = if (preset.id == "default") MaterialTheme.colorScheme.primary
                    else if (com.messages.designsystem.LocalDarkTheme.current) preset.darkContainer
                    else preset.lightContainer
                    val selected = currentBubble == preset.id
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .then(
                                if (selected) Modifier.border(
                                    3.dp, MaterialTheme.colorScheme.onSurface, CircleShape,
                                ) else Modifier
                            )
                            .clickable { onBubble(preset.id) }
                            .semantics {
                                contentDescription =
                                    "${preset.name} bubble color" + if (selected) ", selected" else ""
                            },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Wallpaper",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChatStyle.wallpaperPresets.forEach { preset ->
                    val selected = currentWallpaper == preset.id
                    val colors =
                        if (com.messages.designsystem.LocalDarkTheme.current) preset.dark else preset.light
                    WallpaperTile(
                        label = preset.name,
                        selected = selected,
                        onClick = { onWallpaper(preset.id) },
                    ) {
                        if (colors.isEmpty()) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                                    )
                            )
                        }
                    }
                }
                WallpaperTile(
                    label = "Photo",
                    selected = currentWallpaper == ChatStyle.WALLPAPER_PHOTO,
                    onClick = onPickPhoto,
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Image, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 56.dp, height = 84.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (selected) Modifier.border(
                        3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp),
                    ) else Modifier
                )
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "$label wallpaper" + if (selected) ", selected" else ""
                },
        ) { preview() }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private val DATE_PILL_FMT = SimpleDateFormat("EEE, d MMM", Locale.US)
private val DATE_PILL_YEAR_FMT = SimpleDateFormat("d MMM yyyy", Locale.US)

@Composable
private fun DatePill(ts: Long, modifier: Modifier = Modifier) {
    val label = remember(ts) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val thisYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = ts
        when {
            sameDay(ts, now) -> "Today"
            sameDay(ts, now - 24 * 60 * 60 * 1000) -> "Yesterday"
            cal.get(Calendar.YEAR) == thisYear -> DATE_PILL_FMT.format(Date(ts))
            else -> DATE_PILL_YEAR_FMT.format(Date(ts))
        }
    }
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MessageEntity,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    /** Outgoing bubble container/on pair — per-chat customizable (§8.2). */
    outBubbleColors: Pair<Color, Color>,
    highlightTerms: List<String> = emptyList(),
    onWhy: () -> Unit,
    onNotSpam: () -> Unit,
    onResend: () -> Unit,
    onSendNow: () -> Unit,
    onCancelScheduled: () -> Unit,
    onSnooze: (Long) -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    pinned: Boolean = false,
    onPinToggle: () -> Unit = {},
    linkPreviewsEnabled: Boolean = false,
    /** SIM display name for the info sheet; null subId = default SIM. */
    simNameFor: (Int?) -> String = { "Default" },
    /** In-app message text size (Phase 4 item 15); 1.0 = default. */
    textScale: Float = 1f,
    /** Multi-select (Phase 4 item 14): taps toggle instead of opening menus. */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val context = LocalContext.current
    val isOut = msg.isOutgoing
    val isScheduled = msg.sendStatus == "SCHEDULED"
    var showMenu by remember { mutableStateOf(false) }
    var showSnoozeMenu by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSelectDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val fraudPalette = categoryPalette("SPAM")
    // Refs' bubble grid (plan §2): bubbles cap at ~76% of screen width.
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.76f).dp

    // Smart text actions (Phase 4 item 5): platform TextClassifier entities,
    // NEVER on Spam/Blocked or Dangerous/fraud-flagged messages.
    val smartEligible = SmartText.eligible(msg.category, msg.dangerous, msg.fraudWarning)
    val smartSpans by androidx.compose.runtime.produceState(
        initialValue = emptyList<SmartText.Span>(), msg.id, smartEligible,
    ) {
        value = if (smartEligible && msg.body.isNotBlank()) {
            SmartText.spansFor(context, msg.id, msg.body)
        } else emptyList()
    }

    // Grouped-bubble corners (§9): big outer corners, tight corners between
    // group neighbours, and a tail corner on the group's last bubble.
    val big = 20.dp
    val cont = 8.dp
    val tail = 4.dp
    val bubbleShape = if (isOut) RoundedCornerShape(
        topStart = big, bottomStart = big,
        topEnd = if (firstInGroup) big else cont,
        bottomEnd = if (lastInGroup) tail else cont,
    ) else RoundedCornerShape(
        topEnd = big, bottomEnd = big,
        topStart = if (firstInGroup) big else cont,
        bottomStart = if (lastInGroup) tail else cont,
    )

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = if (firstInGroup) 6.dp else 0.dp),
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
    ) {
        // Red fraud-warning banner (Stage 2 exception / dangerous label)
        if (msg.fraudWarning || msg.dangerous) {
            Row(
                Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fraudPalette?.container ?: MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning, contentDescription = null,
                    tint = fraudPalette?.tint ?: MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (msg.dangerous) "Dangerous — likely fraud. Links are disabled."
                    else "Caution: suspicious link from unverified sender",
                    style = MaterialTheme.typography.labelMedium,
                    color = fraudPalette?.onContainer ?: MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Box(
            Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(bubbleShape)
                .background(
                    if (isOut) outBubbleColors.first
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .then(
                    if (selected) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, bubbleShape,
                    ) else Modifier
                )
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() },
                    onLongClickLabel = "Message options",
                    onLongClick = {
                        Haptics.longPress(view)
                        if (selectionMode) onToggleSelect() else showMenu = true
                    },
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
                                tint = if (isOut) outBubbleColors.second
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg.mediaMimeType ?: "Attachment",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOut) outBubbleColors.second
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (msg.body.isNotBlank()) {
                    // §8.5.3: in-conversation search highlights terms inside the bubble.
                    val highlighted = if (highlightTerms.isEmpty()) AnnotatedString(msg.body)
                    else com.messages.app.ui.search.SearchHighlight.annotate(
                        msg.body, highlightTerms,
                        androidx.compose.ui.text.SpanStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                    // Smart spans layer tappable actions on top (Phase 4 item 5).
                    val linkColor = if (isOut) outBubbleColors.second
                    else MaterialTheme.colorScheme.primary
                    val bodyText = if (smartSpans.isEmpty()) highlighted
                    else androidx.compose.ui.text.buildAnnotatedString {
                        append(highlighted)
                        smartSpans.forEach { span ->
                            val end = minOf(span.end, msg.body.length)
                            if (span.start >= end) return@forEach
                            addStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    color = linkColor,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                ),
                                span.start, end,
                            )
                            addLink(
                                androidx.compose.ui.text.LinkAnnotation.Clickable(span.type) {
                                    SmartText.performAction(context, msg.body, span)
                                },
                                span.start, end,
                            )
                        }
                    }
                    // In-app text size (Phase 4 item 15) — bubble text only.
                    val baseStyle = MaterialTheme.typography.bodyLarge
                    val scaledStyle = if (textScale == 1f) baseStyle else baseStyle.copy(
                        fontSize = baseStyle.fontSize * textScale,
                        lineHeight = baseStyle.lineHeight * textScale,
                    )
                    // Plan §2: 12×8dp inner grid; timestamp + delivery status
                    // live INSIDE the bubble, bottom-right (both refs).
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            bodyText,
                            style = scaledStyle,
                            color = if (isOut) outBubbleColors.second
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (!isScheduled) {
                            Spacer(Modifier.height(2.dp))
                            BubbleMetaRow(
                                msg = msg, isOut = isOut,
                                color = if (isOut) outBubbleColors.second
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                } else if (!isScheduled) {
                    // Media-only bubble: the timestamp still lives inside.
                    BubbleMetaRow(
                        msg = msg, isOut = isOut,
                        color = if (isOut) outBubbleColors.second
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Long-press actions: copy, select, star, pin, snooze, info, delete
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.body))
                        showMenu = false
                    },
                )
                if (msg.body.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Select text") },
                        onClick = { showMenu = false; showSelectDialog = true },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Select messages") },
                    onClick = { showMenu = false; onToggleSelect() },
                )
                DropdownMenuItem(
                    text = { Text(if (msg.starred) "Unstar" else "Star") },
                    onClick = { onStar(); showMenu = false },
                )
                if (!isScheduled) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "Unpin" else "Pin") },
                        onClick = { onPinToggle(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Remind me…") },
                        onClick = { showMenu = false; showSnoozeMenu = true },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Info") },
                    onClick = { showMenu = false; showInfoSheet = true },
                )
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

        // The old below-group meta line is gone (plan §2) — time/status live
        // inside the bubble. Scheduled and failed sends keep a line below so
        // their status (and its actions) is never hidden.
        if (isScheduled) {
            Text(
                "Scheduled · " + SCHEDULED_FMT.format(Date(msg.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        if (msg.sendStatus == "FAILED") {
            // Metadata under the failed bubble, never a full-width row — an
            // unconstrained Row here stretches edge-to-edge and reads as a
            // separate message entry between bubbles.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = maxBubbleWidth),
            ) {
                Text(
                    com.messages.core.send.SendFailure.reasonFor(msg.sendResultCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 4.dp),
                )
                TextButton(onClick = onResend) { Text("Resend") }
            }
        }
        if (isScheduled) {
            Row {
                TextButton(onClick = onSendNow) { Text("Send now") }
                TextButton(onClick = onCancelScheduled) { Text("Cancel") }
            }
        }

        // Opt-in link preview (Phase 4 item 9): Inbox messages only, never
        // filtered folders, never Dangerous/fraud-flagged — even when enabled.
        if (linkPreviewsEnabled && msg.category == "INBOX" &&
            !msg.dangerous && !msg.fraudWarning
        ) {
            LinkPreview.firstUrl(msg.body)?.let { url ->
                LinkPreviewCard(url)
            }
        }

        if (showSelectDialog) {
            AlertDialog(
                onDismissRequest = { showSelectDialog = false },
                title = { Text("Select text") },
                text = {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(msg.body, style = MaterialTheme.typography.bodyLarge)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectDialog = false }) { Text("Done") }
                },
            )
        }

        if (showInfoSheet) {
            MessageInfoSheet(
                msg = msg,
                simName = simNameFor(msg.subId),
                onDismiss = { showInfoSheet = false },
            )
        }

        // One-tap OTP copy chip (§8.2); same extractor as the notification action.
        if (msg.protectedLabel == "OTP") {
            com.messages.protection.OtpExtractor.extract(msg.body)?.let { code ->
                AssistChip(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    label = { Text("Copy OTP $code") },
                    leadingIcon = {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                    },
                )
            }
        }

        // Filtered-message actions — compact row in the banner's visual
        // language (plan §2), not full-height buttons.
        if (msg.category in listOf("SPAM", "PROMOTIONS", "REVIEW", "BLOCKED")) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CompactAction("Not spam", onNotSpam)
                CompactAction("Why?", onWhy)
                // Carrier spam reporting (Phase 4 item 17): incoming spam only.
                if (msg.category == "SPAM" && !isOut) {
                    CompactAction("Report") { showReportDialog = true }
                }
            }
        }

        if (showReportDialog) {
            CarrierReportDialog(msg = msg, onDismiss = { showReportDialog = false })
        }
    }
}

private val BUBBLE_TIME_FMT = SimpleDateFormat("HH:mm", Locale.US)

/**
 * In-bubble time + delivery status (plan §2), bottom-right like both refs:
 * 11sp time, then a single tick for Sent and a double tick for Delivered.
 * Failed status is NOT shown here — it keeps its louder line below the bubble.
 */
@Composable
private fun BubbleMetaRow(
    msg: MessageEntity,
    isOut: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            BUBBLE_TIME_FMT.format(Date(msg.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        if (isOut) {
            when (msg.sendStatus) {
                "DELIVERED" -> {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.DoneAll, contentDescription = "Delivered",
                        tint = color, modifier = Modifier.size(14.dp),
                    )
                }
                "SENT" -> {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.Done, contentDescription = "Sent",
                        tint = color, modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

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

/** Compact 32dp text action under filtered bubbles (plan §2). */
@Composable
private fun CompactAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 32.dp, max = 32.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Small OG-scrape card under an Inbox bubble (Phase 4 item 9, opt-in). */
@Composable
private fun LinkPreviewCard(url: String) {
    val context = LocalContext.current
    val preview by androidx.compose.runtime.produceState<LinkPreviewParser.Preview?>(null, url) {
        value = LinkPreview.fetch(url)
    }
    val p = preview ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(top = 2.dp)
            .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.76f).dp)
            .clickable {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(p.url),
                        )
                    )
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.imageUrl != null) {
                AsyncImage(
                    model = p.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    p.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    p.siteName ?: runCatching { java.net.URL(p.url).host }.getOrDefault(p.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val INFO_FMT = SimpleDateFormat("EEE, d MMM yyyy · h:mm:ss a", Locale.US)

/**
 * Carrier spam reporting (Phase 4 item 17). India (SIM country "in"): the
 * official TRAI path — SMS to 1909 as `<text>, <sender>, dd/mm/yy`; older
 * than 3 days becomes a "report" rather than an actionable complaint, said
 * plainly. Everywhere (incl. India as a second option): GSMA 7726. The exact
 * outgoing text is shown before anything is sent; standard SMS rates note.
 */
@Composable
private fun CarrierReportDialog(msg: MessageEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val india = remember { com.messages.app.report.CarrierReport.isIndia(context) }
    val withinWindow = remember {
        com.messages.app.report.CarrierReportFormat.withinTraiComplaintWindow(
            msg.timestamp, System.currentTimeMillis(),
        )
    }
    // null = choosing a path; otherwise the picked (shortCode, outgoing text).
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun send(shortCode: String, text: String) {
        val ok = com.messages.app.report.CarrierReport.send(context, shortCode, text, msg.subId)
        android.widget.Toast.makeText(
            context,
            if (ok) "Report sent to $shortCode" else "Couldn't send the report",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        onDismiss()
    }

    val current = picked
    if (current == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Report to carrier") },
            text = {
                Column {
                    Text(
                        "Reporting forwards this message to your carrier's spam " +
                            "service by SMS. Standard SMS rates may apply.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (india) {
                        TextButton(onClick = {
                            picked = com.messages.app.report.CarrierReportFormat.TRAI_SHORT_CODE to
                                com.messages.app.report.CarrierReportFormat.traiComplaint(
                                    msg.body, msg.address, msg.timestamp,
                                )
                        }) { Text("Report to 1909 (TRAI DND)") }
                        if (!withinWindow) {
                            Text(
                                "This message is older than 3 days, so TRAI treats it " +
                                    "as an intelligence report rather than an actionable complaint.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = {
                        picked = com.messages.app.report.CarrierReportFormat.GSMA_SHORT_CODE to
                            com.messages.app.report.CarrierReportFormat.gsmaReport(msg.body)
                    }) { Text("Forward to 7726 (SPAM)") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send to ${current.first}?") },
            text = {
                Column {
                    Text(
                        "This exact message will be sent:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            current.second,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { send(current.first, current.second) }) { Text("Send report") }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Back") } },
        )
    }
}

/** Per-message info sheet (Phase 4 item 7): timestamps, SIM, type, status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInfoSheet(
    msg: MessageEntity,
    simName: String,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Message info", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            InfoRow("Type", buildString {
                append(if (msg.mmsId != null) "MMS" else "SMS")
                append(if (msg.isOutgoing) " · Sent" else " · Received")
            })
            InfoRow(
                if (msg.isOutgoing) "Sent" else "Received",
                INFO_FMT.format(Date(msg.timestamp)),
            )
            if (msg.isOutgoing && msg.sendStatus != "NONE") {
                InfoRow("Status", msg.sendStatus.lowercase().replaceFirstChar { it.uppercase() })
            }
            if (msg.isOutgoing && msg.sendStatus == "FAILED") {
                // Raw code included for debugging (null for legacy/MMS rows).
                InfoRow("Reason", com.messages.core.send.SendFailure.detailFor(msg.sendResultCode))
            }
            InfoRow(if (msg.isOutgoing) "To" else "From", msg.address)
            if (msg.subId != null || msg.isOutgoing) InfoRow("SIM", simName)
            InfoRow("Folder", msg.category.lowercase().replaceFirstChar { it.uppercase() })
            if (msg.protectedLabel.isNotBlank() && msg.protectedLabel != "NONE") {
                InfoRow("Label", msg.protectedLabel)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
