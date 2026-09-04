package com.messages.app.ui.compose

import android.app.Application
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.designsystem.AmbientGlassGlow
import com.messages.designsystem.LocalDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ListSkeleton
import com.messages.app.ui.common.rememberLoadingGrace
import com.messages.core.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.messages.app.R

data class PickerContact(val name: String, val number: String, val label: String)

class NewMessageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    /**
     * V2-42: was `List<PickerContact>` with `emptyList()` standing in for every
     * way the read could go wrong. [ContactsLoad] gives the failures somewhere
     * to go, so the screen can tell the user which one happened.
     */
    private val allContacts = MutableStateFlow<ContactsLoad>(ContactsLoad.Loading)
    val query = MutableStateFlow("")

    val contacts: StateFlow<ContactsLoad> =
        combine(allContacts, query) { state, q ->
            // Filtering only ever narrows Ready; the other states have no rows
            // and must survive the combine intact.
            if (state is ContactsLoad.Ready) ContactsLoad.Ready(filterContacts(state.contacts, q))
            else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsLoad.Loading)

    /** In flight, so a double-tap on "Try again" cannot stack two provider reads. */
    private var loadJob: kotlinx.coroutines.Job? = null

    init { refresh() }

    /**
     * (Re)read the address book. Called on open, from the failure state's retry,
     * and after the permission is granted — the last one matters because the
     * process is not restarted on a grant, so without it the picker would keep
     * showing the denial it observed at launch.
     */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            allContacts.value = ContactsLoad.Loading
            allContacts.value = withContext(Dispatchers.IO) { loadContacts() }
        }
    }

    private fun loadContacts(): ContactsLoad {
        val ctx = getApplication<Application>()
        // Checked before querying rather than inferred from the result: a denied
        // read and an empty address book are indistinguishable at the cursor.
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return ContactsLoad.PermissionDenied
        }
        return try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            // A null cursor is the package-visibility failure the manifest
            // documents: the provider refuses silently, with the permission
            // granted, and returns nothing rather than throwing. Reporting it
            // as an empty address book is precisely the bug in this finding.
            ) ?: return ContactsLoad.Failed("Contacts provider returned no cursor")
            cursor.use { c ->
                // Dedupe: the Phone table repeats a number per raw contact / account.
                val seen = LinkedHashMap<String, PickerContact>()
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        ctx.resources, c.getInt(2), c.getString(3),
                    ).toString()
                    val key = name + "|" + number.filter { it.isDigit() || it == '+' }
                    if (key !in seen) seen[key] = PickerContact(name, number, label)
                }
                ContactsLoad.Ready(seen.values.toList())
            }
        } catch (_: SecurityException) {
            // Revoked between the check above and the query, or an OEM provider
            // enforcing its own gate. Same recovery as a plain denial.
            ContactsLoad.PermissionDenied
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            android.util.Log.e("NewMessageViewModel", "contacts query failed", t)
            ContactsLoad.Failed(t::class.java.simpleName)
        }
    }

    /** Resolve (or create) the system thread for the picked recipient. When
     *  composing from inside the secret space, the conversation is created
     *  directly as LOCKED — the routing rule applies from this very moment
     *  (future incoming from the address files locked, never normal). */
    fun openThread(
        address: String,
        space: String = com.messages.core.db.Spaces.NORMAL,
        onResult: (Long) -> Unit,
    ) = viewModelScope.launch {
        val threadId = repo.threadIdFor(address)
        if (space == com.messages.core.db.Spaces.LOCKED) {
            try {
                repo.createLockedConversation(threadId, address)
            } catch (_: com.messages.core.secret.LockedWriteBlockedException) {
                // V2-6b: the locked space refuses new conversations while its
                // content key is down. A dead tap would look broken; say why.
                android.widget.Toast.makeText(
                    getApplication(),
                    getApplication<android.app.Application>()
                        .getString(R.string.secret_seal_blocked_write),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
        }
        onResult(threadId)
    }

    // ---- Group compose (§8.1): staged recipients before opening the thread ----

    val selected = MutableStateFlow<List<PickerContact>>(emptyList())

    fun addRecipient(contact: PickerContact) {
        val key = contact.number.filter { it.isDigit() || it == '+' }
        if (selected.value.none { it.number.filter { ch -> ch.isDigit() || ch == '+' } == key }) {
            selected.value = selected.value + contact
        }
        query.value = ""
    }

    fun removeRecipient(contact: PickerContact) {
        selected.value = selected.value - contact
    }

    /** ';'-joined group address (repo convention) or the single number. */
    fun groupAddress(): String = selected.value.joinToString(";") { it.number }
}

/** Whether the query itself can be sent to as a raw phone number. */
private fun isDialable(q: String): Boolean =
    q.isNotBlank() && q.count { it.isDigit() } >= 3 &&
        q.all { it.isDigit() || it in "+*# -()" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onOpenThread: (threadId: Long, address: String) -> Unit,
    /** Secret space: LOCKED creates the picked conversation directly locked. */
    space: String = com.messages.core.db.Spaces.NORMAL,
    vm: NewMessageViewModel = viewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val contactsLoad by vm.contacts.collectAsStateWithLifecycle()
    val contacts = contactsLoad.rows()
    val pastGrace = rememberLoadingGrace(contactsLoad is ContactsLoad.Loading)
    val selected by vm.selected.collectAsStateWithLifecycle()
    var groupMode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun open(address: String) =
        vm.openThread(address, space) { threadId -> onOpenThread(threadId, address) }

    // 1:1 tap opens the thread directly; in group mode taps stage recipients.
    fun pick(contact: PickerContact) {
        if (groupMode) vm.addRecipient(contact) else open(contact.number)
    }

    val isDark = LocalDarkTheme.current

    Box(Modifier.fillMaxSize()) {
        AmbientGlassGlow(Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (groupMode) {
                                stringResource(R.string.compose_group_title, selected.size)
                            } else {
                                stringResource(R.string.compose_title)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        if (groupMode && selected.size >= 2) {
                            IconButton(onClick = { open(vm.groupAddress()) }) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.compose_start_conversation),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Staged group recipients
                if (selected.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        selected.forEach { contact ->
                            InputChip(
                                selected = false,
                                onClick = { vm.removeRecipient(contact) },
                                label = { Text(contact.name) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.compose_remove_recipient),
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x33262936) else Color(0x33E2E7F0))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.8f),
                                    if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.3f),
                                )
                            ),
                            CircleShape,
                        )
                ) {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.compose_recipient_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    if (!groupMode && query.isBlank()) {
                        item(key = "start-group") {
                            StartGroupRow(onClick = { groupMode = true })
                        }
                    }
                    if (isDialable(query)) {
                        item(key = "send-to-number") {
                            val number = query.trim()
                            SendToNumberRow(
                                number,
                                onClick = {
                                    if (groupMode) vm.addRecipient(PickerContact(number, number, ""))
                                    else open(number)
                                },
                            )
                        }
                    }
                    items(contacts, key = { it.name + "|" + it.number }) { contact ->
                        ContactRow(contact, onClick = { pick(contact) })
                    }
                    // V2-42. Failure notices render whether or not the query is
                    // dialable — a broken address book is worth saying out loud —
                    // while the send-to-number row above keeps manual entry working
                    // through all of them.
                    val notice = contactsNotice(
                        contactsLoad, query, contacts.size, pastGrace, isDialable(query),
                    )
                    when (notice) {
                        ContactsNotice.NONE -> Unit
                        ContactsNotice.LOADING -> item(key = "contacts-loading") {
                            ListSkeleton(stringResource(R.string.compose_loading_contacts), rows = 6, avatar = 44.dp)
                        }
                        ContactsNotice.PERMISSION -> item(key = "contacts-permission") {
                            ContactsPermissionNotice(onGranted = vm::refresh)
                        }
                        ContactsNotice.FAILED -> item(key = "contacts-failed") {
                            ContactsFailedNotice(
                                reason = (contactsLoad as? ContactsLoad.Failed)?.reason,
                                onRetry = vm::refresh,
                            )
                        }
                        ContactsNotice.NO_CONTACTS -> item(key = "contacts-empty") {
                            PickerHint(stringResource(R.string.compose_no_contacts))
                        }
                        ContactsNotice.NO_MATCHES -> item(key = "contacts-no-matches") {
                            PickerHint(stringResource(R.string.compose_no_matches))
                        }
                    }
                }
            }
        }
    }
}

