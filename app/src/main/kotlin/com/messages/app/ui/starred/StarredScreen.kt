package com.messages.app.ui.starred

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StarredViewModel(app: Application, threadId: Long? = null) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)
    private val nameCache = HashMap<String, String?>()

    data class Row(val message: MessageEntity, val displayName: String?)

    // Phase 5 §4: null = global list (Home entry); a threadId scopes the list
    // to one conversation (ContactDetail "Starred messages" row).
    val rows: StateFlow<List<Row>> =
        (if (threadId == null) repo.db.messages().starred()
        else repo.db.messages().starredForThread(threadId))
            .map { list ->
                list.map { m ->
                    Row(m, nameCache.getOrPut(m.address) { repo.displayNameFor(m.address) })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unstar(id: Long) = viewModelScope.launch { repo.db.messages().setStarred(id, false) }
}

class StarredViewModelFactory(
    private val app: Application,
    private val threadId: Long?,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        StarredViewModel(app, threadId) as T
}

private val STARRED_FMT = SimpleDateFormat("d MMM yyyy", Locale.US)

/** Starred-messages screen (Phase 4 item 11); rows open the chat at the message. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    onBack: () -> Unit,
    onOpenMessage: (threadId: Long, messageId: Long) -> Unit,
    threadId: Long? = null,
    vm: StarredViewModel = viewModel(
        key = "starred-${threadId ?: "all"}",
        factory = StarredViewModelFactory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as Application,
            threadId,
        ),
    ),
) {
    val rows by vm.rows.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starred messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Star, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        if (threadId == null) "No starred messages"
                        else "No starred messages in this conversation",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Long-press a message and tap Star to keep it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(rows, key = { it.message.id }) { row ->
                val m = row.message
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMessage(m.threadId, m.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        row.displayName ?: m.address,
                        m.category,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (row.displayName ?: m.address) +
                                    if (m.isOutgoing) " · You" else "",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                STARRED_FMT.format(Date(m.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            m.body.ifBlank { "[media message]" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.unstar(m.id) }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Unstar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
