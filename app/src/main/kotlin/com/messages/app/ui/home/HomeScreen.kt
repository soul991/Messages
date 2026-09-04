package com.messages.app.ui.home

import androidx.activity.compose.BackHandler
import com.messages.app.MainActivity
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.GlassDockItem
import com.messages.designsystem.LiquidGlassBottomDock
import com.messages.designsystem.LiquidGlassSurface
import com.messages.designsystem.LocalDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.AppDateFormat
import com.messages.app.ui.common.ContactAvatar
import com.messages.app.ui.common.ListRender
import com.messages.app.ui.common.ListSkeleton
import com.messages.app.ui.common.LoadFailedState
import com.messages.app.ui.common.LoadState
import com.messages.app.ui.common.listRender
import com.messages.app.ui.common.rememberLoadingGrace
import com.messages.app.ui.common.sharedThreadAvatar
import com.messages.app.ui.common.valueOrNull
import com.messages.app.ui.search.SearchHighlight
import com.messages.core.db.ConversationEntity
import com.messages.designsystem.Haptics
import com.messages.designsystem.Motion
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.messages.app.R

// Phase 5 §1 row grid (REFS §1): 54dp avatar + 16dp gap ≈ 76dp rows.
private val ROW_AVATAR = 54.dp
private val ROW_GAP = 16.dp
// Divider prototype toggle (plan §1): true = inset dividers starting at the
// text column; false = divider-free Telegram-style spacing.
private const val INSET_DIVIDERS = false

// V2-36. The left half of each pair is the stored folder/label id and never
// changes; the right half is a resource, because the tab is the first thing a
// user reads and "Inbox" is not a name they typed.
private val FOLDERS = listOf(
    "INBOX" to R.string.category_inbox,
    "TRANSACTIONS" to R.string.category_transactions,
    "PROMOTIONS" to R.string.category_promotions,
    "SPAM" to R.string.category_spam,
    "REVIEW" to R.string.category_review,
    "BLOCKED" to R.string.home_folder_blocked,
)

