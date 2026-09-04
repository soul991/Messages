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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messages.designsystem.AmbientGlassGlow
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.LiquidGlassCard
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.AppDateFormat
import com.messages.app.ui.common.ContactAvatar
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.messages.app.R

class ArchivedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val conversations: StateFlow<List<ConversationEntity>> =
        repo.db.conversations().archived()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // V2-24: `archived()` is hard-coded to space = 'NORMAL', so everything
    // this screen can show — and un-archive — lives in the normal space.
    fun unarchive(threadId: Long) = viewModelScope.launch {
        repo.db.conversations().setArchived(threadId, false, com.messages.core.db.Spaces.NORMAL)
    }
}

// V2-45: same row rule as Home, and now literally the same code — locale and
// zone resolved at render time rather than frozen at class-init.
private fun formatTime(ts: Long): String = AppDateFormat.listRowStamp(ts)

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
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        AmbientGlassGlow(Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.archived_title), fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                            stringResource(R.string.archived_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            stringResource(R.string.archived_empty_subtitle),
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
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        depth = GlassDepth.LOW,
                        onClick = { onOpenThread(conv.threadId) },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    if (conv.locked) stringResource(R.string.conversation_locked_preview) else conv.lastMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            // V2-36: read in composition, used from the coroutine.
                            val unarchived = stringResource(R.string.archived_unarchived_snackbar)
                            IconButton(onClick = {
                                vm.unarchive(conv.threadId)
                                scope.launch {
                                    snackbarHostState.showSnackbar(unarchived)
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.Unarchive,
                                    contentDescription = stringResource(R.string.archived_unarchive),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
