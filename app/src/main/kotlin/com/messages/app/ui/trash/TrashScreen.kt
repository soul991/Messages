package com.messages.app.ui.trash

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.search.SearchHighlight
import com.messages.app.ui.common.AppDateFormat
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.core.trash.TrashRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.messages.app.R

class TrashViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val trashed: StateFlow<List<MessageEntity>> =
        repo.db.messages().trashedMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * V2-36: a resource id, not a sentence. Resolving it in composition means
     * the snackbar follows a locale change even if the flow emitted before it,
     * and it keeps the last piece of user-visible English out of the ViewModel.
     */
    val lastAction = MutableStateFlow<Int?>(null)

    fun restore(messageId: Long) = viewModelScope.launch {
        repo.restoreFromTrash(messageId)
        lastAction.value = R.string.trash_restored_snackbar
    }

    fun deleteForever(messageId: Long) = viewModelScope.launch {
        repo.deleteForever(messageId)
        lastAction.value = R.string.trash_deleted_forever_snackbar
    }

    fun emptyTrash() = viewModelScope.launch {
        trashed.value.forEach { repo.deleteForever(it.id) }
        lastAction.value = R.string.trash_emptied_snackbar
    }

    fun clearLastAction() {
        lastAction.value = null
    }
}

/**
 * Trash folder (§6.4): user-deleted messages, browsable + restorable for
 * [TrashRetention.RETENTION_DAYS] days; per-item Delete forever and a
 * guarded Empty-trash action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    vm: TrashViewModel = viewModel(),
) {
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val lastAction by vm.lastAction.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmForever by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    // Per-folder search (§8.5): 3-char threshold, live filter + highlighting.
    var query by remember { mutableStateOf("") }
    val terms = remember(query) {
        if (query.trim().length >= 3) query.trim().split(Regex("\\s+")) else emptyList()
    }
    val shown = remember(trashed, terms) {
        if (terms.isEmpty()) trashed
        else trashed.filter { m ->
            terms.any { t ->
                m.body.contains(t, ignoreCase = true) || m.address.contains(t, ignoreCase = true)
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(lastAction) {
        lastAction?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            vm.clearLastAction()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_action)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                // V2-36: the window comes from the constant the purge worker
                // actually enforces, so the sentence cannot drift from the
                // behaviour it describes.
                pluralStringResource(
                    R.plurals.trash_retention_notice,
                    TrashRetention.RETENTION_DAYS,
                    TrashRetention.RETENTION_DAYS,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (trashed.isNotEmpty()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.trash_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (trashed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Delete, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.trash_is_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { msg ->
                        TrashRow(
                            msg = msg,
                            terms = terms,
                            onRestore = { vm.restore(msg.id) },
                            onDeleteForever = { confirmForever = msg },
                        )
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.trash_no_match),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    confirmForever?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmForever = null },
            title = { Text(stringResource(R.string.trash_delete_forever_title)) },
            text = { Text(stringResource(R.string.trash_delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForever(msg.id)
                    confirmForever = null
                }) { Text(stringResource(R.string.trash_delete_forever_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForever = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.trash_empty_confirm_body, trashed.size, trashed.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.emptyTrash()
                    confirmEmpty = false
                }) { Text(stringResource(R.string.trash_empty_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// V2-45: locale and zone read at render time, not at class-init.

@Composable
private fun TrashRow(
    msg: MessageEntity,
    terms: List<String> = emptyList(),
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val daysLeft = TrashRetention.purgeCountdownDays(msg.trashedAt)
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.primaryContainer,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // V2-36: formatted whole rather than glued together — a
                    // recipient marker is not a prefix in every language.
                    SearchHighlight.annotate(
                        if (msg.isOutgoing) {
                            stringResource(R.string.trash_row_outgoing_to, msg.address)
                        } else {
                            msg.address
                        },
                        terms,
                        highlight,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    AppDateFormat.dayMonthYearClock(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                if (msg.body.isBlank()) androidx.compose.ui.text.AnnotatedString("(media message)")
                else SearchHighlight.annotate(SearchHighlight.snippet(msg.body, terms), terms, highlight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (daysLeft > 0) {
                    pluralStringResource(R.plurals.trash_purge_countdown, daysLeft, daysLeft)
                } else {
                    stringResource(R.string.trash_purge_imminent)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Filled.RestoreFromTrash, contentDescription = stringResource(R.string.trash_restore),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDeleteForever) {
            Icon(
                Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever_action),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
