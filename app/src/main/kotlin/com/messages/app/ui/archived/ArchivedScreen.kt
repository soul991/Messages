package com.messages.app.ui.archived

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchivedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val conversations: StateFlow<List<ConversationEntity>> =
        repo.db.conversations().archived()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unarchive(threadId: Long) = viewModelScope.launch {
        repo.db.conversations().setArchived(threadId, false)
    }
}

// Same composition-only formatter policy as HomeScreen's row time.
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

private val ROW_AVATAR = 54.dp
private val ROW_GAP = 16.dp

/** Archived conversations (Home overflow → Archived); rows open the chat, trailing action unarchives. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    vm: ArchivedViewModel = viewModel(),
) {
    val conversations by vm.conversations.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Archive, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "No archived conversations",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Swipe a conversation on Home to archive it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(conversations, key = { it.threadId }) { conv ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenThread(conv.threadId) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        conv.contactName ?: conv.address,
                        conv.category,
                        size = ROW_AVATAR,
                        photoUri = if (conv.locked) null
                        else com.messages.app.ui.common.rememberContactPhoto(conv.address),
                    )
                    Spacer(Modifier.width(ROW_GAP))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conv.contactName ?: conv.address,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatTime(conv.lastTimestamp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (conv.locked) "🔒 Locked conversation" else conv.lastMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        vm.unarchive(conv.threadId)
                        scope.launch {
                            snackbarHostState.showSnackbar("Moved back to Home")
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Unarchive,
                            contentDescription = "Unarchive",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