private val SEARCH_LABELS = listOf(
    "OTP" to R.string.home_label_otp,
    "BANK" to R.string.home_label_bank,
    "DELIVERY" to R.string.home_label_delivery,
    "TRAVEL" to R.string.home_label_travel,
    "BILL" to R.string.home_label_bill,
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
    onOpenOutbox: () -> Unit = {},
    /** Secret space: fired by the 3s press-and-hold on the "Messages" title. */
    onSecretEntry: () -> Unit = {},
    roleRequestFailed: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    onOpenDefaultApps: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    LaunchedEffect(initialFolder) { if (initialFolder != null) vm.setFolder(initialFolder) }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // V2-36. Snackbars are raised from onSwipeAction and from onClick lambdas —
    // neither is a composable scope — so their copy is resolved here.
    val undoLabel = stringResource(R.string.action_undo)
    val archivedMessage = stringResource(R.string.home_archived_snackbar)
    val trashedMessage = stringResource(R.string.home_trashed_snackbar)
    val markedReadMessage = stringResource(R.string.home_folder_marked_read)
    // Counted, so it is a plural rather than an "s" glued on — and the count is
    // only known inside the click handler, hence a resolver instead of a value.
    val resources = LocalContext.current.resources
    val trashedCount = { n: Int ->
        resources.getQuantityString(R.plurals.home_trashed_count_snackbar, n, n)
    }
    val folder by vm.folder.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    val typing by vm.typing.collectAsStateWithLifecycle()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val labelFilter by vm.labelFilter.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val selectedThreads by vm.selectedThreads.collectAsStateWithLifecycle()
    val outboxWaiting by vm.outboxCount.collectAsStateWithLifecycle()
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
                        archivedMessage, actionLabel = undoLabel, withDismissAction = true,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.unarchive(conv.threadId)
                }
            }
            SwipeActions.DELETE -> {
                val at = System.currentTimeMillis()
                vm.trashThread(conv.threadId)
                scope.launch {
                    val r = snackbarHostState.showSnackbar(
                        trashedMessage, actionLabel = undoLabel,
                        withDismissAction = true,
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        vm.undoTrashThread(conv.threadId)
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
                    title = {
                        Text(pluralStringResource(
                            R.plurals.home_selected_count,
                            selectedThreads.size, selectedThreads.size,
                        ))
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.markThreadsRead(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.Drafts, contentDescription = stringResource(R.string.home_mark_read))
                        }
                        IconButton(onClick = {
                            vm.markThreadsUnread(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.MarkEmailUnread, contentDescription = stringResource(R.string.home_mark_unread))
                        }
                        IconButton(onClick = {
                            vm.archiveThreads(selectedThreads)
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.home_archive))
                        }
                        IconButton(onClick = {
                            val ids = selectedThreads
                            val at = vm.trashThreads(ids)
                            vm.clearSelection()
                            scope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    trashedCount(ids.size),
                                    actionLabel = undoLabel, withDismissAction = true,
                                )
                                if (r == SnackbarResult.ActionPerformed) {
                                    vm.undoTrashThreads(ids)
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
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
                        // V2-39: the timed press is pointer input only, so it
                        // does not exist for TalkBack, Switch Access, a D-pad or
                        // a keyboard. When the user has opted in, the same
                        // entry is published as a labelled semantic action and
                        // a long-click action — additions to the node, never a
                        // change to the gesture, and never a way past the
                        // credential prompt underneath.
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val accessibleEntry = remember {
                            com.messages.app.ui.secret.SecretEntryAccess.enabled(context)
                        }
                        val entryLabel = stringResource(
                            com.messages.app.ui.secret.SecretEntryAccess.ACTION_LABEL,
                        )
                        Text(
                            stringResource(R.string.app_name),
                            modifier = Modifier
                                .pointerInput(Unit) {
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
                                }
                                .then(
                                    if (!accessibleEntry) Modifier else Modifier.semantics {
                                        onLongClick(label = entryLabel) {
                                            onSecretEntry(); true
                                        }
                                        customActions = listOf(
                                            CustomAccessibilityAction(entryLabel) {
                                                onSecretEntry(); true
                                            },
                                        )
                                    },
                                ),
                        )
                    },
                    actions = {
                        IconButton(onClick = onDashboard) {
                            Icon(Icons.Filled.Shield, contentDescription = stringResource(R.string.home_protection_dashboard))
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                        Box {
                            IconButton(onClick = { showHomeMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                            }
                            DropdownMenu(
                                expanded = showHomeMenu,
                                onDismissRequest = { showHomeMenu = false },
                            ) {
                                // V2-48: the count is in the label rather than
                                // in a dot, because "3 waiting" is the part
                                // that decides whether it is worth opening.
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (outboxWaiting > 0) {
                                                stringResource(R.string.outbox_title_count, outboxWaiting)
                                            } else {
                                                stringResource(R.string.outbox_title)
                                            }
                                        )
                                    },
                                    onClick = { showHomeMenu = false; onOpenOutbox() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archived_title)) },
                                    onClick = { showHomeMenu = false; onOpenArchived() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_starred_messages)) },
                                    onClick = { showHomeMenu = false; onOpenStarred() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_mark_all_as_read)) },
                                    onClick = {
                                        showHomeMenu = false
                                        vm.markFolderRead(folder)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(markedReadMessage)
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
                    text = { Text(stringResource(R.string.home_new_message)) },
                )
            }
        },
        bottomBar = {
            if (!searchActive && !selectionActive && isDefaultSmsApp) {
                val inboxUnread by vm.folderUnread("INBOX").collectAsStateWithLifecycle(0)
                val txnUnread by vm.folderUnread("TRANSACTIONS").collectAsStateWithLifecycle(0)
                val promoUnread by vm.folderUnread("PROMOTIONS").collectAsStateWithLifecycle(0)
                val reviewUnread by vm.folderUnread("REVIEW").collectAsStateWithLifecycle(0)
                val spamUnread by vm.folderUnread("SPAM").collectAsStateWithLifecycle(0)

                val dockItems = remember(inboxUnread, txnUnread, promoUnread, reviewUnread, spamUnread) {
                    listOf(
                        GlassDockItem(
                            key = "INBOX",
                            title = "Personal",
                            icon = Icons.Outlined.Forum,
                            unreadCount = inboxUnread,
                        ),
                        GlassDockItem(
                            key = "TRANSACTIONS",
                            title = "Bank",
                            icon = Icons.Outlined.ReceiptLong,
                            unreadCount = txnUnread,
                            accentColor = Color(0xFF16A34A),
                        ),
                        GlassDockItem(
                            key = "PROMOTIONS",
                            title = "Offers",
                            icon = Icons.Outlined.LocalOffer,
                            unreadCount = promoUnread,
                            accentColor = Color(0xFFD97706),
                        ),
                        GlassDockItem(
                            key = "REVIEW",
                            title = "Review",
                            icon = Icons.Outlined.RateReview,
                            unreadCount = reviewUnread,
                            accentColor = Color(0xFF2563EB),
                        ),
                        GlassDockItem(
                            key = "SPAM",
                            title = "Spam",
                            icon = Icons.Outlined.Shield,
                            unreadCount = spamUnread,
                            accentColor = Color(0xFFDC2626),
                        ),
                    )
                }

                LiquidGlassBottomDock(
                    items = dockItems,
                    selectedKey = folder,
                    onItemSelected = { key ->
                        if (key != folder) Haptics.tick(view)
                        vm.setFolder(key)
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // §8.4 gate (Google Messages behavior): if the role was denied, the
            // conversation area is an empty state with a single card that
            // re-triggers the role request. No list, no search, no composer.
            if (!isDefaultSmsApp) {
                DefaultSmsGate(
                    onRequestDefault = onRequestDefault,
                    roleRequestFailed = roleRequestFailed,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDefaultApps = onOpenDefaultApps,
                )
                return@Column
            }

            // Search bar — incremental, chip-based (§8.5)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = searchActive,
                    enter = fadeIn(Motion.effectsDefault()),
                    exit = fadeOut(Motion.effectsFast()),
                ) {
                    IconButton(onClick = { exitSearch() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_close_search))
                    }
                }
                LiquidGlassSurface(
                    shape = RoundedCornerShape(26.dp),
                    depth = GlassDepth.LOW,
                    modifier = Modifier.weight(1f),
                ) {
                    TextField(
                        value = typing,
                        onValueChange = { vm.setTyping(it); searchActive = true },
                        placeholder = {
                            Text(
                                if (chips.isEmpty()) stringResource(R.string.home_search_placeholder) else stringResource(R.string.home_search_add_keyword),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            if (!searchActive) Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (searchActive && (typing.isNotEmpty() || chips.isNotEmpty())) {
                                IconButton(onClick = {
                                    if (typing.isNotEmpty()) vm.setTyping("") else vm.clearSearch()
                                }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear)) }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.commitTyping() }),
                        shape = RoundedCornerShape(26.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { if (it.isFocused) searchActive = true },
                    )
                }
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
    val rightAction by SwipeActions.right.collectAsStateWithLifecycle()
    val leftAction by SwipeActions.left.collectAsStateWithLifecycle()
    val drafts by com.messages.app.ui.common.DraftStore.drafts.collectAsStateWithLifecycle()
    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
    val selectedThreads by vm.selectedThreads.collectAsStateWithLifecycle()
    val selectionActive = selectedThreads.isNotEmpty()
    val rowView = LocalView.current
    var badgeSheet by remember {
        mutableStateOf<com.messages.protection.SenderBadges.Badge?>(null)
    }
    Column(Modifier.fillMaxSize()) {
        // Top filter sub-bar: Unread only filter chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = unreadOnly,
                onClick = { vm.setUnreadOnly(!unreadOnly) },
                label = { Text(stringResource(R.string.home_filter_unread), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (unreadOnly) {
                    {
                        Icon(
                            Icons.Filled.Close, contentDescription = stringResource(R.string.home_clear_unread_filter),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else null,
                shape = RoundedCornerShape(12.dp),
            )
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
            val loadState by remember(targetFolder, unreadOnly) {
                if (unreadOnly) vm.unreadConversationsFor(targetFolder)
                else vm.conversationsFor(targetFolder)
            }.collectAsStateWithLifecycle()

            val pastGrace = rememberLoadingGrace(loadState is LoadState.Loading)
            val conversations = loadState.valueOrNull.orEmpty()
            val badgeMeta by vm.latestIncomingMeta.collectAsStateWithLifecycle()

            val pinnedList = remember(conversations) { conversations.filter { it.pinned } }
            val regularList = remember(conversations, pinnedList) {
                if (pinnedList.isNotEmpty()) conversations.filter { !it.pinned }
                else conversations
            }

            when (listRender(loadState, pastGrace)) {
                ListRender.NOTHING -> Box(Modifier.fillMaxSize()) // fast load, no flash
                ListRender.LOADING -> ListSkeleton(stringResource(R.string.home_loading_conversations))
                ListRender.FAILED -> LoadFailedState(
                    headline = stringResource(R.string.home_load_failed),
                    reason = (loadState as? LoadState.Failed)?.reason,
                    onRetry = vm::retry,
                )
                ListRender.EMPTY ->
                    if (unreadOnly) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.home_no_unread),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else EmptyFolderState(targetFolder)
                ListRender.CONTENT -> {
                    val listState = rememberLazyListState()
                    val listScope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        if (pinnedList.isNotEmpty() && !unreadOnly) {
                            item(key = "pinned_carousel") {
                                PinnedConversationsRow(
                                    pinnedList = pinnedList,
                                    onOpenThread = onOpenThread,
                                )
                            }
                        }

                        itemsIndexed(
                            if (pinnedList.isNotEmpty() && !unreadOnly) regularList else conversations,
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
                                index < conversations.lastIndex
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
                                contentDescription = stringResource(R.string.home_back_to_top),
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

/** Pinned Conversations Carousel (iMessage-inspired horizontal glass avatar bar) */
@Composable
private fun PinnedConversationsRow(
    pinnedList: List<ConversationEntity>,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pinnedList.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.home_pinned_section),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            items(pinnedList, key = { it.threadId }) { conv ->
                val unread = conv.unreadCount > 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(66.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenThread(conv.threadId) }
                        .padding(vertical = 4.dp),
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (unread) 2.dp else 1.dp,
                                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContactAvatar(
                                name = conv.contactName ?: conv.address,
                                category = conv.category,
                                size = 52.dp,
                                photoUri = if (conv.locked) null
                                else com.messages.app.ui.common.rememberContactPhoto(conv.address),
                            )
                        }
                        if (unread) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = conv.contactName?.split(" ")?.firstOrNull() ?: conv.address,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
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
                            Icons.Filled.Close, contentDescription = stringResource(R.string.home_remove_chip, chip),
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
        items(SEARCH_LABELS) { (key, labelRes) ->
            FilterChip(
                selected = labelFilter == key,
                onClick = { vm.setLabelFilter(if (labelFilter == key) null else key) },
                label = { Text(stringResource(labelRes)) },
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
                    stringResource(R.string.home_search_min_length),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (saved.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_recent_searches),
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
                stringResource(R.string.home_search_no_match),
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
                SearchSectionHeader(stringResource(R.string.home_search_section_conversations))
            }
            items(state.conversationMatches, key = { "conv-${it.threadId}" }) { conv ->
                ConversationMatchRow(conv, state.activeKeywords) { onOpenThread(conv.threadId) }
            }
            if (state.results.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.home_search_section_messages)) }
            }
        }
        items(normal, key = { it.message.id }) { row ->
            SearchResultRow(row, state.activeKeywords, onOpenResult)
        }
        if (filtered.isNotEmpty()) {
            item { SearchSectionHeader(stringResource(R.string.home_search_section_spam)) }
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
                    pluralStringResource(
                        R.plurals.home_match_count,
                        keywords.size, row.matchCount, keywords.size,
                    ),
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
                Text(stringResource(R.string.home_contacts_prompt_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.home_contacts_prompt_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) },
                ) { Text(stringResource(R.string.home_contacts_allow)) }
            }
            IconButton(onClick = {
                dismissed = true
                prefs.edit().putBoolean("contacts_banner_dismissed", true).apply()
            }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss))
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
private fun DefaultSmsGate(
    onRequestDefault: () -> Unit,
    roleRequestFailed: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    onOpenDefaultApps: () -> Unit = {},
) {
    val context = LocalContext.current
    val isRestricted = remember(roleRequestFailed) {
        roleRequestFailed || MainActivity.isRestrictedSettingsActive(context)
    }
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
                stringResource(R.string.home_default_sms_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_default_sms_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            if (isRestricted) {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    depth = GlassDepth.LOW,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.restricted_settings_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.restricted_settings_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_open_app_settings), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenDefaultApps,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.action_open_default_apps), maxLines = 1)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onRequestDefault,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isRestricted) stringResource(R.string.restricted_settings_retry)
                    else stringResource(R.string.home_default_sms_action)
                )
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
    badge: com.messages.protection.SenderBadges.Badge? = null,
    onBadgeTap: (com.messages.protection.SenderBadges.Badge) -> Unit = {},
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val rightEnabled = rightAction != SwipeActions.NONE && !selectionActive
    val leftEnabled = leftAction != SwipeActions.NONE && !selectionActive

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

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (rightEnabled) onAction(rightAction, conv)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (leftEnabled) onAction(leftAction, conv)
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = rightEnabled,
        enableDismissFromEndToStart = leftEnabled,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val action = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> rightAction
                SwipeToDismissBoxValue.EndToStart -> leftAction
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) {
                SwipeActionBackground(
                    action = action,
                    fromStart = direction == SwipeToDismissBoxValue.StartToEnd,
                    progress = dismissState.progress,
                    willDismiss = dismissState.targetValue != SwipeToDismissBoxValue.Settled,
                )
            }
        },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ConversationRow(
                conv = conv,
                draft = draft,
                onClick = onClick,
                badge = badge,
                onBadgeTap = onBadgeTap,
                selected = selected,
                onLongClick = onLongClick,
            )
        }
    }
}

