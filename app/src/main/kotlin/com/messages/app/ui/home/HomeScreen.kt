package com.messages.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
            SwipeActions.READ -> vm.markThreadRead(conv.threadId)
            SwipeActions.MUTE -> vm.toggleMute(conv.threadId, !conv.muted)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = !searchActive,
                enter = expandVertically(Motion.spatialDefault()) + fadeIn(Motion.effectsDefault()),
                exit = shrinkVertically(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
            ) {
                LargeTopAppBar(
                    title = { Text("Messages") },
                    actions = {
                        IconButton(onClick = onDashboard) {
                            Icon(Icons.Filled.Shield, contentDescription = "Protection dashboard")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
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
                        Text(if (chips.isEmpty()) "Search messages" else "Add another keyword")
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
                        .onFocusChanged { if (it.isFocused) searchActive = true },
                )
            }

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
    Column(Modifier.fillMaxSize()) {
        // Folder chips directly under the search bar (§9)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
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
            val conversations by remember(targetFolder) { vm.conversationsFor(targetFolder) }
                .collectAsState()
            when {
                conversations == null -> Box(Modifier.fillMaxSize()) // first load, no flash
                conversations.orEmpty().isEmpty() -> EmptyFolderState(targetFolder)
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(conversations.orEmpty(), key = { it.threadId }) { conv ->
                            SwipeableConversationRow(
                                conv = conv,
                                draft = drafts[conv.threadId],
                                rightAction = rightAction,
                                leftAction = leftAction,
                                onAction = onSwipeAction,
                                onClick = { onOpenThread(conv.threadId) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = Motion.effectsDefault(),
                                    placementSpec = Motion.spatialDefault(),
                                    fadeOutSpec = Motion.effectsFast(),
                                ),
                            )
                        }
                    }
                }
            }
        }
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
        ContactAvatar(conv.contactName ?: conv.address, conv.category)
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
        ContactAvatar(row.displayName ?: msg.address, msg.category)
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
 * [leftAction]. The row always snaps back (confirmValueChange returns false) —
 * rows that leave the list do so via the data update + animateItem, so
 * non-removing actions (pin, read, mute) don't strand a dismissed row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    conv: ConversationEntity,
    draft: String?,
    rightAction: String,
    leftAction: String,
    onAction: (String, ConversationEntity) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(rightAction, conv)
                SwipeToDismissBoxValue.EndToStart -> onAction(leftAction, conv)
                else -> {}
            }
            false
        },
        positionalThreshold = { total -> total * 0.4f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = rightAction != SwipeActions.NONE,
        enableDismissFromEndToStart = leftAction != SwipeActions.NONE,
        backgroundContent = { SwipeActionBackground(dismissState, rightAction, leftAction) },
        modifier = modifier,
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            ConversationRow(conv = conv, draft = draft, onClick = onClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionBackground(
    state: SwipeToDismissBoxState,
    rightAction: String,
    leftAction: String,
) {
    val direction = state.dismissDirection
    val action = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> rightAction
        SwipeToDismissBoxValue.EndToStart -> leftAction
        else -> return
    }
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
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Arrangement.Start
        } else {
            Arrangement.End
        },
    ) {
        Icon(icon, contentDescription = SwipeActions.label(action), tint = tint)
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    draft: String? = null,
) {
    val unread = conv.unreadCount > 0
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            conv.contactName ?: conv.address,
            conv.category,
            modifier = Modifier.sharedThreadAvatar(conv.threadId),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.contactName ?: conv.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conv.pinned) {
                    Icon(
                        Icons.Outlined.PushPin, contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(conv.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) MaterialTheme.colorScheme.primary
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
                if (unread) {
                    Spacer(Modifier.width(8.dp))
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

private fun formatTime(ts: Long): String {
    if (ts == 0L) return ""
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).let {
        it.format(Date(now)) == it.format(Date(ts))
    }
    return if (sameDay) SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
    else SimpleDateFormat("dd MMM", Locale.US).format(Date(ts))
}
