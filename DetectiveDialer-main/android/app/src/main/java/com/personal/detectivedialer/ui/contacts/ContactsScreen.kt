package com.personal.detectivedialer.ui.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.contacts.DeviceContact
import com.personal.detectivedialer.ui.components.EmptyState
import com.personal.detectivedialer.ui.components.InitialAvatar
import com.personal.detectivedialer.ui.components.avatarColorFor
import com.personal.detectivedialer.ui.theme.Brand
import kotlinx.coroutines.launch

/**
 * Contacts tab: device contacts loaded from ContactsContract with a live search,
 * a Samsung-style A–Z fast-scroll index, and swipe-right-to-call rows.
 */
@Composable
fun ContactsScreen(
    onOpenContact: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(viewModel.hasPermission()) }
    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.load()
    }

    // Placing a call may need CALL_PHONE; remember the target across the prompt.
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pendingCall?.let { viewModel.placeCall(it) }; pendingCall = null }

    val call: (String) -> Unit = { number ->
        if (!viewModel.placeCall(number)) {
            pendingCall = number
            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Scaffold(
        topBar = {
            ContactsTopBar(
                query = query,
                onQuery = viewModel::setQuery,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPermission -> ContactsPermission {
                    contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
                loading && contacts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                contacts.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Contacts,
                    title = if (query.isBlank()) "No contacts" else "No matches",
                    hint = if (query.isBlank()) "Contacts you save will appear here."
                    else "No contact matches \"${query.trim()}\".",
                )
                else -> ContactsList(contacts, onOpenContact, call)
            }
        }
    }
}

@Composable
private fun ContactsList(
    contacts: List<DeviceContact>,
    onOpen: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Sorted list is already alphabetical; group into letter sections and record
    // each section header's flat item index for the fast-scroll jump.
    val grouped = remember(contacts) { contacts.groupBy { it.sortLetter } }
    val letterIndex = remember(grouped) {
        val map = LinkedHashMap<String, Int>()
        var idx = 0
        grouped.forEach { (letter, list) -> map[letter] = idx; idx += 1 + list.size }
        map
    }

    Box(Modifier.fillMaxSize()) {
        ContactsLazyColumn(grouped, listState, onOpen, onCall)
        AzIndex(
            letters = letterIndex.keys.toList(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { letter ->
            letterIndex[letter]?.let { target -> scope.launch { listState.scrollToItem(target) } }
        }
    }
}

@Composable
private fun ContactsLazyColumn(
    grouped: Map<String, List<DeviceContact>>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (letter, list) ->
            item(key = "h-$letter") { LetterHeader(letter) }
            items(list, key = { it.id }) { contact ->
                SwipeToCallRow(contact, onOpen = { onOpen(contact.primaryNumber) }, onCall = onCall)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        letter,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** A contact row that reveals a green Call action as it's swiped right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToCallRow(contact: DeviceContact, onOpen: () -> Unit, onCall: (String) -> Unit) {
    val safeGreen = Brand.SafeGreen
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && contact.primaryNumber.isNotBlank()) {
                onCall(contact.primaryNumber)
            }
            // Never actually dismiss — snap back after triggering the call.
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(safeGreen)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("Call", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        ContactRow(contact, onOpen)
    }
}

@Composable
private fun ContactRow(contact: DeviceContact, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(name = contact.name, color = avatarColorFor(contact.name))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                contact.primaryNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Right-edge A–Z index; tap or drag to jump the list to that section. */
@Composable
private fun AzIndex(letters: List<String>, modifier: Modifier = Modifier, onLetter: (String) -> Unit) {
    if (letters.size < 2) return
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 2.dp)
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val i = (fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    onLetter(letters[i])
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onLetter(letter) }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun ContactsTopBar(query: String, onQuery: (String) -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.Blue)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF5C6670),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF171C20),
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(Brand.Blue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search contacts",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF8A939C),
                                )
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF5C6670),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ContactsPermission(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Contacts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Show your contacts", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Allow access to read contacts so you can search and call them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Button(onClick = onGrant) { Text("Allow contacts") }
    }
}
