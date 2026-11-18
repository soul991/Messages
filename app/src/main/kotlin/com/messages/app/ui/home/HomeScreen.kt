package com.messages.app.ui.home

import androidx.activity.compose.BackHandler
import com.messages.app.MainActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.zIndex
import androidx.compose.material3.Surface
import com.messages.app.ui.common.DateSkeletons
import com.messages.designsystem.DockItem
import com.messages.designsystem.BottomDock
import com.messages.designsystem.LocalDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.messages.designsystem.groupCard
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
import com.messages.designsystem.GlassTopBar
import com.messages.designsystem.LiquidGlassActionDivider
import com.messages.designsystem.LiquidGlassActionGroup
import com.messages.designsystem.LiquidGlassActionRow
import com.messages.designsystem.LiquidGlassIconButton
import com.messages.designsystem.LiquidGlassSheetDialog
import com.messages.designsystem.LiquidGlassSurface
import com.messages.designsystem.liquidGlassRimBrush
import com.messages.designsystem.liquidGlassSheenBrush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
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
import com.messages.app.ui.common.sharedThreadName
import com.messages.app.ui.common.valueOrNull
import com.messages.app.ui.search.SearchHighlight
import com.messages.core.db.ConversationEntity
import com.messages.designsystem.DockIcons
import com.messages.designsystem.Haptics
import com.messages.designsystem.JumpToTopButton
import com.messages.designsystem.Motion
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.SwipeIcons
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ripple
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import com.messages.core.util.FriendlySenderNames
import com.messages.designsystem.DesignTokens
import com.messages.designsystem.InterFontFamily
import com.messages.designsystem.dockActivePill
import com.messages.designsystem.dockBackground
import com.messages.designsystem.dockBorder
import com.messages.designsystem.greenText
import com.messages.designsystem.hairline
import com.messages.designsystem.selectedRow
import com.messages.designsystem.textMuted
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.messages.app.ui.common.showTransient
import com.messages.app.ui.common.AppTransientSnackbarHost
import com.messages.app.R

private val ROW_HEIGHT = 82.dp
private val ROW_AVATAR = 52.dp
private val ROW_GAP = 12.dp

