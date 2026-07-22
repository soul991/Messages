package com.messages.app.ui.contact

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    fun setMuted(mute: Boolean) = viewModelScope.launch {
        repo.db.conversations().setMuted(threadId, mute)
        muted.value = mute
    }

    fun setLocked(lock: Boolean) = viewModelScope.launch {
        repo.db.conversations().setLocked(threadId, lock)
        locked.value = lock
        if (lock) {
            com.messages.app.shortcut.ConversationShortcuts.remove(getApplication(), threadId)
        }
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
 * Google-Messages-style contact detail page, opened from the chat top bar:
 * identity (photo/name/number), Call / Add-to-contacts / View-in-Contacts
 * intents, and the per-conversation controls (mute, lock, block). Custom
 * notification tone lands with per-conversation channels (Phase 4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(threadId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ContactDetailViewModel = viewModel(
        factory = ContactDetailViewModelFactory(context.applicationContext as Application, threadId)
    )
    val address by vm.address.collectAsState()
    val contactName by vm.contactName.collectAsState()
    val category by vm.category.collectAsState()
    val muted by vm.muted.collectAsState()
    val locked by vm.locked.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val lookupKey by vm.contactLookupKey.collectAsState()

    val isGroup = address.contains(';')
    val saved = lookupKey != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Spacer(Modifier.height(24.dp))
            ContactAvatar(
                contactName ?: address,
                category,
                size = 96.dp,
                textStyle = MaterialTheme.typography.displaySmall,
                photoUri = if (isGroup) null else rememberContactPhoto(address.ifBlank { null }),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                contactName ?: address.ifBlank { "Conversation" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (contactName != null || isGroup) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isGroup) address.replace(";", ", ") else address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))

            if (!isGroup && address.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Numeric senders can be called; alphanumeric headers can't.
                    if (address.any { it.isDigit() } && address.none { it.isLetter() }) {
                        FilledTonalButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Call")
                        }
                    }
                    if (saved) {
                        FilledTonalButton(onClick = {
                            runCatching {
                                val uri = Uri.withAppendedPath(
                                    ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey,
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }) {
                            Icon(Icons.Filled.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("View contact")
                        }
                    } else {
                        FilledTonalButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, address)
                                    }
                                )
                            }
                        }) {
                            Icon(Icons.Filled.PersonAddAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add contact")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            HorizontalDivider()

            DetailSwitchRow(
                title = "Mute notifications",
                subtitle = "No alerts for this conversation.",
                checked = muted,
                onChange = { vm.setMuted(it) },
            )
            DetailSwitchRow(
                title = "Lock conversation",
                subtitle = "Require unlock to open; previews hidden.",
                checked = locked,
                onChange = { vm.setLocked(it) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setBlocked(!blocked) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Block, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (blocked) "Unblock sender" else "Block sender",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        if (blocked) "Messages will arrive normally again."
                        else "Future messages land in Blocked, silently. Nothing is deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
