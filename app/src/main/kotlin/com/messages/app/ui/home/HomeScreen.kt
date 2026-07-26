package com.messages.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.app.ui.common.sharedThreadAvatar
import com.messages.app.ui.search.SearchHighlight
import com.messages.core.db.ConversationEntity
import com.messages.designsystem.Haptics
import com.messages.designsystem.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Phase 5 §1 row grid (REFS §1): 54dp avatar + 16dp gap ≈ 76dp rows.
private val ROW_AVATAR = 54.dp
private val ROW_GAP = 16.dp
// Divider prototype toggle (plan §1): true = inset dividers starting at the
// text column; false = divider-free Telegram-style spacing.
private const val INSET_DIVIDERS = false

private val FOLDERS = listOf(
    "INBOX" to "Inbox",
    "TRANSACTIONS" to "Transactions",
    "PROMOTIONS" to "Promotions",
    "SPAM" to "Spam",
    "REVIEW" to "Review",
    "BLOCKED" to "Blocked",
)

private val SEARCH_LABELS = listOf(
    "OTP" to "OTP",
    "BANK" to "Bank",
    "DELIVERY" to "Delivery",
    "TRAVEL" to "Travel",
    "BILL" to "Bill",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDefaultSmsApp: Boolean,
    initialFolder: String?,
    onRequestDefault: () -> Unit,
    onOpenThread: (Long) -> Unit,
    /** Open a chat at a specific matched message with terms highlighted (§8.5.3). */
    onOpenSearchResult: (threadId: Long, messageId: Long, terms: List<String>) -> Unit,
    onCompose: () -> Unit,
    onSettings: () -> Unit,
    onDashboard: () -> Unit,
    onOpenStarred: () -> Unit = {},
    onOpenArchived: () -> Unit = {},
    /** Secret space: fired by the 3s press-and-hold on the "Messages" title. */
    onSecretEntry: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    LaunchedEffect(initialFolder) { if (initialFolder != null) vm.setFolder(initialFolder) }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val folder by vm.folder.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val typing by vm.typing.collectAsState()
    val chips by vm.chips.collectAsState()
    val labelFilter by vm.labelFilter.collectAsState()
    val searchState by vm.searchState.collectAsState()
    val selectedThreads by vm.selectedThreads.collectAsState()
    val selectionActive = selectedThreads.isNotEmpty()
    var showHomeMenu by remember { mutableStateOf(false) }

    // §9: large-title collapsing app bar; its collapse fraction also drives
    // the FAB shrinking to icon-only as the list scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fabExpanded by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction < 0.5f }
    }

    fun exitSearch() {
        searchActive = false
        vm.clearSearch()
    }
    BackHandler(enabled = searchActive) { exitSearch() }
    BackHandler(enabled = selectionActive) { vm.clearSelection() }

    // Swipe actions (§8.2) with undo snackbars for the destructive ones.
    fun onSwipeAction(action: String, conv: ConversationEntity) {
        Haptics.tick(view)
        when (action) {
            SwipeActions.ARCHIVE -> {
                vm.archive(conv.threadId)
                scope.launch {
                    val r = snackbarHostState.showSnackbar(
                        "Archived", actionLabel = "Undo", withDismissAction = true,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.unarchive(conv.threadId)
                }
            }
            SwipeActions.DELETE -> {
                val at = System.currentTimeMillis()
                vm.trashThread(conv.threadId)
                scope.launch {
                    val r = snackbarHostState.showSnackbar(
                        "Conversation moved to Trash", actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        vm.undoTrashThread(conv.threadId, at - 1_000)
                    }
                }
            }
            SwipeActions.PIN -> vm.togglePin(conv.threadId, !conv.pinned)
            // Read swipe is a toggle (Phase 4 item 13): unread → read,
            // read → marked unread (Google Messages behavior).
            SwipeActions.READ ->
                if (conv.unreadCount > 0) vm.markThreadRead(conv.threadId)
                else vm.markThreadUnread(conv.threadId)
            SwipeActions.MUTE -> vm.toggleMute(conv.threadId, !conv.muted)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Multi-select contextual bar (Phase 4 item 14).
            if (selectionActive) {
                TopAppBar(
                    title = { Text("${selectedThreads.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.markThreadsRead(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.Drafts, contentDescription = "Mark read")
                        }
                        IconButton(onClick = {
                            vm.markThreadsUnread(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.MarkEmailUnread, contentDescription = "Mark unread")
                        }
                        IconButton(onClick = {
                            vm.archiveThreads(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = {
                            val ids = selectedThreads
                            val at = vm.trashThreads(ids)
                            vm.clearSelection()
                            scope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    "${ids.size} conversation${if (ids.size == 1) "" else "s"} moved to Trash",
                                    actionLabel = "Undo", withDismissAction = true,
                                )
                                if (r == SnackbarResult.ActionPerformed) {
                                    vm.undoTrashThreads(ids, at - 1_000)
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    },
                )
            } else {
            AnimatedVisibility(
                visible = !searchActive,
                enter = expandVertically(Motion.spatialDefault()) + fadeIn(Motion.effectsDefault()),
                exit = shrinkVertically(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
            ) {
                LargeTopAppBar(
                    // Secret space entry: press and hold the title for 1.5s —
                    // standard long-press feel, still deliberate. pointerInput
                    // + a timed press (not combinedClickable — its long-press
                    // fires at the system ~400ms timeout). No visual
                    // affordance: nothing hints the space exists.
                    title = {
                        val haptics = androidx.compose.ui.hapticfeedback.HapticFeedbackType
                        val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Text(
                            "Messages",
                            modifier = Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    val held = try {
                                        withTimeout(1_500) {
                                            waitForUpOrCancellation()
                                            false // released/cancelled before 1.5s
                                        }
                                    } catch (_: PointerEventTimeoutCancellationException) {
                                        true // still down at 1.5s
                                    }
                                    if (held) {
                                        hapticFeedback.performHapticFeedback(haptics.LongPress)
                                        onSecretEntry()
                                    }
                                }
                            },
                        )
                    },
                    actions = {
                        IconButton(onClick = onDashboard) {
                            Icon(Icons.Filled.Shield, contentDescription = "Protection dashboard")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        Box {
                            IconButton(onClick = { showHomeMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showHomeMenu,
                                onDismissRequest = { showHomeMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Archived") },
                                    onClick = { showHomeMenu = false; onOpenArchived() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Starred messages") },
                                    onClick = { showHomeMenu = false; onOpenStarred() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Mark all as read") },
                                    onClick = {
                                        showHomeMenu = false
                                        vm.markFolderRead(folder)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Folder marked as read")
                                        }
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
            }
        },
        floatingActionButton = {
            // §8.4: no messaging features until the default-SMS role is granted.
            if (!searchActive && isDefaultSmsApp) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("New message") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // §8.4 gate (Google Messages behavior): if the role was denied, the
            // conversation area is an empty state with a single card that
            // re-triggers the role request. No list, no search, no composer.
            if (!isDefaultSmsApp) {
                DefaultSmsGate(onRequestDefault)
                return@Column
            }

            // Search bar — incremental, chip-based (§8.5)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = searchActive,
                    enter = fadeIn(Motion.effectsDefault()),
                    exit = fadeOut(Motion.effectsFast()),
                ) {
                    IconButton(onClick = { exitSearch() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                }
                TextField(
                    value = typing,
                    onValueChange = { vm.setTyping(it); searchActive = true },
                    placeholder = {
                        Text(
                            if (chips.isEmpty()) "Search messages" else "Add another keyword",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        if (!searchActive) Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchActive && (typing.isNotEmpty() || chips.isNotEmpty())) {
                            IconButton(onClick = {
                                if (typing.isNotEmpty()) vm.setTyping("") else vm.clearSearch()
                            }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.commitTyping() }),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        // Refs' pill height (~52dp vs the M3 56dp default); the
                        // single-line field centers fine at this height.
                        .height(52.dp)
                        .onFocusChanged { if (it.isFocused) searchActive = true },
                )
            }

            // Contacts access banner: without READ_CONTACTS every thread shows
            // raw numbers. Dismissible; graceful denial (re-triggerable, falls
            // back to app settings when permanently denied).
            if (!searchActive) ContactsPermissionBanner()

            AnimatedContent(
                targetState = searchActive,
                transitionSpec = {
                    fadeIn(Motion.effectsDefault()) togetherWith fadeOut(Motion.effectsFast())
                },
                label = "search-mode",
            ) { inSearch ->
                if (inSearch) {
                    Column(Modifier.fillMaxSize()) {
                        SearchPane(
                            vm = vm,
                            chips = chips,
                            typing = typing,
                            labelFilter = labelFilter,
                            state = searchState,
                            onOpenResult = { msg ->
                                vm.recordSearchUse()
                                onOpenSearchResult(msg.threadId, msg.id, searchState.activeKeywords)
                            },
                            onOpenThread = onOpenThread,
                        )
                    }
                } else {
                    FolderPane(
                        vm = vm,
                        folder = folder,
                        onSelectFolder = { key ->
                            if (key != folder) Haptics.tick(view)
                            vm.setFolder(key)
                        },
                        onOpenThread = onOpenThread,
                        onSwipeAction = ::onSwipeAction,
                    )
                }
            }
        }
    }
}

/** Folder chips + the conversation list, with a directional animated switch (§9). */
@Composable
private fun FolderPane(
    vm: HomeViewModel,
    folder: String,
    onSelectFolder: (String) -> Unit,
    onOpenThread: (Long) -> Unit,
    onSwipeAction: (String, ConversationEntity) -> Unit,
) {
    val rightAction by SwipeActions.right.collectAsState()
    val leftAction by SwipeActions.left.collectAsState()
    val drafts by com.messages.app.ui.common.DraftStore.drafts.collectAsState()
    val unreadOnly by vm.unreadOnly.collectAsState()
    val selectedThreads by vm.selectedThreads.collectAsState()
    val selectionActive = selectedThreads.isNotEmpty()
    val rowView = LocalView.current
    // Badge-tap explanation sheet (Phase 2).
    var badgeSheet by remember {
        mutableStateOf<com.messages.protection.SenderBadges.Badge?>(null)
    }
    Column(Modifier.fillMaxSize()) {
        // Folder chips directly under the search bar (§9)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Unread-only filter (Phase 4 item 12) — orthogonal to folders.
            item {
                FilterChip(
                    selected = unreadOnly,
                    onClick = { vm.setUnreadOnly(!unreadOnly) },
                    label = { Text("Unread") },
                    leadingIcon = if (unreadOnly) {
                        {
                            Icon(
                                Icons.Filled.Close, contentDescription = "Clear unread filter",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                )
            }
            items(FOLDERS) { (key, label) ->
                val unread by vm.folderUnread(key).collectAsState(initial = 0)
                FilterChip(
                    selected = folder == key,
                    onClick = { onSelectFolder(key) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label)
                            if (unread > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge {
                                    Text(
                                        "$unread",
                                        modifier = Modifier.semantics {
                                            contentDescription = "$unread unread"
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        // Animated folder switch: content slides toward the tapped direction
        // on expressive spatial springs; fades stay critically damped.
        AnimatedContent(
            targetState = folder,
            transitionSpec = {
                val from = FOLDERS.indexOfFirst { it.first == initialState }
                val to = FOLDERS.indexOfFirst { it.first == targetState }
                val dir = if (to >= from) 1 else -1
                (slideInHorizontally(Motion.spatialDefault()) { it / 4 * dir } +
                    fadeIn(Motion.effectsDefault())) togetherWith
                    (slideOutHorizontally(Motion.spatialDefault()) { -it / 4 * dir } +
                        fadeOut(Motion.effectsFast()))
            },
            label = "folder-switch",
        ) { targetFolder ->
            // Unread-only filter (Phase 4 item 12): a dedicated DAO flow with
            // the shared unreadCount>0 predicate — folder-scoped, drops a
            // conversation live when it's read, includes mark-as-unread.
            val conversations by remember(targetFolder, unreadOnly) {
                if (unreadOnly) vm.unreadConversationsFor(targetFolder)
                else vm.conversationsFor(targetFolder)
            }.collectAsState()
            // Verified-sender badges (Phase 2): latest incoming message's
            // fraud/protected state per thread; eligibility is decided by the
            // engine's SenderBadges, never re-detected in the UI.
            val badgeMeta by vm.latestIncomingMeta.collectAsState()
            when {
                conversations == null -> Box(Modifier.fillMaxSize()) // first load, no flash
                conversations.orEmpty().isEmpty() ->
                    if (unreadOnly) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No unread conversations",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else EmptyFolderState(targetFolder)
                else -> {
                    val listState = rememberLazyListState()
                    val listScope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            conversations.orEmpty(),
                            key = { _, it -> it.threadId },
                        ) { index, conv ->
                            val meta = badgeMeta[conv.threadId]
                            val badge = remember(conv.address, conv.contactName, meta) {
                                com.messages.protection.SenderBadges.badgeFor(
                                    address = conv.address,
                                    isContact = conv.contactName != null,
                                    dangerous = meta?.dangerous == true || meta?.fraudWarning == true,
                                    protectedLabel = meta?.protectedLabel,
                                )
                            }
                            SwipeableConversationRow(
                                conv = conv,
                                draft = drafts[conv.threadId],
                                rightAction = rightAction,
                                leftAction = leftAction,
                                onAction = onSwipeAction,
                                onClick = {
                                    if (selectionActive) vm.toggleSelected(conv.threadId)
                                    else onOpenThread(conv.threadId)
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = Motion.effectsDefault(),
                                    placementSpec = Motion.spatialDefault(),
                                    fadeOutSpec = Motion.effectsFast(),
                                ),
                                badge = badge,
                                onBadgeTap = { badgeSheet = it },
                                selectionActive = selectionActive,
                                selected = conv.threadId in selectedThreads,
                                onLongClick = {
                                    Haptics.longPress(rowView)
                                    vm.toggleSelected(conv.threadId)
                                },
                            )
                            // Inset divider starting at the text column (plan
                            // §1 prototype) — never after the last row.
                            if (INSET_DIVIDERS &&
                                index < conversations.orEmpty().lastIndex
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .padding(start = 16.dp + ROW_AVATAR + ROW_GAP),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                    // Jump back to the newest conversations after scrolling
                    // deep into the list (Google Messages affordance).
                    // Bottom-start so it never collides with the compose FAB.
                    val showJumpTop by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 6 }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showJumpTop,
                        enter = scaleIn(Motion.spatialFast()) + fadeIn(Motion.effectsDefault()),
                        exit = scaleOut(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                listScope.launch { listState.animateScrollToItem(0) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Back to top",
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    badgeSheet?.let { b ->
        com.messages.app.ui.common.SenderBadgeSheet(b, onDismiss = { badgeSheet = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPane(
    vm: HomeViewModel,
    chips: List<String>,
    typing: String,
    labelFilter: String?,
    state: HomeViewModel.SearchState,
    onOpenResult: (com.messages.core.db.MessageEntity) -> Unit,
    onOpenThread: (Long) -> Unit,
) {
    // Committed keyword chips — unlimited, each removable (§8.5.2).
    if (chips.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                InputChip(
                    selected = true,
                    onClick = { vm.removeChip(chip) },
                    label = { Text(chip) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Remove $chip",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }

    // Label filter chips (OTP / Bank / Delivery / Travel / Bill) + suggestions.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        items(SEARCH_LABELS) { (key, label) ->
            FilterChip(
                selected = labelFilter == key,
                onClick = { vm.setLabelFilter(if (labelFilter == key) null else key) },
                label = { Text(label) },
            )
        }
    }

    // Suggested chips extracted from the current result set (§8.5.2).
    if (state.suggestedChips.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        ) {
            items(state.suggestedChips) { suggestion ->
                AssistChip(
                    onClick = { vm.addChip(suggestion) },
                    label = { Text(suggestion) },
                )
            }
        }
    }

    val hasQuery = state.activeKeywords.isNotEmpty()
    if (!hasQuery) {
        // Below the 3-char threshold: recent searches instead of results (§8.5.1).
        val saved = remember { vm.savedSearches() }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (typing.isNotEmpty()) {
                Text(
                    "Type at least 3 characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (saved.isNotEmpty()) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    saved.forEach { combo ->
                        AssistChip(
                            onClick = { vm.applySavedSearch(combo) },
                            label = { Text(combo.joinToString("  ")) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.History, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
        return
    }

    if (state.results.isEmpty() && state.conversationMatches.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No messages match",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Results: ranked by match count then recency (done in the engine layer);
    // Spam & Blocked surface under a separator (§6.2).
    val (filtered, normal) = state.results.partition {
        it.message.category == "SPAM" || it.message.category == "BLOCKED"
    }
    LazyColumn(Modifier.fillMaxSize()) {
        // §8.5.3: conversations whose contact name / number matches ("mom").
        if (state.conversationMatches.isNotEmpty()) {
            item {
                SearchSectionHeader("Conversations")
            }
            items(state.conversationMatches, key = { "conv-${it.threadId}" }) { conv ->
                ConversationMatchRow(conv, state.activeKeywords) { onOpenThread(conv.threadId) }
            }
            if (state.results.isNotEmpty()) {
                item { SearchSectionHeader("Messages") }
            }
        }
        items(normal, key = { it.message.id }) { row ->
            SearchResultRow(row, state.activeKeywords, onOpenResult)
        }
        if (filtered.isNotEmpty()) {
            item { SearchSectionHeader("In Spam & Blocked") }
            items(filtered, key = { it.message.id }) { row ->
                SearchResultRow(row, state.activeKeywords, onOpenResult)
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** A conversation whose contact name / number matched a keyword (§8.5.3). */
@Composable
private fun ConversationMatchRow(
    conv: ConversationEntity,
    keywords: List<String>,
    onClick: () -> Unit,
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.primaryContainer,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            conv.contactName ?: conv.address, conv.category,
            photoUri = com.messages.app.ui.common.rememberContactPhoto(conv.address),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                SearchHighlight.annotate(conv.contactName ?: conv.address, keywords, highlight),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conv.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    row: HomeViewModel.SearchRowUi,
    keywords: List<String>,
    onOpen: (com.messages.core.db.MessageEntity) -> Unit,
) {
    val msg = row.message
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.primaryContainer,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(msg) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            row.displayName ?: msg.address, msg.category,
            photoUri = com.messages.app.ui.common.rememberContactPhoto(msg.address),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SearchHighlight.annotate(row.displayName ?: msg.address, keywords, highlight),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTime(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Snippet windowed around the first match, all terms highlighted (§8.5.3).
            Text(
                SearchHighlight.annotate(
                    SearchHighlight.snippet(msg.body, keywords),
                    keywords, highlight,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (keywords.size > 1) {
                Text(
                    "Matches ${row.matchCount} of ${keywords.size} keywords",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Contextual READ_CONTACTS request: a dismissible "Show contact names" card.
 * Grant → contact names refresh immediately; denial keeps the card (tap
 * again re-requests); permanent denial routes to the app-settings page.
 */
@Composable
private fun ContactsPermissionBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var dismissed by remember {
        mutableStateOf(prefs.getBoolean("contacts_banner_dismissed", false))
    }
    if (granted || dismissed) return
    val activity = context as? android.app.Activity
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) {
            com.messages.core.contacts.ContactSync.ensureObserver(context)
            com.messages.core.contacts.ContactSync.refreshOnForeground(context)
        } else if (activity != null &&
            !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS)
        ) {
            // "Don't ask again" — the system dialog will never show; take the
            // user to the app-settings page instead.
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null),
                    )
                )
            }
        }
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show contact names", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Allow Contacts access so people show up by name and photo, not number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) },
                ) { Text("Allow") }
            }
            IconButton(onClick = {
                dismissed = true
                prefs.edit().putBoolean("contacts_banner_dismissed", true).apply()
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * §8.4: the viewer-shell empty state shown while the default-SMS role is not
 * held — mirrors Google Messages' "Set as default" screen. The single card
 * re-triggers the RoleManager request; no popup nagging.
 */
@Composable
private fun DefaultSmsGate(onRequestDefault: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shield, contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Set Messages as your default SMS app",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "To see your conversations and turn on spam, scam & fraud " +
                    "protection, Messages needs to be your SMS app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRequestDefault) {
                Text("Set as default SMS app")
            }
        }
    }
}

/**
 * Swipe-action wrapper (§8.2): start→end runs [rightAction], end→start runs
 * [leftAction]. The row always snaps back — rows that leave the list do so via
 * the data update + animateItem, so non-removing actions (pin, read, mute)
 * don't strand a dismissed row.
 *
 * Hand-rolled instead of M3's SwipeToDismissBox (Phase 6): the box's anchored-
 * draggable machinery costs real composition time PER ROW while flinging the
 * list (~12ms/frame on-device across a compose burst), and our rows never
 * actually dismiss (the old confirmValueChange always returned false). Here
 * the drag offset is an Animatable read only inside graphicsLayer (draw phase
 * — zero recomposition while dragging) and the colored action background is
 * composed only while a drag is engaged (no permanent extra layer/overdraw).
 */
@Composable
private fun SwipeableConversationRow(
    conv: ConversationEntity,
    draft: String?,
    rightAction: String,
    leftAction: String,
    onAction: (String, ConversationEntity) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: com.messages.protection.SenderBadges.Badge? = null,
    onBadgeTap: (com.messages.protection.SenderBadges.Badge) -> Unit = {},
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val rightEnabled = rightAction != SwipeActions.NONE
    val leftEnabled = leftAction != SwipeActions.NONE
    // In selection mode swipes are disabled — taps toggle, long-press extends.
    // Same for the no-actions-configured case: plain row, no gesture handler.
    if (selectionActive || (!rightEnabled && !leftEnabled)) {
        Box(modifier.background(MaterialTheme.colorScheme.surface)) {
            ConversationRow(
                conv = conv, draft = draft, onClick = onClick,
                badge = badge, onBadgeTap = onBadgeTap,
                selected = selected, onLongClick = onLongClick,
            )
        }
        return
    }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // -1 = swiping toward start (left action), 1 = toward end (right action),
    // 0 = at rest. Only crossing zero recomposes — not every drag frame.
    val engaged by remember {
        derivedStateOf {
            when {
                offsetX.value > 0f -> 1
                offsetX.value < 0f -> -1
                else -> 0
            }
        }
    }
    Box(modifier) {
        if (engaged != 0) {
            SwipeActionBackground(
                action = if (engaged > 0) rightAction else leftAction,
                fromStart = engaged > 0,
            )
        }
        Box(
            Modifier
                // Opaque only while sliding (it must cover the action backdrop);
                // at rest the row draws straight on the window surface — one
                // less full-row overdraw layer while scrolling.
                .then(
                    if (engaged != 0) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else Modifier
                )
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(rightEnabled, leftEnabled) {
                    val threshold = 0.4f // fraction of row width, as before
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = (offsetX.value + dragAmount).coerceIn(
                                if (leftEnabled) -size.width.toFloat() else 0f,
                                if (rightEnabled) size.width.toFloat() else 0f,
                            )
                            scope.launch { offsetX.snapTo(target) }
                        },
                        onDragEnd = {
                            val past = size.width * threshold
                            when {
                                offsetX.value > past -> onAction(rightAction, conv)
                                offsetX.value < -past -> onAction(leftAction, conv)
                            }
                            scope.launch { offsetX.animateTo(0f, Motion.spatialFast()) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, Motion.spatialFast()) }
                        },
                    )
                },
        ) {
            ConversationRow(
                conv = conv, draft = draft, onClick = onClick,
                badge = badge, onBadgeTap = onBadgeTap,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun BoxScope.SwipeActionBackground(
    action: String,
    fromStart: Boolean,
) {
    val (icon, container, tint) = when (action) {
        SwipeActions.ARCHIVE -> Triple(
            Icons.Outlined.Archive,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        SwipeActions.DELETE -> Triple(
            Icons.Outlined.Delete,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SwipeActions.PIN -> Triple(
            Icons.Outlined.PushPin,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SwipeActions.READ -> Triple(
            Icons.Outlined.MarkChatRead,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        SwipeActions.MUTE -> Triple(
            Icons.Outlined.NotificationsOff,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface,
        )
        else -> return
    }
    Row(
        Modifier
            .matchParentSize()
            .background(container)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
    ) {
        Icon(icon, contentDescription = SwipeActions.label(action), tint = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: ConversationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    draft: String? = null,
    badge: com.messages.protection.SenderBadges.Badge? = null,
    onBadgeTap: (com.messages.protection.SenderBadges.Badge) -> Unit = {},
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val unread = conv.unreadCount > 0
    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Select conversation" else null,
            )
            // 54dp avatar + 11dp vertical padding ≈ the refs' 74–76pt row.
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            conv.contactName ?: conv.address,
            conv.category,
            modifier = Modifier.sharedThreadAvatar(conv.threadId),
            size = ROW_AVATAR,
            // Locked chats keep their masked row anonymous — monogram only.
            photoUri = if (conv.locked) null
            else com.messages.app.ui.common.rememberContactPhoto(conv.address),
        )
        Spacer(Modifier.width(ROW_GAP))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        conv.contactName ?: conv.address,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill=false: short names keep the badge hugging them,
                        // long names still ellipsize before the badge.
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(4.dp))
                        com.messages.app.ui.common.SenderBadgeIcon(
                            badge,
                            onClick = { onBadgeTap(badge) },
                        )
                    }
                    if (conv.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(conv.lastTimestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread && !conv.muted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Draft preview (§8.1) outranks the last message, like Google
                // Messages — but never leaks content from locked chats.
                val showDraft = draft != null && !conv.locked
                Text(
                    when {
                        conv.locked -> "🔒 Locked conversation"
                        showDraft -> "Draft: $draft"
                        else -> conv.lastMessage
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        showDraft -> MaterialTheme.colorScheme.primary
                        unread -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Single trailing-indicator slot (plan §1): unread badge wins
                // over the pin glyph; never both. Muted chats keep their count
                // in a calm gray badge instead of the loud primary one.
                when {
                    unread -> {
                        Spacer(Modifier.width(8.dp))
                        if (conv.muted) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    "${conv.unreadCount}",
                                    modifier = Modifier.semantics {
                                        contentDescription = "${conv.unreadCount} unread, muted"
                                    },
                                )
                            }
                        } else {
                            Badge {
                                Text(
                                    "${conv.unreadCount}",
                                    modifier = Modifier.semantics {
                                        contentDescription = "${conv.unreadCount} unread"
                                    },
                                )
                            }
                        }
                    }
                    conv.pinned -> {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.PushPin, contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private data class EmptyStateSpec(
    val icon: ImageVector,
    val headline: String,
    val supporting: String,
)

private fun emptySpecFor(folder: String): EmptyStateSpec = when (folder) {
    "SPAM" -> EmptyStateSpec(
        Icons.Outlined.Shield,
        "No spam today — enjoy the silence",
        "Caught messages stay here, reviewable any time. Nothing is ever deleted.",
    )
    "PROMOTIONS" -> EmptyStateSpec(
        Icons.Outlined.LocalOffer,
        "No promotions right now",
        "Offers and marketing wait here without making a sound.",
    )
    "REVIEW" -> EmptyStateSpec(
        Icons.Outlined.RateReview,
        "Nothing needs your review",
        "When the engine isn't sure, it asks you here instead of guessing.",
    )
    "BLOCKED" -> EmptyStateSpec(
        Icons.Outlined.Block,
        "No blocked messages",
        "Messages from senders you block are kept here, silently.",
    )
    "TRANSACTIONS" -> EmptyStateSpec(
        Icons.Outlined.ReceiptLong,
        "No transactions yet",
        "Receipts, debits and statements are filed here automatically.",
    )
    else -> EmptyStateSpec(
        Icons.Outlined.Forum,
        "No messages yet",
        "Conversations you start or receive will appear here.",
    )
}

/** §9 delight: layered-shape illustration settling in on a gentle spring. */
@Composable
private fun EmptyFolderState(folder: String) {
    val spec = emptySpecFor(folder)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.85f,
        animationSpec = Motion.gentle(),
        label = "empty-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.effectsSlow(),
        label = "empty-alpha",
    )
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(120.dp)
                        .rotate(-10f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    Modifier
                        .size(104.dp)
                        .rotate(8f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
                Icon(
                    spec.icon, contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                spec.headline,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                spec.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Composition-only helpers (main thread): SimpleDateFormat is not thread-safe
// but is only ever touched from composition here. Allocating formatters per
// row per frame showed up in the Phase 6 fling profile — reuse instead.
private val rowTimeFormat = SimpleDateFormat("HH:mm", Locale.US)
private val rowDateFormat = SimpleDateFormat("dd MMM", Locale.US)
private val sharedDate = Date(0)

private fun localDayOf(ts: Long): Long =
    (ts + java.util.TimeZone.getDefault().getOffset(ts)) / 86_400_000L

private fun formatTime(ts: Long): String {
    if (ts == 0L) return ""
    val sameDay = localDayOf(ts) == localDayOf(System.currentTimeMillis())
    sharedDate.time = ts
    return if (sameDay) rowTimeFormat.format(sharedDate) else rowDateFormat.format(sharedDate)
}
