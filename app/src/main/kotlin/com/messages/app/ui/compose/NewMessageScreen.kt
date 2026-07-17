package com.messages.app.ui.compose

import android.app.Application
import android.provider.ContactsContract
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.core.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickerContact(val name: String, val number: String, val label: String)

class NewMessageViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)
    private val allContacts = MutableStateFlow<List<PickerContact>>(emptyList())
    val query = MutableStateFlow("")

    val contacts: StateFlow<List<PickerContact>> =
        combine(allContacts, query) { list, q ->
            if (q.isBlank()) list
            else {
                val qDigits = q.filter { it.isDigit() || it == '+' }
                list.filter { c ->
                    c.name.contains(q, ignoreCase = true) ||
                        (qDigits.length >= 3 &&
                            c.number.filter { it.isDigit() || it == '+' }.contains(qDigits))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { allContacts.value = withContext(Dispatchers.IO) { loadContacts() } }
    }

    private fun loadContacts(): List<PickerContact> = try {
        val ctx = getApplication<Application>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
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
            seen.values.toList()
        } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Resolve (or create) the system thread for the picked recipient. */
    fun openThread(address: String, onResult: (Long) -> Unit) = viewModelScope.launch {
        onResult(repo.threadIdFor(address))
    }
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
    vm: NewMessageViewModel = viewModel(),
) {
    val query by vm.query.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun pick(address: String) = vm.openThread(address) { threadId -> onOpenThread(threadId, address) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("To: name or number") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            LazyColumn(Modifier.fillMaxSize()) {
                if (isDialable(query)) {
                    item(key = "send-to-number") {
                        SendToNumberRow(query.trim(), onClick = { pick(query.trim()) })
                    }
                }
                items(contacts, key = { it.name + "|" + it.number }) { contact ->
                    ContactRow(contact, onClick = { pick(contact.number) })
                }
                if (contacts.isEmpty() && !isDialable(query)) {
                    item {
                        Text(
                            if (query.isBlank()) "No contacts to show"
                            else "No matches — type a number to message it directly",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
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
            Text("Send to $number", style = MaterialTheme.typography.titleMedium)
            Text(
                "Not in your contacts",
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
                "${contact.number} · ${contact.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