private data class SwipeVisuals(
    val icon: ImageVector,
    val container: Color,
    val tint: Color,
    val label: String,
)

@Composable
private fun SwipeActionBackground(
    action: String,
    fromStart: Boolean,
    progress: Float,
    willDismiss: Boolean,
) {
    val isDark = LocalDarkTheme.current
    val view = LocalView.current

    // Trigger subtle tactile tick when passing the dismiss confirmation threshold
    LaunchedEffect(willDismiss) {
        if (willDismiss) {
            com.messages.designsystem.Haptics.tick(view)
        }
    }

    val visuals = when (action) {
        SwipeActions.ARCHIVE -> SwipeVisuals(
            icon = Icons.Outlined.Archive,
            container = if (isDark) Color(0xFF0F5223) else Color(0xFF1E8E3E), // Google Messages Emerald Green
            tint = Color.White,
            label = stringResource(R.string.home_archive),
        )
        SwipeActions.DELETE -> SwipeVisuals(
            icon = Icons.Outlined.Delete,
            container = if (isDark) Color(0xFF7F1D1D) else Color(0xFFD93025), // Google Messages Crimson Red
            tint = Color.White,
            label = stringResource(R.string.action_delete),
        )
        SwipeActions.PIN -> SwipeVisuals(
            icon = Icons.Outlined.PushPin,
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            label = stringResource(R.string.home_pinned),
        )
        SwipeActions.READ -> SwipeVisuals(
            icon = Icons.Outlined.MarkChatRead,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            label = stringResource(R.string.home_mark_read),
        )
        SwipeActions.MUTE -> SwipeVisuals(
            icon = Icons.Outlined.NotificationsOff,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            tint = MaterialTheme.colorScheme.onSurface,
            label = stringResource(R.string.action_mute),
        )
        else -> return
    }

    // Gmail-style physics-based animations
    val iconScale by animateFloatAsState(
        targetValue = if (willDismiss) 1.22f else (0.72f + progress * 0.35f).coerceIn(0.72f, 1.05f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "gmail-swipe-scale",
    )

    val iconAlpha by animateFloatAsState(
        targetValue = (progress * 3.5f).coerceIn(0.45f, 1.0f),
        label = "gmail-swipe-icon-alpha",
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (progress > 0.15f) ((progress - 0.15f) * 3.5f).coerceIn(0f, 1f) else 0f,
        label = "gmail-swipe-text-alpha",
    )

    val textSlide by animateDpAsState(
        targetValue = if (willDismiss) 0.dp else ((1f - progress.coerceIn(0f, 1f)) * 14).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "gmail-swipe-text-slide",
    )

    Row(
        Modifier
            .fillMaxSize()
            .background(visuals.container)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
    ) {
        if (fromStart) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = visuals.label,
                color = visuals.tint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationX = textSlide.toPx()
                },
            )
        } else {
            Text(
                text = visuals.label,
                color = visuals.tint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationX = -textSlide.toPx()
                },
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
            )
        }
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
    val rowShape = RoundedCornerShape(16.dp)
    val rowBg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(rowShape)
            .background(rowBg)
            .then(
                if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, rowShape)
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.home_select_conversation) else null,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    if (badge != null && badge != com.messages.protection.SenderBadges.Badge.VERIFIED) {
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
                            contentDescription = stringResource(R.string.home_muted),
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
                        showDraft -> stringResource(R.string.home_draft_prefix, draft)
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
                        // `semantics { }` is not a composable scope.
                        val unreadLabel = pluralStringResource(
                            if (conv.muted) R.plurals.home_unread_count_muted
                            else R.plurals.home_unread_count,
                            conv.unreadCount, conv.unreadCount,
                        )
                        if (conv.muted) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    conv.unreadCount.toString(),
                                    modifier = Modifier.semantics {
                                        contentDescription = unreadLabel
                                    },
                                )
                            }
                        } else {
                            Badge {
                                Text(
                                    conv.unreadCount.toString(),
                                    modifier = Modifier.semantics {
                                        contentDescription = unreadLabel
                                    },
                                )
                            }
                        }
                    }
                    conv.pinned -> {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.PushPin, contentDescription = stringResource(R.string.home_pinned),
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
    @StringRes val headline: Int,
    @StringRes val supporting: Int,
)

