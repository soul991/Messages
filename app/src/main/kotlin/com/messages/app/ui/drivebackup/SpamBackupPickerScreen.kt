package com.messages.app.ui.drivebackup

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messages.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.drive.DriveBackup
import com.messages.app.ui.search.SearchHighlight
import com.messages.app.ui.common.AppDateFormat
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpamBackupPickerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    private val allSpam = MutableStateFlow<List<MessageEntity>>(emptyList())
    val query = MutableStateFlow("")
    val selected = MutableStateFlow(DriveBackup.customSpamIds(app))

    /** Search-first UX (§8.3): the same live filtering as global search. */
    val shown: StateFlow<List<MessageEntity>> =
        combine(allSpam, query) { spam, q ->
            val t = q.trim()
            if (t.length < 3) spam
            else spam.filter {
                it.body.contains(t, ignoreCase = true) ||
                    it.address.contains(t, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            allSpam.value = repo.db.messages().allMessages()
                // V2-6: NORMAL only. Locked rows never travel in the plaintext
                // payload this picker shapes, so ticking one could never have
                // done anything — and their bodies are sealed, so they would
                // have rendered here as base64.
                .filter {
                    it.category == "SPAM" && !it.trashed &&
                        it.space == com.messages.core.db.Spaces.NORMAL
                }
                .sortedByDescending { it.timestamp }
        }
    }

    fun spamTotal() = allSpam.value.size

    fun toggle(id: Long) {
        selected.value = selected.value.let { if (id in it) it - id else it + id }
        persist()
    }

    fun selectAllShown() {
        selected.value = selected.value + shown.value.map { it.id }
        persist()
    }

    fun clearSelection() {
        selected.value = emptySet()
        persist()
    }

    private fun persist() = DriveBackup.setCustomSpamIds(getApplication(), selected.value)
}

// V2-45: locale and zone read at render time, not at class-init.

/**
 * §8.3 Custom spam-backup picker: search-first multi-select over the Spam
 * folder with a running "n of total will be backed up" count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamBackupPickerScreen(
    onBack: () -> Unit,
    vm: SpamBackupPickerViewModel = viewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val shown by vm.shown.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val terms = if (query.trim().length >= 3) listOf(query.trim()) else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_spam_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.action_clear)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                pluralStringResource(
                    R.plurals.drive_spam_picker_count,
                    selected.size,
                    selected.size,
                    vm.spamTotal(),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text(stringResource(R.string.drive_spam_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (shown.isNotEmpty()) {
                TextButton(
                    onClick = { vm.selectAllShown() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        if (terms.isEmpty()) {
                            stringResource(R.string.drive_spam_picker_select_all)
                        } else {
                            pluralStringResource(
                                R.plurals.drive_spam_picker_select_all_results,
                                shown.size,
                                shown.size,
                            )
                        },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { msg ->
                    val highlight = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        background = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggle(msg.id) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = msg.id in selected,
                            onCheckedChange = { vm.toggle(msg.id) },
                        )
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    SearchHighlight.annotate(msg.address, terms, highlight),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    AppDateFormat.dayMonth(msg.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Text(
                                SearchHighlight.annotate(
                                    SearchHighlight.snippet(msg.body, terms), terms, highlight,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
