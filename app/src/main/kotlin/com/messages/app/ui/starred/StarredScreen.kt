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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.app.ui.common.AppDateFormat
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.messages.app.R

class StarredViewModel(app: Application, threadId: Long? = null) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    data class Row(val message: MessageEntity, val displayName: String?)

    // Phase 5 §4: null = global list (Home entry); a threadId scopes the list
    // to one conversation (ContactDetail "Starred messages" row).
    //
    // V2-28: the name lookup is a contacts-provider query. It ran once per row
    // on the collector's dispatcher (the main thread) behind a per-ViewModel map
    // that never expired, so a renamed contact stayed wrong for the life of the
    // screen. Now: one batched resolution through the shared cache, on IO.
    val rows: StateFlow<List<Row>> =
        (if (threadId == null) repo.db.messages().starred()
        else repo.db.messages().starredForThread(threadId))
            .map { list ->
                val names = repo.displayNamesFor(list.map { it.address })
                list.map { m -> Row(m, names[m.address]) }
            }
            .flowOn(Dispatchers.IO)
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

// V2-45: locale and zone read at render time, not at class-init.

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
    val rows by vm.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.starred_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                        if (threadId == null) stringResource(R.string.starred_empty_title)
                        else stringResource(R.string.starred_empty_title_thread),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.starred_empty_subtitle),
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
                                AppDateFormat.dayMonthYear(m.timestamp),
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
                            contentDescription = stringResource(R.string.starred_unstar),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