/** Plain informational line under the list — a real answer, not a failure. */
@Composable
private fun PickerHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(20.dp),
    )
}

/**
 * V2-42. Shown when READ_CONTACTS is not granted.
 *
 * Worded as a capability the app is missing rather than a list that is empty,
 * because that is the difference the finding is about: the user's contacts are
 * not gone, this app just cannot see them. "Allow" requests; once the system
 * has stopped showing the dialog, the only route left is the settings page, so
 * that button appears then rather than cluttering the first pass.
 */
@Composable
private fun ContactsPermissionNotice(onGranted: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var permanentlyDenied by remember { mutableStateOf(false) }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                )
            )
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) {
            com.messages.core.contacts.ContactSync.ensureObserver(context)
            com.messages.core.contacts.ContactSync.refreshOnForeground(context)
            // The process is not restarted on a grant, so the picker has to be
            // told to read again — otherwise it keeps showing this card.
            onGranted()
        } else {
            // "Don't ask again": the dialog will never appear again, so stop
            // offering a button that now does nothing.
            permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.READ_CONTACTS,
                )
        }
    }

    NoticeCard {
        Text(
            stringResource(R.string.compose_contacts_unavailable),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.compose_contacts_denied_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(
                onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) },
            ) { Text(stringResource(R.string.compose_allow_contacts)) }
            if (permanentlyDenied) {
                androidx.compose.material3.TextButton(onClick = ::openAppSettings) {
                    Text(stringResource(R.string.compose_app_settings))
                }
            }
        }
    }
}

/**
 * V2-42. Shown when the provider was asked and did not answer.
 *
 * Distinct from the permission card because the recovery is distinct: nothing
 * for the user to authorise, just a read that failed and can be repeated.
 */
@Composable
private fun ContactsFailedNotice(reason: String?, onRetry: () -> Unit) {
    NoticeCard {
        Text(
            stringResource(R.string.compose_contacts_failed),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.compose_contacts_failed_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!reason.isNullOrBlank()) {
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
    }
}

@Composable
private fun NoticeCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StartGroupRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Group, contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.compose_new_group),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SendToNumberRow(number: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(R.string.compose_send_to_number, number),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.compose_not_in_contacts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ContactRow(contact: PickerContact, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initial = contact.name.firstOrNull()?.uppercaseChar()?.toString()
            if (initial != null) {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(
                    Icons.Filled.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                stringResource(
                    R.string.compose_contact_number_label, contact.number, contact.label,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
