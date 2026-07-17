package com.messages.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDefaultSmsApp: Boolean,
    initialFolder: String?,
    onRequestDefault: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onCompose: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    LaunchedEffect(initialFolder) { if (initialFolder != null) vm.setFolder(initialFolder) }

    val folder by vm.folder.collectAsState()
    val conversations by vm.conversations.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCompose,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("New message") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            Text(
                "Messages",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (!isDefaultSmsApp) {
                DefaultAppBanner(onRequestDefault)
            }

            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { vm.search(it); searchActive = it.isNotBlank() },
                placeholder = { Text("Search messages") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Folder chips directly under the search bar (§9)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(FOLDERS) { (key, label) ->
                    val unread by vm.folderUnread(key).collectAsState(initial = 0)
                    FilterChip(
                        selected = folder == key,
                        onClick = { vm.setFolder(key); searchActive = false },
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

            if (searchActive) {
                SearchResults(searchResults, onOpenThread)
            } else if (conversations.isEmpty()) {
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

@Composable
private fun DefaultAppBanner(onRequestDefault: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onRequestDefault)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Shield, contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Make Messages your default SMS app",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Enable spam & fraud protection for every message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
                    conv.lastMessage,
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
private fun SearchResults(
    results: List<com.messages.core.db.MessageEntity>,
    onOpenThread: (Long) -> Unit,
) {
    val (filtered, normal) = results.partition { it.category == "SPAM" || it.category == "BLOCKED" }
    LazyColumn(Modifier.fillMaxSize()) {
        items(normal, key = { it.id }) { msg -> SearchRow(msg, onOpenThread) }
        if (filtered.isNotEmpty()) {
            item {
                Text(
                    "In Spam & Blocked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(filtered, key = { it.id }) { msg -> SearchRow(msg, onOpenThread) }
        }
    }
}

@Composable
private fun SearchRow(msg: com.messages.core.db.MessageEntity, onOpenThread: (Long) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(msg.threadId) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(msg.address, style = MaterialTheme.typography.titleSmall)
        Text(
            msg.body, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.outline,
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