private fun emptySpecFor(folder: String): EmptyStateSpec = when (folder) {
    "SPAM" -> EmptyStateSpec(
        Icons.Outlined.Shield,
        R.string.home_empty_spam_headline,
        R.string.home_empty_spam_supporting,
    )
    "PROMOTIONS" -> EmptyStateSpec(
        Icons.Outlined.LocalOffer,
        R.string.home_empty_promotions_headline,
        R.string.home_empty_promotions_supporting,
    )
    "REVIEW" -> EmptyStateSpec(
        Icons.Outlined.RateReview,
        R.string.home_empty_review_headline,
        R.string.home_empty_review_supporting,
    )
    "BLOCKED" -> EmptyStateSpec(
        Icons.Outlined.Block,
        R.string.home_empty_blocked_headline,
        R.string.home_empty_blocked_supporting,
    )
    "TRANSACTIONS" -> EmptyStateSpec(
        Icons.Outlined.ReceiptLong,
        R.string.home_empty_transactions_headline,
        R.string.home_empty_transactions_supporting,
    )
    else -> EmptyStateSpec(
        Icons.Outlined.Forum,
        R.string.home_empty_default_headline,
        R.string.home_empty_default_supporting,
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
                stringResource(spec.headline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(spec.supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// V2-45: the formatters used to live here as file-level vals, which froze the
// locale and time zone at class-init. AppDateFormat reads both at render time
// and still reuses the formatter objects — allocating one per row per frame
// showed up in the Phase 6 fling profile, and that stays fixed.
private fun formatTime(ts: Long): String = AppDateFormat.listRowStamp(ts)
