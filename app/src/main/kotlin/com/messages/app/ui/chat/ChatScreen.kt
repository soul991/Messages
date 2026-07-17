package com.messages.app.ui.chat

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messages.core.db.MessageEntity
import com.messages.designsystem.CategoryColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModelFactory(
    private val app: Application,
    private val threadId: Long,
    private val fallbackAddress: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(app, threadId, fallbackAddress) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: Long,
    onBack: () -> Unit,
    onWhy: (Long) -> Unit,
    fallbackAddress: String? = null,
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext as Application, threadId, fallbackAddress)
    )
    val messages by vm.messages.collectAsState()
    val contactName by vm.contactName.collectAsState()
    val address by vm.address.collectAsState()
    val pendingAttachment by vm.pendingAttachment.collectAsState()
    val sendError by vm.sendError.collectAsState()
    val simOptions by vm.simOptions.collectAsState()
    val selectedSubId by vm.selectedSubId.collectAsState()
    var showSimMenu by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Attachment sources: gallery (photo picker) and camera (FileProvider target).
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.attach(uri) }
    var cameraTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraTarget?.let { vm.attach(it) } }
    var showAttachSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSendError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(contactName ?: address, style = MaterialTheme.typography.titleMedium)
                        if (contactName != null) {
                            Text(
                                address, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        onWhy = { onWhy(msg.id) },
                        onNotSpam = { vm.moveToInbox(msg.id) },
                        onResend = { vm.resend(msg) },
                    )
                }
            }

            // Pending attachment preview
            if (pendingAttachment != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = pendingAttachment,
                        contentDescription = "Attachment preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(72.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.attach(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
                    }
                }
            }

            // Composer
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { showAttachSheet = true }) {
                    Icon(
                        Icons.Filled.Attachment,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Text message") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                // Dual-SIM indicator + per-chat picker (§8.1) — only with 2+ SIMs
                if (simOptions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showSimMenu = true }) {
                            Icon(
                                Icons.Filled.SimCard,
                                contentDescription = "Choose SIM",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showSimMenu,
                            onDismissRequest = { showSimMenu = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (selectedSubId == null) "• Default SIM" else "Default SIM") },
                                onClick = { vm.selectSim(null); showSimMenu = false },
                            )
                            simOptions.forEach { sim ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (selectedSubId == sim.subId) "• " else "") +
                                                "SIM ${sim.slotIndex + 1} — ${sim.displayName}"
                                        )
                                    },
                                    onClick = { vm.selectSim(sim.subId); showSimMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { vm.sendWithAttachment(draft); draft = "" },
                    enabled = draft.isNotBlank() || pendingAttachment != null,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (showAttachSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    AttachOption(Icons.Filled.Image, "Gallery") {
                        showAttachSheet = false
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AttachOption(Icons.Filled.PhotoCamera, "Camera") {
                        showAttachSheet = false
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file,
                        )
                        cameraTarget = uri
                        cameraCapture.launch(uri)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val OTP_EXTRACT = Regex("""\b(\d{4,8})\b""")

@Composable
private fun MessageBubble(
    msg: MessageEntity,
    onWhy: () -> Unit,
    onNotSpam: () -> Unit,
    onResend: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val isOut = msg.isOutgoing
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
    ) {
        // Red fraud-warning banner (Stage 2 exception / dangerous label)
        if (msg.fraudWarning || msg.dangerous) {
            Row(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CategoryColors.FraudContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning, contentDescription = null,
                    tint = CategoryColors.Fraud,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (msg.dangerous) "Dangerous — likely fraud. Links are disabled."
                    else "Caution: suspicious link from unverified sender",
                    style = MaterialTheme.typography.labelMedium,
                    color = CategoryColors.Fraud,
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isOut) 18.dp else 4.dp,
                        bottomEnd = if (isOut) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (isOut) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
        ) {
            Column {
                // MMS media attachment
                if (msg.mediaUri != null) {
                    if (msg.mediaMimeType?.startsWith("image/") == true) {
                        AsyncImage(
                            model = File(msg.mediaUri!!),
                            contentDescription = "MMS image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Attachment, contentDescription = null,
                                modifier = Modifier.width(18.dp),
                                tint = if (isOut) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg.mediaMimeType ?: "Attachment",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOut) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (msg.body.isNotBlank()) {
                    Text(
                        msg.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOut) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SimpleDateFormat("HH:mm", Locale.US).format(Date(msg.timestamp)) +
                    if (msg.sendStatus == "FAILED") " · Failed" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.sendStatus == "FAILED") CategoryColors.Fraud
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            if (msg.sendStatus == "FAILED") {
                TextButton(onClick = onResend) { Text("Resend") }
            }
        }

        // One-tap OTP copy chip (§8.2)
        if (msg.protectedLabel == "OTP") {
            OTP_EXTRACT.find(msg.body)?.groupValues?.get(1)?.let { code ->
                AssistChip(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    label = { Text("Copy OTP $code") },
                    leadingIcon = {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                    },
                )
            }
        }

        // Filtered-message actions
        if (msg.category in listOf("SPAM", "PROMOTIONS", "REVIEW", "BLOCKED")) {
            Row {
                TextButton(onClick = onNotSpam) { Text("Not spam") }
                TextButton(onClick = onWhy) { Text("Why?") }
            }
        }
    }
}
