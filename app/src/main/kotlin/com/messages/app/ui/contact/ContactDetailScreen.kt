package com.messages.app.ui.contact

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.app.ui.common.rememberContactPhoto
import com.messages.core.MessageRepository
import com.messages.core.db.UserRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.messages.app.R

class ContactDetailViewModel(
    app: Application,
    private val threadId: Long,
) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val address = MutableStateFlow("")
    val contactName = MutableStateFlow<String?>(null)
    val category = MutableStateFlow<String?>(null)
    val muted = MutableStateFlow(false)
    val locked = MutableStateFlow(false)

    /** Whether a per-conversation notification channel exists (Phase 4 item 4). */
    val hasCustomChannel = MutableStateFlow(
        com.messages.app.notify.ConversationChannels.exists(app, threadId)
    )

    /** Contacts-app lookup URI when the number is saved (View in Contacts). */
    val contactLookupKey = MutableStateFlow<String?>(null)

    // Combined with the async-loaded address so the initial evaluation isn't
    // stuck against the "" placeholder until a rules change re-triggers it.
    val blocked: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(repo.db.userRules().observeAll(), address) { rules, addr ->
            rules.any { blockRuleMatches(it, addr) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun blockRuleMatches(rule: UserRuleEntity, addr: String): Boolean =
        rule.kind == "BLOCK" && rule.target == "SENDER" &&
            rule.pattern.equals(addr, ignoreCase = true)

    init {
        viewModelScope.launch {
            val conv = repo.db.conversations().byThreadId(threadId) ?: return@launch
            address.value = conv.address
            category.value = conv.category
            muted.value = conv.muted
            locked.value = conv.locked
            val hit = withContext(Dispatchers.IO) {
                if (conv.address.contains(';')) null else repo.lookupContact(conv.address)
            }
            contactName.value = hit?.name ?: conv.contactName
            contactLookupKey.value = hit?.lookupKey
        }
    }

    // V2-24: this screen is normal-space only — the locked-space chat route
    // leaves `onOpenContact` at its no-op default, so there is no way in from
    // the vault. Naming NORMAL keeps that true if a route is ever added.
    fun setMuted(mute: Boolean) = viewModelScope.launch {
        repo.db.conversations().setMuted(threadId, mute, com.messages.core.db.Spaces.NORMAL)
        muted.value = mute
    }

    fun setLocked(lock: Boolean) = viewModelScope.launch {
        repo.db.conversations().setLocked(threadId, lock)
        locked.value = lock
        // R-03: the unread widget excludes locked rows at the DAO layer, so push
        // a refresh to drop any already-rendered preview for this thread.
        com.messages.app.widget.WidgetUpdater.requestUpdate(getApplication())
        if (lock) {
            com.messages.app.shortcut.ConversationShortcuts.remove(getApplication(), threadId)
            // The channel name would leak the sender into system settings.
            com.messages.app.notify.ConversationChannels.remove(getApplication(), threadId)
            hasCustomChannel.value = false
        }
    }

    /** Creates the per-conversation channel and returns its id for the system sheet. */
    fun ensureCustomChannel(): String {
        val id = com.messages.app.notify.ConversationChannels.ensure(
            getApplication(), threadId,
            contactName.value ?: address.value.ifBlank { "Conversation" },
        )
        hasCustomChannel.value = true
        return id
    }

    fun removeCustomChannel() {
        com.messages.app.notify.ConversationChannels.remove(getApplication(), threadId)
        hasCustomChannel.value = false
    }

    fun setBlocked(block: Boolean) = viewModelScope.launch {
        val dao = repo.db.userRules()
        if (block) {
            if (!blocked.value) {
                val position = (dao.all().maxOfOrNull { it.position } ?: -1) + 1
                dao.insert(
                    UserRuleEntity(
                        position = position, kind = "BLOCK",
                        target = "SENDER", pattern = address.value,
                    )
                )
            }
        } else {
            dao.all().filter { blockRuleMatches(it, address.value) }.forEach { dao.delete(it.id) }
        }
    }
}

class ContactDetailViewModelFactory(
    private val app: Application,
    private val threadId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ContactDetailViewModel(app, threadId) as T
}

/**
 * Contact detail page, opened from the chat top bar (Phase 5 §4, REFS §6b):
 * photo hero, circular tonal action trio (message / call / save), the
 * Starred-messages and Search-in-conversation rows, and the per-conversation
 * controls (mute, lock, tone, block) in the tonal-icon row language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    threadId: Long,
    onBack: () -> Unit,
    onMessage: () -> Unit = onBack,
    onOpenStarred: () -> Unit = {},
    onSearchInChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: ContactDetailViewModel = viewModel(
        factory = ContactDetailViewModelFactory(context.applicationContext as Application, threadId)
    )
    val address by vm.address.collectAsStateWithLifecycle()
    val contactName by vm.contactName.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val lookupKey by vm.contactLookupKey.collectAsStateWithLifecycle()
    val hasCustomChannel by vm.hasCustomChannel.collectAsStateWithLifecycle()

    val isGroup = address.contains(';')
    val saved = lookupKey != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Photo hero (REFS §6b) ----
            Spacer(Modifier.height(8.dp))
            ContactAvatar(
                contactName ?: address,
                category,
                size = 120.dp,
                textStyle = MaterialTheme.typography.displayMedium,
                photoUri = if (isGroup) null else rememberContactPhoto(address.ifBlank { null }),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                contactName ?: address.ifBlank { stringResource(R.string.contact_unnamed) },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (contactName != null || isGroup) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isGroup) address.replace(";", ", ") else address,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            // ---- Circular tonal action trio (REFS §6b) ----
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroAction(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    label = stringResource(R.string.contact_action_message),
                    onClick = onMessage,
                )
                // Numeric senders can be called; alphanumeric headers can't.
                if (!isGroup && address.any { it.isDigit() } && address.none { it.isLetter() }) {
                    HeroAction(
                        icon = Icons.Outlined.Call,
                        label = stringResource(R.string.contact_action_call),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                                )
                            }
                        },
                    )
                }
                if (!isGroup && address.isNotBlank()) {
                    if (saved) {
                        HeroAction(
                            icon = Icons.Outlined.Person,
                            label = stringResource(R.string.contact_action_open_contact),
                            onClick = {
                                runCatching {
                                    val uri = Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey,
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            },
                        )
                    } else {
                        HeroAction(
                            icon = Icons.Outlined.PersonAddAlt,
                            label = stringResource(R.string.contact_action_save),
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                            putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                        }
                                    )
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- Feature rows (REFS §6b: colored-icon rows) ----
            DetailRow(
                icon = Icons.Outlined.StarOutline,
                title = stringResource(R.string.starred_title),
                subtitle = stringResource(R.string.contact_starred_subtitle),
                onClick = onOpenStarred,
            )
            DetailRow(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.contact_search_title),
                subtitle = stringResource(R.string.contact_search_subtitle),
                onClick = onSearchInChat,
            )

            SectionGap()

            DetailRow(
                icon = Icons.Outlined.NotificationsOff,
                title = stringResource(R.string.contact_mute_title),
                subtitle = stringResource(R.string.contact_mute_subtitle),
                onClick = { vm.setMuted(!muted) },
                trailing = { Switch(checked = muted, onCheckedChange = { vm.setMuted(it) }) },
            )
            // The old per-chat biometric lock toggle is gone — locking now
            // means the secret locked space ("Lock chat" in the chat's ⋮
            // menu). This row only points there; legacy locked=1 rows keep
            // their auth gate until the first secret-space setup migrates them.
            DetailRow(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.contact_lock_title),
                subtitle = stringResource(R.string.contact_lock_subtitle),
                onClick = {},
            )

            // Per-conversation tone (Phase 4 item 4): a dedicated notification
            // channel, customized through the system sheet. Locked chats can't
            // have one — the channel name would surface in system settings.
            if (!locked) {
                DetailRow(
                    icon = Icons.Outlined.MusicNote,
                    title = stringResource(R.string.contact_tone_title),
                    subtitle = if (hasCustomChannel) {
                        stringResource(R.string.contact_tone_subtitle_custom)
                    } else {
                        stringResource(R.string.contact_tone_subtitle_default)
                    },
                    onClick = {
                        val channelId = vm.ensureCustomChannel()
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
                                }
                            )
                        }
                    },
                )
                if (hasCustomChannel) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.removeCustomChannel() }
                            // Aligns with row titles: 20 + 40dp slot + 16 gap.
                            .padding(start = 76.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
                    ) {
                        Text(
                            stringResource(R.string.contact_tone_reset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            SectionGap()

            DetailRow(
                icon = Icons.Outlined.Block,
                title = if (blocked) stringResource(R.string.contact_unblock_title)
                else stringResource(R.string.contact_block_title),
                subtitle = if (blocked) stringResource(R.string.contact_unblock_subtitle)
                else stringResource(R.string.contact_block_subtitle),
                onClick = { vm.setBlocked(!blocked) },
                iconContainer = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                titleColor = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Circular tonal action with a label beneath (REFS §6b trio). */
@Composable
private fun HeroAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(60.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionGap() {
    HorizontalDivider(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Detail row: 40dp tonal icon container + title/subtitle + optional trailing. */
@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