// V2-36. The left half of each pair is the stored folder/label id and never
// changes; the right half is a resource, because the tab is the first thing a
// user reads and "Inbox" is not a name they typed.
private val FOLDERS = listOf(
    "INBOX" to R.string.category_personal,
    "PROMOTIONS" to R.string.category_promotional,
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

/**
 * Helper for dock visibility (B1: dock is always visible; hide-on-scroll is removed).
 */
internal fun calculateDockVisibility(
    currentlyVisible: Boolean = true,
    deltaY: Float = 0f,
    isTop: Boolean = false,
): Boolean = true

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDefaultSmsApp: Boolean,
    initialFolder: String?,
    onFolderConsumed: () -> Unit = {},
    onRequestDefault: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onOpenContact: (Long) -> Unit = {},
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
    LaunchedEffect(initialFolder) {
        if (initialFolder != null) {
            vm.setFolder(initialFolder)
            onFolderConsumed()
        }
    }

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    var moreSheetConv by remember { mutableStateOf<ConversationEntity?>(null) }

    // Hoist per-folder listState to maintain independent scroll positions across tabs
    val inboxListState = rememberLazyListState()
    val promotionsListState = rememberLazyListState()
    val spamListState = rememberLazyListState()
    val reviewListState = rememberLazyListState()
    val blockedListState = rememberLazyListState()
    val fallbackListState = rememberLazyListState()

    val folderListStates = remember(
        inboxListState,
        promotionsListState,
        spamListState,
        reviewListState,
        blockedListState,
    ) {
        mapOf(
            "INBOX" to inboxListState,
            "TRANSACTIONS" to inboxListState, // mapped to inbox for safe migration
            "PROMOTIONS" to promotionsListState,
            "SPAM" to spamListState,
            "REVIEW" to reviewListState,
            "BLOCKED" to blockedListState,
        )
    }
    val listStateFor: (String) -> LazyListState = { folderKey ->
        folderListStates[folderKey] ?: fallbackListState
    }

    fun exitSearch() {
        searchActive = false
        vm.clearSearch()
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) {
            kotlinx.coroutines.android.awaitFrame()
            searchFocusRequester.requestFocus()
        }
    }

    val searchProgress by animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = Motion.screenSpring(),
        label = "search_spring_progress",
    )
    BackHandler(enabled = !searchActive && !selectionActive && folder != "INBOX") {
        com.messages.app.ui.navigation.NavigationBackPolicy.handleHomeBack(
            searchActive = searchActive,
            selectionActive = selectionActive,
            currentFolder = folder,
            onResetFolderToInbox = { vm.setFolder("INBOX") },
        )
    }
    BackHandler(enabled = searchActive) {
        com.messages.app.ui.navigation.NavigationBackPolicy.handleHomeBack(
            searchActive = searchActive,
            selectionActive = selectionActive,
            currentFolder = folder,
            onExitSearch = { exitSearch() },
        )
    }
    BackHandler(enabled = selectionActive) {
        com.messages.app.ui.navigation.NavigationBackPolicy.handleHomeBack(
            searchActive = searchActive,
            selectionActive = selectionActive,
            currentFolder = folder,
            onClearSelection = { vm.clearSelection() },
        )
    }

    // G1: Search bar scroll-away on scroll down, return on scroll up or top
    val density = androidx.compose.ui.platform.LocalDensity.current
    val searchBarMaxHeightDp = 60.dp // 12dp top + 40dp pill + 8dp bottom
    val searchBarMaxHeightPx = with(density) { searchBarMaxHeightDp.toPx() }
    var searchBarOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(searchBarMaxHeightPx, selectionActive, searchActive) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (selectionActive || searchActive) return Offset.Zero
                val delta = available.y
                val prevOffset = searchBarOffsetPx
                val newOffset = (prevOffset + delta).coerceIn(-searchBarMaxHeightPx, 0f)
                val consumed = newOffset - prevOffset
                searchBarOffsetPx = newOffset
                return Offset(0f, consumed)
            }
        }
    }

    val currentFolderListState = listStateFor(folder)
    LaunchedEffect(currentFolderListState.firstVisibleItemIndex, currentFolderListState.firstVisibleItemScrollOffset) {
        if (currentFolderListState.firstVisibleItemIndex == 0 && currentFolderListState.firstVisibleItemScrollOffset == 0) {
            searchBarOffsetPx = 0f
        }
    }
    LaunchedEffect(folder) {
        searchBarOffsetPx = 0f
    }
    LaunchedEffect(selectionActive) {
        if (selectionActive) searchBarOffsetPx = 0f
    }

    // Swipe actions (§8.2) with undo snackbars for the destructive ones.
    fun onSwipeAction(action: String, conv: ConversationEntity) {
        Haptics.tick(view)
        when (action) {
            SwipeActions.ARCHIVE -> {
                vm.archive(conv.threadId)
                scope.launch {
                    val r = snackbarHostState.showTransient(
                        archivedMessage, actionLabel = undoLabel, withDismissAction = true,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.unarchive(conv.threadId)
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

    val inboxUnreadFlow = remember(vm) { vm.folderUnread("INBOX") }
    val promoUnreadFlow = remember(vm) { vm.folderUnread("PROMOTIONS") }
    val spamUnreadFlow = remember(vm) { vm.folderUnread("SPAM") }
    val reviewUnreadFlow = remember(vm) { vm.folderUnread("REVIEW") }

    val inboxUnread by inboxUnreadFlow.collectAsStateWithLifecycle()
    val promoUnread by promoUnreadFlow.collectAsStateWithLifecycle()
    val spamUnread by spamUnreadFlow.collectAsStateWithLifecycle()
    val reviewUnread by reviewUnreadFlow.collectAsStateWithLifecycle()

    val dockItems = remember(inboxUnread, promoUnread, spamUnread, reviewUnread) {
        listOf(
            DockItem(
                key = "INBOX",
                title = resources.getString(R.string.category_personal),
                outlinedIcon = DockIcons.Personal,
                filledIcon = DockIcons.Personal,
                unreadCount = inboxUnread,
                category = "INBOX",
            ),
            DockItem(
                key = "PROMOTIONS",
                title = resources.getString(R.string.category_promotional),
                outlinedIcon = DockIcons.Promotional,
                filledIcon = DockIcons.Promotional,
                unreadCount = promoUnread,
                category = "PROMOTIONS",
            ),
            DockItem(
                key = "SPAM",
                title = resources.getString(R.string.category_spam),
                outlinedIcon = DockIcons.Spam,
                filledIcon = DockIcons.Spam,
                unreadCount = spamUnread,
                category = "SPAM",
            ),
            DockItem(
                key = "REVIEW",
                title = resources.getString(R.string.category_review),
                outlinedIcon = DockIcons.Review,
                filledIcon = DockIcons.Review,
                unreadCount = reviewUnread,
                category = "REVIEW",
            ),
        )
    }

    val hazeState = remember { HazeState() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {},
            topBar = {
                GlassTopBar(
                    isDark = LocalDarkTheme.current,
                    hazeState = hazeState,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .graphicsLayer {
                                    alpha = (1f - searchProgress).coerceIn(0f, 1f)
                                    translationY = -searchProgress * 28.dp.toPx()
                                },
                        ) {
                        AnimatedContent(
                            targetState = selectionActive,
                            transitionSpec = {
                                fadeIn(Motion.effectsDefault()) togetherWith fadeOut(Motion.effectsFast())
                            },
                            label = "home-topbar-mode",
                            modifier = Modifier.fillMaxSize(),
                        ) { isSelecting ->
                            if (isSelecting) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        IconButton(onClick = { vm.clearSelection() }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.home_clear_selection),
                                                tint = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            pluralStringResource(
                                                R.plurals.home_selected_count,
                                                selectedThreads.size, selectedThreads.size,
                                            ),
                                            style = TextStyle(
                                                fontFamily = InterFontFamily,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
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
                                                val r = snackbarHostState.showTransient(
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
                                    }
                                }
                            } else if (folder == "BLOCKED") {
                                // Blocked Header: Back button to return to Inbox + "Blocked" title + Shield & More options
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 8.dp, end = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        IconButton(onClick = { vm.setFolder("INBOX") }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.action_back),
                                                tint = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.category_blocked),
                                            style = TextStyle(
                                                fontFamily = InterFontFamily,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight(700),
                                                letterSpacing = (-0.5).sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val shieldTint = if (isDefaultSmsApp) MaterialTheme.colorScheme.greenText else MaterialTheme.colorScheme.textMuted
                                        LiquidGlassIconButton(
                                            onClick = onDashboard,
                                            hazeState = hazeState,
                                        ) {
                                            Icon(
                                                imageVector = if (isDefaultSmsApp) DockIcons.Shield else DockIcons.ShieldOff,
                                                contentDescription = stringResource(R.string.home_protection_dashboard),
                                                tint = shieldTint,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        LiquidGlassIconButton(
                                            onClick = { showHomeMenu = true },
                                            hazeState = hazeState,
                                        ) {
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = stringResource(R.string.action_more_options),
                                                tint = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Standard Header: "Messages" 30sp/700, Shield icon button, More options
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 16.dp, end = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                            val haptics = androidx.compose.ui.hapticfeedback.HapticFeedbackType
                            val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val accessibleEntry = remember {
                                com.messages.app.ui.secret.SecretEntryAccess.enabled(context)
                            }
                            val entryLabel = stringResource(
                                com.messages.app.ui.secret.SecretEntryAccess.ACTION_LABEL,
                            )

                            Text(
                                text = stringResource(R.string.app_name),
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight(700),
                                    letterSpacing = (-0.5).sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            val held = try {
                                                withTimeout(1_500) {
                                                    waitForUpOrCancellation()
                                                    false
                                                }
                                            } catch (_: PointerEventTimeoutCancellationException) {
                                                true
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

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val shieldTint = if (isDefaultSmsApp) MaterialTheme.colorScheme.greenText else MaterialTheme.colorScheme.textMuted
                                LiquidGlassIconButton(
                                    onClick = onDashboard,
                                    hazeState = hazeState,
                                ) {
                                    Icon(
                                        imageVector = if (isDefaultSmsApp) DockIcons.Shield else DockIcons.ShieldOff,
                                        contentDescription = stringResource(R.string.home_protection_dashboard),
                                        tint = shieldTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                LiquidGlassIconButton(
                                    onClick = { showHomeMenu = true },
                                    hazeState = hazeState,
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.action_more_options),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Unified Liquid Glass search bar with spring transform
                val searchPillShape = remember { RoundedCornerShape(24.dp) }
                LiquidGlassSurface(
                    shape = searchPillShape,
                    hazeState = hazeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(48.dp)
                        .graphicsLayer {
                            translationY = -searchProgress * 56.dp.toPx()
                        }
                        .clip(searchPillShape)
                        .then(
                            if (!searchActive && !selectionActive) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(),
                                    onClick = { searchActive = true },
                                )
                            } else Modifier
                        ),
                ) {
                    if (!searchActive && searchProgress < 0.05f) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = DockIcons.Search,
                                contentDescription = stringResource(R.string.home_search_placeholder),
                                tint = MaterialTheme.colorScheme.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.home_search_placeholder),
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 20.sp,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false,
                                    ),
                                ),
                                color = MaterialTheme.colorScheme.textMuted,
                            )
                        }
                    } else {
                        TextField(
                            value = typing,
                            onValueChange = { vm.setTyping(it) },
                            placeholder = {
                                Text(
                                    if (chips.isEmpty()) stringResource(R.string.home_search_placeholder)
                                    else stringResource(R.string.home_search_add_keyword),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                IconButton(onClick = { exitSearch() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.chat_close_search),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            trailingIcon = {
                                if (typing.isNotEmpty() || chips.isNotEmpty()) {
                                    IconButton(onClick = {
                                        if (typing.isNotEmpty()) {
                                            vm.setTyping("")
                                        } else {
                                            vm.clearSearch()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                            }),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(searchFocusRequester),
                        )
                    }
                }
            }
        }
    },
) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -searchProgress * 56.dp.toPx()
                    }
                    .nestedScroll(nestedScrollConnection),
            ) {
                if (!isDefaultSmsApp && searchProgress < 0.05f) {
                    DefaultSmsInfoCard(
                        onRequestDefault = onRequestDefault,
                        roleRequestFailed = roleRequestFailed,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenDefaultApps = onOpenDefaultApps,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    if (searchProgress < 0.99f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = (1f - searchProgress).coerceIn(0f, 1f)
                                    translationY = searchProgress * 32.dp.toPx()
                                },
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                ContactsPermissionBanner()
                                FolderPane(
                                    vm = vm,
                                    folder = folder,
                                    searchActive = searchActive,
                                    listStateFor = listStateFor,
                                    onSelectFolder = { key ->
                                        if (key != folder) Haptics.tick(view)
                                        vm.setFolder(key)
                                    },
                                    onOpenThread = onOpenThread,
                                    onSwipeAction = ::onSwipeAction,
                                    onMoreAction = { conv ->
                                        Haptics.tick(view)
                                        moreSheetConv = conv
                                    },
                                    hazeState = hazeState,
                                )
                            }
                        }
                    }

                    if (searchProgress > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = searchProgress.coerceIn(0f, 1f)
                                    translationY = (1f - searchProgress) * 32.dp.toPx()
                                },
                        ) {
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
                    }
                }
            }
        }

        // Floating Jump to Top Button (44dp visual glass, 48dp touch target, 16dp from start, above dock at 88dp)
        JumpToTopButton(
            listState = currentFolderListState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 88.dp),
            hazeState = hazeState,
            visible = !searchActive && !selectionActive,
            contentDescription = stringResource(R.string.home_back_to_top),
            onClickExtra = {
                searchBarOffsetPx = 0f
            },
        )

        // Floating New Message FAB (48dp round icon-only, green, 20dp from right, above dock at 88dp)
        AnimatedVisibility(
            visible = !searchActive && !selectionActive,
            enter = scaleIn(Motion.butterSpring()) + fadeIn(Motion.effectsDefault()),
            exit = scaleOut(Motion.butterSpring()) + fadeOut(Motion.effectsFast()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 88.dp),
        ) {
            val fabInteractionSource = remember { MutableInteractionSource() }
            val fabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed) 0.92f else 1f,
                animationSpec = Motion.butterSpring(),
                label = "fab-scale",
            )
            val isDarkFab = LocalDarkTheme.current
            val fabRim = remember(isDarkFab) {
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (isDarkFab) 0.45f else 0.70f), Color.Transparent),
                )
            }
            val fabSheen = remember {
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .background(fabSheen)
                    .border(1.dp, fabRim, CircleShape)
                    .clickable(
                        interactionSource = fabInteractionSource,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onCompose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DockIcons.NewMessage,
                    contentDescription = stringResource(R.string.home_new_message),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Floating Undo / Transient Message Banner
        // Sits centered above the dock with clear separation (96dp bottom padding leaves clearance over the 62dp dock).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp)
                .zIndex(5f),
        ) {
            AppTransientSnackbarHost(
                hostState = snackbarHostState,
            )
        }

        // Floating Bottom Dock (56dp tall, compact pill, wrap content width, centered, always visible)
        AnimatedVisibility(
            visible = !searchActive && !selectionActive,
            enter = slideInVertically(Motion.butterSpring()) { it } + fadeIn(Motion.effectsDefault()),
            exit = slideOutVertically(Motion.butterSpring()) { it } + fadeOut(Motion.effectsFast()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomDock(
                items = dockItems,
                selectedKey = folder,
                onItemSelected = { key ->
                    if (key != folder) Haptics.tick(view)
                    vm.setFolder(key)
                },
                hazeState = hazeState,
            )
        }

        // Liquid Glass Home overflow menu sheet
        if (showHomeMenu) {
            LiquidGlassSheetDialog(
                onDismissRequest = { showHomeMenu = false },
            ) {
                LiquidGlassActionGroup {
                    LiquidGlassActionRow(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.settings_title),
                        onClick = { showHomeMenu = false; onSettings() },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = Icons.Filled.Drafts,
                        label = if (outboxWaiting > 0) {
                            stringResource(R.string.outbox_title_count, outboxWaiting)
                        } else {
                            stringResource(R.string.outbox_title)
                        },
                        onClick = { showHomeMenu = false; onOpenOutbox() },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.Archive,
                        label = stringResource(R.string.archived_title),
                        onClick = { showHomeMenu = false; onOpenArchived() },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.StarOutline,
                        label = stringResource(R.string.home_starred_messages),
                        onClick = { showHomeMenu = false; onOpenStarred() },
                    )
                    if (folder != "BLOCKED") {
                        LiquidGlassActionDivider()
                        LiquidGlassActionRow(
                            icon = Icons.Outlined.Block,
                            label = stringResource(R.string.category_blocked),
                            onClick = {
                                showHomeMenu = false
                                vm.setFolder("BLOCKED")
                            },
                        )
                    }
                }
                LiquidGlassActionGroup {
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.MarkChatRead,
                        label = stringResource(R.string.home_mark_all_as_read),
                        onClick = {
                            showHomeMenu = false
                            vm.markFolderRead(folder)
                            scope.launch {
                                snackbarHostState.showTransient(markedReadMessage)
                            }
                        },
                    )
                }
            }
        }

        // Mac WhatsApp Liquid Glass conversation "More" action sheet
        moreSheetConv?.let { conv ->
            val displayName = remember(conv.contactName, conv.address) {
                conv.contactName?.takeIf { it.isNotBlank() } ?: FriendlySenderNames.format(conv.address)
            }
            val blockedSnackbarText = stringResource(R.string.home_blocked_snackbar, displayName)
            LiquidGlassSheetDialog(
                onDismissRequest = { moreSheetConv = null },
                header = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContactAvatar(
                            name = displayName,
                            category = conv.category,
                            size = 44.dp,
                            photoUri = com.messages.app.ui.common.rememberContactPhoto(conv.address.ifBlank { null }),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = displayName,
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ) {
                LiquidGlassActionGroup {
                    LiquidGlassActionRow(
                        icon = if (conv.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                        label = stringResource(if (conv.muted) R.string.home_unmute else R.string.home_mute),
                        onClick = {
                            moreSheetConv = null
                            Haptics.tick(view)
                            vm.toggleMute(conv.threadId, !conv.muted)
                        },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.home_contact_info),
                        onClick = {
                            moreSheetConv = null
                            onOpenContact(conv.threadId)
                        },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = if (conv.unreadCount > 0) Icons.Outlined.MarkChatRead else Icons.Outlined.MarkChatUnread,
                        label = stringResource(if (conv.unreadCount > 0) R.string.home_mark_read else R.string.home_mark_unread),
                        onClick = {
                            moreSheetConv = null
                            Haptics.tick(view)
                            if (conv.unreadCount > 0) vm.markThreadRead(conv.threadId)
                            else vm.markThreadUnread(conv.threadId)
                        },
                    )
                    LiquidGlassActionDivider()
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.PushPin,
                        label = stringResource(if (conv.pinned) R.string.home_unpin else R.string.home_pin),
                        onClick = {
                            moreSheetConv = null
                            Haptics.tick(view)
                            vm.togglePin(conv.threadId, !conv.pinned)
                        },
                    )
                }
                val canBlock = conv.category != "BLOCKED" && conv.address.isNotBlank() && !conv.address.contains(';')
                LiquidGlassActionGroup {
                    if (canBlock) {
                        LiquidGlassActionRow(
                            icon = Icons.Outlined.Block,
                            label = stringResource(R.string.home_block_sender, displayName),
                            destructive = true,
                            onClick = {
                                moreSheetConv = null
                                Haptics.tick(view)
                                vm.blockSender(conv.address, conv.threadId)
                                scope.launch {
                                    snackbarHostState.showTransient(blockedSnackbarText)
                                }
                            },
                        )
                        LiquidGlassActionDivider()
                    }
                    LiquidGlassActionRow(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.home_delete_chat),
                        destructive = true,
                        onClick = {
                            moreSheetConv = null
                            Haptics.tick(view)
                            val trashedAfter = System.currentTimeMillis()
                            vm.trashThread(conv.threadId)
                            scope.launch {
                                val r = snackbarHostState.showTransient(
                                    trashedMessage,
                                    actionLabel = undoLabel,
                                    withDismissAction = true,
                                )
                                if (r == SnackbarResult.ActionPerformed) {
                                    vm.undoTrashThread(conv.threadId, trashedAfter)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Conversation list, with a directional animated switch (§9). Flat list without section headers or card containers. */
@Composable
private fun FolderPane(
    vm: HomeViewModel,
    folder: String,
    searchActive: Boolean,
    onSelectFolder: (String) -> Unit,
    onOpenThread: (Long) -> Unit,
    onSwipeAction: (String, ConversationEntity) -> Unit,
    onMoreAction: (ConversationEntity) -> Unit,
    listStateFor: (String) -> LazyListState,
    hazeState: HazeState,
) {
    val drafts by com.messages.app.ui.common.DraftStore.drafts.collectAsStateWithLifecycle()
    val selectedThreads by vm.selectedThreads.collectAsStateWithLifecycle()
    val selectionActive = selectedThreads.isNotEmpty()
    val rowView = LocalView.current
    var openSwipeThreadId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(folder, selectionActive, searchActive) {
        if (openSwipeThreadId != null) {
            openSwipeThreadId = null
        }
    }

    AnimatedContent(
        targetState = folder,
        transitionSpec = {
            val from = FOLDERS.indexOfFirst { it.first == initialState }
            val to = FOLDERS.indexOfFirst { it.first == targetState }
            val dir = if (to >= from) 1 else -1
            ((slideInHorizontally(Motion.butterSpring()) { it / 3 * dir } +
                fadeIn(Motion.effectsDefault()) +
                scaleIn(Motion.butterSpring(), initialScale = 0.96f)) togetherWith
                (slideOutHorizontally(Motion.butterSpring()) { -it / 3 * dir } +
                    fadeOut(Motion.effectsFast()) +
                    scaleOut(Motion.butterSpring(), targetScale = 0.96f))).apply {
                targetContentZIndex = 1f
            }
        },
        label = "folder-switch",
    ) { targetFolder ->
        val folderListState = listStateFor(targetFolder)
        val loadState by remember(targetFolder) {
            vm.conversationsFor(targetFolder)
        }.collectAsStateWithLifecycle()

        val pastGrace = rememberLoadingGrace(loadState is LoadState.Loading)
        val conversations = loadState.valueOrNull.orEmpty()

        when (listRender(loadState, pastGrace)) {
            ListRender.NOTHING -> Box(Modifier.fillMaxSize()) // fast load, no flash
            ListRender.LOADING -> ListSkeleton(stringResource(R.string.home_loading_conversations))
            ListRender.FAILED -> LoadFailedState(
                headline = stringResource(R.string.home_load_failed),
                reason = (loadState as? LoadState.Failed)?.reason,
                onRetry = vm::retry,
            )
            ListRender.EMPTY -> EmptyFolderState(targetFolder)
            ListRender.CONTENT -> {
                Box(Modifier.fillMaxSize()) {
                    LaunchedEffect(folderListState) {
                        snapshotFlow { folderListState.isScrollInProgress }
                            .collect { scrolling ->
                                if (scrolling && openSwipeThreadId != null) {
                                    openSwipeThreadId = null
                                }
                            }
                    }
                    LazyColumn(
                        state = folderListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(hazeState)
                            .testTag("home_conversation_list"),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 160.dp,
                        ),
                    ) {
                        items(
                            items = conversations,
                            key = { it.threadId },
                        ) { conv ->
                            SwipeableConversationRow(
                                conv = conv,
                                draft = drafts[conv.threadId],
                                isSwipedOpen = openSwipeThreadId == conv.threadId,
                                onSwipeOpenChange = { open ->
                                    openSwipeThreadId = if (open) conv.threadId else if (openSwipeThreadId == conv.threadId) null else openSwipeThreadId
                                },
                                onAction = onSwipeAction,
                                onMore = { onMoreAction(conv) },
                                accessibilityActionsEnabled = !selectionActive,
                                onClick = {
                                    if (openSwipeThreadId != null) {
                                        openSwipeThreadId = null
                                    } else if (targetFolder == folder) {
                                        if (selectionActive) vm.toggleSelected(conv.threadId)
                                        else onOpenThread(conv.threadId)
                                    }
                                },
                                selectionActive = selectionActive,
                                selected = conv.threadId in selectedThreads,
                                onLongClick = {
                                    openSwipeThreadId = null
                                    Haptics.longPress(rowView)
                                    vm.toggleSelected(conv.threadId)
                                },
                                canCommitSwipe = { !folderListState.isScrollInProgress },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Always-visible protection status; opens the dashboard (replaces the toolbar shield). */
@Composable
private fun ProtectionStatusCard(
    reviewWaiting: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = Motion.butterSpring(),
        label = "protection-card-scale",
    )
    androidx.compose.material3.Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(28.dp),
        color = cs.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_protection_on),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                )
                androidx.compose.animation.AnimatedContent(
                    targetState = reviewWaiting,
                    transitionSpec = {
                        (androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn()) togetherWith
                            (androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut())
                    },
                    label = "protection_counter",
                ) { count ->
                    Text(
                        if (count > 0) stringResource(R.string.home_protection_review_waiting, count)
                        else stringResource(R.string.home_protection_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_protection_dashboard),
                tint = cs.onPrimaryContainer,
            )
        }
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
                        .clip(RoundedCornerShape(20.dp))
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
                            val pinnedName = conv.contactName?.takeIf { it.isNotBlank() } ?: FriendlySenderNames.format(conv.address)
                            ContactAvatar(
                                name = pinnedName,
                                category = conv.category,
                                size = 52.dp,
                                modifier = Modifier.sharedThreadAvatar(conv.threadId),
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
                    val pinnedShortName = conv.contactName?.takeIf { it.isNotBlank() }?.split(" ")?.firstOrNull() ?: FriendlySenderNames.format(conv.address)
                    Text(
                        text = pinnedShortName,
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

@Composable
private fun Modifier.searchCascadeEntrance(
    key: Any,
    index: Int,
    alreadyAnimatedKeys: MutableSet<Any>,
    isScrolling: Boolean,
): Modifier {
    if (!Motion.animationsEnabled()) return this

    // An item animates ONLY if its key is not in the set AND its index < 8 AND the list is not scrolling;
    // add the key to the set on first composition. Items outside those conditions render at full alpha with no delay.
    val shouldAnimate = remember(key) {
        val inSet = alreadyAnimatedKeys.contains(key)
        alreadyAnimatedKeys.add(key)
        !inSet && index < 8 && !isScrolling
    }
    if (!shouldAnimate) return this

    val delayMs = (index * 35L).coerceAtLeast(0L)
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        visible = true
    }
    val animProgress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.screenSpring(),
        label = "search-cascade-$index",
    )
    return this.graphicsLayer {
        val p = animProgress.value
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
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
    val queryKey = listOf(chips, typing, labelFilter, state.activeKeywords)
    val alreadyAnimatedKeys = remember(queryKey) { mutableSetOf<Any>() }
    val searchListState = rememberLazyListState()
    val isScrolling = searchListState.isScrollInProgress

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
        itemsIndexed(SEARCH_LABELS) { index, (key, labelRes) ->
            FilterChip(
                selected = labelFilter == key,
                onClick = { vm.setLabelFilter(if (labelFilter == key) null else key) },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.searchCascadeEntrance(
                    key = "label-$key",
                    index = index,
                    alreadyAnimatedKeys = alreadyAnimatedKeys,
                    isScrolling = false,
                ),
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
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = searchListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // §8.5.3: conversations whose contact name / number matches ("mom").
            if (state.conversationMatches.isNotEmpty()) {
                item {
                    SearchSectionHeader(stringResource(R.string.home_search_section_conversations))
                }
                itemsIndexed(state.conversationMatches, key = { _, it -> "conv-${it.threadId}" }) { index, conv ->
                    val rowKey = "conv-${conv.threadId}"
                    Box(Modifier.searchCascadeEntrance(rowKey, index, alreadyAnimatedKeys, isScrolling)) {
                        ConversationMatchRow(conv, state.activeKeywords) { onOpenThread(conv.threadId) }
                    }
                }
                if (state.results.isNotEmpty()) {
                    item { SearchSectionHeader(stringResource(R.string.home_search_section_messages)) }
                }
            }
            itemsIndexed(normal, key = { _, it -> it.message.id }) { index, row ->
                val rowKey = "msg-${row.message.id}"
                Box(Modifier.searchCascadeEntrance(rowKey, index, alreadyAnimatedKeys, isScrolling)) {
                    SearchResultRow(row, state.activeKeywords, onOpenResult)
                }
            }
            if (filtered.isNotEmpty()) {
                item { SearchSectionHeader(stringResource(R.string.home_search_section_spam)) }
                itemsIndexed(filtered, key = { _, it -> it.message.id }) { index, row ->
                    val rowKey = "spam-${row.message.id}"
                    Box(Modifier.searchCascadeEntrance(rowKey, index, alreadyAnimatedKeys, isScrolling)) {
                        SearchResultRow(row, state.activeKeywords, onOpenResult)
                    }
                }
            }
        }
        JumpToTopButton(
            listState = searchListState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 24.dp),
            contentDescription = stringResource(R.string.home_back_to_top),
        )
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
        val displayName = conv.contactName?.takeIf { it.isNotBlank() } ?: FriendlySenderNames.format(conv.address)
        ContactAvatar(
            displayName, conv.category,
            modifier = Modifier.sharedThreadAvatar(conv.threadId),
            photoUri = com.messages.app.ui.common.rememberContactPhoto(conv.address),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                SearchHighlight.annotate(displayName, keywords, highlight),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    conv.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (conv.muted || conv.pinned) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (conv.muted) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = stringResource(R.string.home_muted),
                                tint = MaterialTheme.colorScheme.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (conv.pinned) {
                            Icon(
                                imageVector = SwipeIcons.PinFilled,
                                contentDescription = stringResource(R.string.home_pinned),
                                tint = MaterialTheme.colorScheme.textMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(45f),
                            )
                        }
                    }
                }
            }
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

@Composable
private fun DefaultSmsInfoCard(
    onRequestDefault: () -> Unit,
    roleRequestFailed: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    onOpenDefaultApps: () -> Unit = {},
) {
    val context = LocalContext.current
    val isRestricted = remember(roleRequestFailed) {
        roleRequestFailed || MainActivity.isRestrictedSettingsActive(context)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_default_sms_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_default_sms_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRestricted) {
                    androidx.compose.material3.TextButton(onClick = onOpenAppSettings) {
                        Text(stringResource(R.string.action_open_app_settings))
                    }
                    androidx.compose.material3.TextButton(onClick = onOpenDefaultApps) {
                        Text(stringResource(R.string.action_open_default_apps))
                    }
                }
                Button(onClick = onRequestDefault) {
                    Text(
                        if (isRestricted) stringResource(R.string.restricted_settings_retry)
                        else stringResource(R.string.home_default_sms_action),
                    )
                }
            }
        }
    }
}

/**
 * Mac WhatsApp Liquid Glass dual-side swipe-to-reveal row.
 *
 * - Swipe right (start→end) reveals two rounded floating action cards: Unread/Read (emerald) + Pin/Unpin (smoked glass).
 * - Swipe left (end→start) reveals two rounded floating action cards: More (smoked glass) + Archive (emerald).
 * - Horizontal-axis-locked drags ([SwipeGesturePolicy]) keep vertical scrolling crisp and accidental swipes impossible.
 * - Snaps open cleanly so the user taps the exact card they want (no accidental delete on swipe).
 */
@Composable
private fun SwipeableConversationRow(
    conv: ConversationEntity,
    draft: String?,
    isSwipedOpen: Boolean,
    onSwipeOpenChange: (Boolean) -> Unit,
    onAction: (String, ConversationEntity) -> Unit,
    onMore: () -> Unit,
    accessibilityActionsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: com.messages.protection.SenderBadges.Badge? = null,
    onBadgeTap: (com.messages.protection.SenderBadges.Badge) -> Unit = {},
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    canCommitSwipe: () -> Boolean = { true },
) {
    if (selectionActive) {
        Box(modifier = modifier.fillMaxWidth()) {
            ConversationRow(
                conv = conv,
                draft = draft,
                onClick = onClick,
                badge = badge,
                onBadgeTap = onBadgeTap,
                selected = selected,
                onLongClick = onLongClick,
                onAction = { action -> onAction(action, conv) },
                onMore = onMore,
                accessibilityActionsEnabled = accessibilityActionsEnabled,
            )
        }
        return
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = androidx.compose.ui.platform.LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val revealWidthDp = 168.dp
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val maxPullPx = revealWidthPx * 1.15f

    // Synchronous live drag state + spring settle Animatable: zero per-pixel recomposition or coroutine churn at 120Hz.
    val dragOffset = remember(conv.threadId) { mutableFloatStateOf(0f) }
    val settleAnim = remember(conv.threadId) { Animatable(0f) }
    var settleJob by remember(conv.threadId) { mutableStateOf<Job?>(null) }

    fun currentOffsetPx(): Float = dragOffset.floatValue + settleAnim.value

    fun settleTo(target: Float) {
        val from = currentOffsetPx()
        settleJob?.cancel()
        settleJob = scope.launch {
            settleAnim.snapTo(from)
            dragOffset.floatValue = 0f
            if (from != target) {
                settleAnim.animateTo(target, Motion.butterSpring())
            }
        }
    }

    val latestIsSwipedOpen by rememberUpdatedState(isSwipedOpen)
    val latestOnSwipeOpenChange by rememberUpdatedState(onSwipeOpenChange)
    val latestCanCommitSwipe by rememberUpdatedState(canCommitSwipe)

    DisposableEffect(conv.threadId) {
        onDispose {
            if (latestIsSwipedOpen) {
                latestOnSwipeOpenChange(false)
            }
        }
    }

    LaunchedEffect(isSwipedOpen) {
        if (!isSwipedOpen && currentOffsetPx() != 0f) {
            settleTo(0f)
        }
    }

    fun closeRow(after: (() -> Unit)? = null) {
        latestOnSwipeOpenChange(false)
        settleTo(0f)
        after?.invoke()
    }

    val startSideRevealed by remember(conv.threadId, isRtl) {
        derivedStateOf {
            val tx = dragOffset.floatValue + settleAnim.value
            if (isRtl) tx < -1f else tx > 1f
        }
    }
    val endSideRevealed by remember(conv.threadId, isRtl) {
        derivedStateOf {
            val tx = dragOffset.floatValue + settleAnim.value
            if (isRtl) tx > 1f else tx < -1f
        }
    }
    val revealProgressProvider: () -> Float = remember(conv.threadId, revealWidthPx) {
        { (kotlin.math.abs(dragOffset.floatValue + settleAnim.value) / revealWidthPx).coerceIn(0f, 1f) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        // Revealed Start-side action cards (Swipe Right in LTR): Unread/Read + Pin/Unpin
        if (startSideRevealed) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(revealWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val p = revealProgressProvider()
                        alpha = (p * 1.4f).coerceIn(0f, 1f)
                        val s = 0.90f + 0.10f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(if (isRtl) 1f else 0f, 0.5f)
                    }
                    .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isUnread = conv.unreadCount > 0
                LiquidGlassSwipeTile(
                    icon = SwipeIcons.UnreadBubble,
                    label = stringResource(if (isUnread) R.string.home_swipe_read else R.string.home_swipe_unread),
                    accentGreen = true,
                    progress = revealProgressProvider,
                    onClick = { closeRow { onAction(SwipeActions.READ, conv) } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("swipe_unread_${conv.threadId}"),
                )
                LiquidGlassSwipeTile(
                    icon = if (conv.pinned) SwipeIcons.PinOutlined else SwipeIcons.PinFilled,
                    label = stringResource(if (conv.pinned) R.string.home_unpin else R.string.home_pin),
                    accentGreen = false,
                    progress = revealProgressProvider,
                    onClick = { closeRow { onAction(SwipeActions.PIN, conv) } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("swipe_pin_${conv.threadId}"),
                )
            }
        }

        // Revealed End-side action cards (Swipe Left in LTR): More + Archive
        if (endSideRevealed) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(revealWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val p = revealProgressProvider()
                        alpha = (p * 1.4f).coerceIn(0f, 1f)
                        val s = 0.90f + 0.10f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(if (isRtl) 0f else 1f, 0.5f)
                    }
                    .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassSwipeTile(
                    icon = SwipeIcons.MoreDots,
                    label = stringResource(R.string.home_swipe_more),
                    accentGreen = false,
                    progress = revealProgressProvider,
                    onClick = { closeRow { onMore() } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("swipe_more_${conv.threadId}"),
                )
                LiquidGlassSwipeTile(
                    icon = SwipeIcons.ArchiveFilled,
                    label = stringResource(R.string.home_archive),
                    accentGreen = true,
                    progress = revealProgressProvider,
                    onClick = { closeRow { onAction(SwipeActions.ARCHIVE, conv) } },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("swipe_archive_${conv.threadId}"),
                )
            }
        }

        // Foreground conversation row sliding smoothly on spring physics
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = dragOffset.floatValue + settleAnim.value }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(conv.threadId, selectionActive, isRtl, revealWidthPx) {
                    if (selectionActive) return@pointerInput
                    val touchSlopPx = with(density) { SwipeGesturePolicy.TOUCH_SLOP_DP.dp.toPx() }
                    val verticalAbortSlopPx = with(density) { 6.dp.toPx() }
                    val verticalCancelSlopPx = with(density) { 16.dp.toPx() }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!latestCanCommitSwipe()) return@awaitEachGesture

                        var totalDx = 0f
                        var totalDy = 0f
                        var dragDx = 0f
                        var dragDy = 0f
                        var dragStarted = false
                        var settled = false
                        val initialOffset = currentOffsetPx()

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    if (dragStarted) {
                                        settled = true
                                        val released = currentOffsetPx()
                                        val threshold = revealWidthPx * SwipeGesturePolicy.COMMIT_THRESHOLD_FRACTION
                                        val target = when {
                                            released >= threshold -> revealWidthPx
                                            released <= -threshold -> -revealWidthPx
                                            else -> 0f
                                        }
                                        val willBeOpen = target != 0f
                                        if (willBeOpen && initialOffset == 0f) {
                                            Haptics.tick(view)
                                        }
                                        latestOnSwipeOpenChange(willBeOpen)
                                        settleTo(target)
                                    }
                                    break
                                }

                                if (!latestCanCommitSwipe()) {
                                    if (dragStarted) {
                                        settled = true
                                        latestOnSwipeOpenChange(false)
                                        settleTo(0f)
                                    }
                                    break
                                }

                                val dragAmountX = change.position.x - change.previousPosition.x
                                val dragAmountY = change.position.y - change.previousPosition.y
                                totalDx += dragAmountX
                                totalDy += dragAmountY

                                val absDx = kotlin.math.abs(totalDx)
                                val absDy = kotlin.math.abs(totalDy)

                                if (!dragStarted) {
                                    // Require >= 6dp vertical movement before yielding so 1px thumb jitter never kills horizontal swipes
                                    if (absDy >= verticalAbortSlopPx && (absDx == 0f || (absDy / absDx) >= SwipeGesturePolicy.MAX_DY_DX_RATIO)) {
                                        break
                                    }
                                    // Engage drag once horizontal slop (24dp) is crossed (or smaller slop if already open)
                                    val effectiveSlop = if (initialOffset != 0f) touchSlopPx * 0.5f else touchSlopPx
                                    if (absDx >= effectiveSlop && SwipeGesturePolicy.isHorizontalDominant(totalDx, totalDy)) {
                                        dragStarted = true
                                        dragDx = 0f
                                        dragDy = 0f
                                        settleJob?.cancel()
                                        settleJob = null
                                        latestOnSwipeOpenChange(true)
                                        change.consume()
                                    }
                                } else {
                                    dragDx += dragAmountX
                                    dragDy += dragAmountY
                                    val absDragDx = kotlin.math.abs(dragDx)
                                    val absDragDy = kotlin.math.abs(dragDy)
                                    if (absDragDy > verticalCancelSlopPx && (absDragDx == 0f || absDragDy / absDragDx > 1.5f)) {
                                        settled = true
                                        latestOnSwipeOpenChange(false)
                                        settleTo(0f)
                                        break
                                    }
                                    change.consume()
                                    val current = currentOffsetPx()
                                    val rawNext = current + dragAmountX
                                    // Gentle rubber-band past revealWidthPx: damp only dragAmountX, never accumulated overshoot
                                    val damped = when {
                                        dragAmountX > 0f && current >= revealWidthPx ->
                                            current + dragAmountX * 0.25f
                                        dragAmountX > 0f && rawNext > revealWidthPx ->
                                            revealWidthPx + (rawNext - revealWidthPx) * 0.25f
                                        dragAmountX < 0f && current <= -revealWidthPx ->
                                            current + dragAmountX * 0.25f
                                        dragAmountX < 0f && rawNext < -revealWidthPx ->
                                            -revealWidthPx + (rawNext + revealWidthPx) * 0.25f
                                        else -> rawNext
                                    }.coerceIn(-maxPullPx, maxPullPx)
                                    dragOffset.floatValue = damped - settleAnim.value
                                }
                            }
                        } finally {
                            if (dragStarted && !settled) {
                                latestOnSwipeOpenChange(false)
                                settleTo(0f)
                            }
                        }
                    }
                },
        ) {
            ConversationRow(
                conv = conv,
                draft = draft,
                onClick = onClick,
                badge = badge,
                onBadgeTap = onBadgeTap,
                selected = selected,
                onLongClick = onLongClick,
                onAction = { action -> onAction(action, conv) },
                onMore = onMore,
                accessibilityActionsEnabled = accessibilityActionsEnabled,
            )
        }
    }
}

/**
 * Individual rounded Liquid Glass swipe action card matching Mac WhatsApp's
 * floating pill geometry (emerald green for primary action, smoked glass for secondary).
 */
@Composable
private fun LiquidGlassSwipeTile(
    icon: ImageVector,
    label: String,
    accentGreen: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val shape = remember { RoundedCornerShape(16.dp) }
    val labelStyle = remember {
        TextStyle(
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val bgColor = if (accentGreen) {
        if (isDark) DesignTokens.Dark.Green else DesignTokens.Light.Green
    } else {
        if (isDark) DesignTokens.LiquidGlass.Dark.SwipeNeutral else DesignTokens.LiquidGlass.Light.SwipeNeutral
    }
    val contentColor = if (accentGreen) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        if (isDark) DesignTokens.LiquidGlass.Dark.SwipeNeutralContent else DesignTokens.LiquidGlass.Light.SwipeNeutralContent
    }
    val borderBrush = remember(isDark, accentGreen) {
        if (accentGreen) {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
            )
        } else {
            liquidGlassRimBrush(isDark)
        }
    }
    val sheenBrush = remember(isDark) { liquidGlassSheenBrush(isDark) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = Motion.butterSpring(),
        label = "swipe-tile-press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(bgColor, shape)
            .background(sheenBrush, shape)
            .border(BorderStroke(0.75.dp, borderBrush), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.2f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val contentAlpha = ((progress() - 0.30f) / 0.45f).coerceIn(0f, 1f)
                    val contentScale = 0.76f + 0.24f * contentAlpha
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = labelStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    onAction: (String) -> Unit = {},
    onMore: () -> Unit = {},
    accessibilityActionsEnabled: Boolean = false,
) {
    val unread = conv.unreadCount > 0
    val context = LocalContext.current
    val rowBg = if (selected) MaterialTheme.colorScheme.selectedRow else Color.Transparent

    val rowInteractionSource = remember { MutableInteractionSource() }
    val rowPressed by rowInteractionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (rowPressed) 0.98f else 1f,
        animationSpec = Motion.butterSpring(),
        label = "row-press-scale",
    )

    val displayName = remember(conv.contactName, conv.address) {
        conv.contactName?.takeIf { it.isNotBlank() } ?: FriendlySenderNames.format(conv.address)
    }
    val avatarLetter = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .background(rowBg)
            .combinedClickable(
                interactionSource = rowInteractionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.home_select_conversation) else null,
            )
            .semantics {
                if (accessibilityActionsEnabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(context.getString(R.string.home_archive)) {
                            onAction(SwipeActions.ARCHIVE); true
                        },
                        CustomAccessibilityAction(
                            context.getString(if (conv.pinned) R.string.home_unpin else R.string.home_pin),
                        ) {
                            onAction(SwipeActions.PIN); true
                        },
                        CustomAccessibilityAction(
                            context.getString(if (unread) R.string.home_mark_read else R.string.home_mark_unread),
                        ) {
                            onAction(SwipeActions.READ); true
                        },
                        CustomAccessibilityAction(context.getString(R.string.home_swipe_more)) {
                            onMore(); true
                        },
                    )
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Neutral avatar: 52dp circle, surface bg, 1px hairline border, 20sp/600 textMuted letter
        Box(
            modifier = Modifier
                .size(ROW_AVATAR)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarLetter,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.textMuted,
            )
        }

        Spacer(Modifier.width(14.dp))

        // Right column with inset hairline bottom border
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                // Top line: Name (16sp, 600 if unread, 500 if read) and Time (12sp, greenText if unread, textMuted if read)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = formatTime(conv.lastTimestamp),
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = if (unread) MaterialTheme.colorScheme.greenText else MaterialTheme.colorScheme.textMuted,
                    )
                }

                Spacer(Modifier.height(5.dp))

                // Bottom line: Preview (14sp muted, 1 line ellipsis) and trailing indicators (muted, pinned, unread badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val previewText = when {
                        conv.locked -> stringResource(R.string.conversation_locked_preview)
                        draft != null -> stringResource(R.string.home_draft_prefix, draft)
                        else -> conv.lastMessage
                    }
                    Text(
                        text = previewText,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (conv.muted || conv.pinned || unread) {
                        Spacer(Modifier.width(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (conv.muted) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsOff,
                                    contentDescription = stringResource(R.string.home_muted),
                                    tint = MaterialTheme.colorScheme.textMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            if (conv.pinned) {
                                Icon(
                                    imageVector = SwipeIcons.PinFilled,
                                    contentDescription = stringResource(R.string.home_pinned),
                                    tint = MaterialTheme.colorScheme.textMuted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(45f),
                                )
                            }
                            if (unread) {
                                Box(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .widthIn(min = 20.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = conv.unreadCount.toString(),
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = DesignTokens.Dark.OnGreen,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Inset hairline divider at the bottom of the right column
            HorizontalDivider(
                color = MaterialTheme.colorScheme.hairline,
                thickness = 1.dp,
            )
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
