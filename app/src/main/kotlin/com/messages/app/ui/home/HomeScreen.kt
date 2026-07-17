package com.messages.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.search.SearchHighlight
import com.messages.core.db.ConversationEntity
import com.messages.designsystem.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val folder by vm.folder.collectAsState()
    val conversations by vm.conversations.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val typing by vm.typing.collectAsState()
    val chips by vm.chips.collectAsState()
    val labelFilter by vm.labelFilter.collectAsState()
    val searchState by vm.searchState.collectAsState()

    fun exitSearch() {
        searchActive = false
        vm.clearSearch()
    }
    BackHandler(enabled = searchActive) { exitSearch() }

    Scaffold(
        floatingActionButton = {
            // §8.4: no messaging features until the default-SMS role is granted.
            if (!searchActive && isDefaultSmsApp) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("New message") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            if (!searchActive) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDashboard) {
                        Icon(Icons.Filled.Shield, contentDescription = "Protection dashboard")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }

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
                if (searchActive) {
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
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) searchActive = true },
                )
            }

            if (searchActive) {
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
            } else {
                // Folder chips directly under the search bar (§9)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(FOLDERS) { (key, label) ->
                        val unread by vm.folderUnread(key).collectAsState(initial = 0)
                        FilterChip(
                            selected = folder == key,
                            onClick = { vm.setFolder(key) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label)
                                    if (unread > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge { Text("$unread") }
                                    }
                                }
                            },
                        )
                    }
                }

                if (conversations.isEmpty()) {
                    EmptyFolderState(folder)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(conversations, key = { it.threadId }) { conv ->
                            ConversationRow(conv, onClick = { onOpenThread(conv.threadId) })
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
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (saved.isNotEmpty()) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
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
                color = MaterialTheme.colorScheme.outline,
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
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.conversationMatches, key = { "conv-${it.threadId}" }) { conv ->
                ConversationMatchRow(conv, state.activeKeywords) { onOpenThread(conv.threadId) }
            }
            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        items(normal, key = { it.message.id }) { row ->
            SearchResultRow(row, state.activeKeywords, onOpenResult)
        }
        if (filtered.isNotEmpty()) {
            item {
                Text(
                    "In Spam & Blocked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(filtered, key = { it.message.id }) { row ->
                SearchResultRow(row, state.activeKeywords, onOpenResult)
            }
        }
    }
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
        Avatar(conv.contactName ?: conv.address, conv.category)
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
        Avatar(row.displayName ?: msg.address, msg.category)
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
                    color = MaterialTheme.colorScheme.outline,
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
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "To see your conversations and turn on spam, scam & fraud " +
                    "protection, Messages needs to be your SMS app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRequestDefault) {
                Text("Set as default SMS app")
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: ConversationEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conv.contactName ?: conv.address, conv.category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.contactName ?: conv.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conv.pinned) {
                    Icon(
                        Icons.Outlined.PushPin, contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(conv.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (conv.locked) "🔒 Locked conversation" else conv.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conv.unreadCount > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conv.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("${conv.unreadCount}") }
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, category: String) {
    val bg = when (category) {
        "SPAM" -> CategoryColors.FraudContainer
        "PROMOTIONS" -> CategoryColors.PromoContainer
        "TRANSACTIONS" -> CategoryColors.ProtectedContainer
        "REVIEW" -> CategoryColors.ReviewContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fg = when (category) {
        "SPAM" -> CategoryColors.Fraud
        "PROMOTIONS" -> CategoryColors.Promo
        "TRANSACTIONS" -> CategoryColors.Protected
        "REVIEW" -> CategoryColors.Review
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = fg,
        )
    }
}

@Composable
private fun EmptyFolderState(folder: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Archive, contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (folder) {
                    "SPAM" -> "No spam today — enjoy the silence"
                    "PROMOTIONS" -> "No promotions right now"
                    "REVIEW" -> "Nothing needs your review"
                    "BLOCKED" -> "No blocked messages"
                    "TRANSACTIONS" -> "No transactions yet"
                    else -> "No messages yet"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
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
