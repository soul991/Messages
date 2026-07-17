package com.personal.detectivedialer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.ui.components.rememberFileImageBitmap
import com.personal.detectivedialer.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAllowlist: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand.Blue,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGroup("Personal", Icons.Outlined.Person) {
                Field("Your name", s.ownerName, "Used in AI greetings") {
                    viewModel.update { cur -> cur.copy(ownerName = it) }
                }
                Field("Home address", s.homeAddress, "Given to delivery agents") {
                    viewModel.update { cur -> cur.copy(homeAddress = it) }
                }
                Field("Gate code", s.gateCode, "Shared with verified deliveries") {
                    viewModel.update { cur -> cur.copy(gateCode = it) }
                }
            }

            SettingsGroup("AI behaviour", Icons.Outlined.SmartToy) {
                ChoiceRow(
                    label = "Preferred language",
                    options = listOf("auto" to "Auto", "bn" to "Bengali", "hi" to "Hindi", "en" to "English"),
                    selected = s.language,
                ) { viewModel.update { cur -> cur.copy(language = it) } }
                ChoiceRow(
                    label = "Persona tone",
                    options = listOf("professional" to "Professional", "friendly" to "Friendly", "terse" to "Terse"),
                    selected = s.personaTone,
                ) { viewModel.update { cur -> cur.copy(personaTone = it) } }
            }

            SettingsGroup("Screening", Icons.Outlined.Shield) {
                ChoiceRow(
                    label = "Unknown international callers",
                    options = listOf("OFF" to "Ring", "SILENCE" to "Silence", "REJECT" to "Reject"),
                    selected = s.internationalPolicy,
                ) { viewModel.update { cur -> cur.copy(internationalPolicy = it) } }
                Helper(
                    "Applies to non-Indian numbers that aren't in your contacts or allowlist. " +
                        "Silence = the call still lands, but the phone doesn't audibly ring.",
                )
                NavRow(
                    icon = Icons.Outlined.VerifiedUser,
                    label = "Allowed numbers",
                    hint = "Numbers that always ring through",
                    onClick = onOpenAllowlist,
                )
            }

            SettingsGroup("Incoming call appearance", Icons.Outlined.Image) {
                IncomingAppearance(
                    wallpaperPath = s.incomingWallpaperPath,
                    scrimPercent = s.incomingScrimPercent,
                    onPickWallpaper = viewModel::setIncomingWallpaper,
                    onClearWallpaper = viewModel::clearIncomingWallpaper,
                    onScrimChange = viewModel::setIncomingScrim,
                )
            }

            SettingsGroup("SMS filtering", Icons.Outlined.Sms) {
                Helper(
                    "TRAI sender suffixes route messages automatically. Blocked messages go to " +
                        "the Blocked tab — always readable, restorable, never auto-deleted.",
                )
                ToggleRow("Block -P promotional", s.smsBlockPromotional) {
                    viewModel.update { cur -> cur.copy(smsBlockPromotional = it) }
                }
                ToggleRow("Block -S service", s.smsBlockService) {
                    viewModel.update { cur -> cur.copy(smsBlockService = it) }
                }
                ToggleRow("Block -T transactional", s.smsBlockTransactional) {
                    viewModel.update { cur -> cur.copy(smsBlockTransactional = it) }
                }
                ToggleRow("Block -G government", s.smsBlockGovernment) {
                    viewModel.update { cur -> cur.copy(smsBlockGovernment = it) }
                }
                ToggleRow(
                    "Content check for unlabelled senders",
                    s.smsContentFallback,
                    helper = "Off by default. When on, texts from senders without a TRAI suffix are " +
                        "sent to your backend (/screen-sms) for AI spam classification.",
                ) {
                    viewModel.update { cur -> cur.copy(smsContentFallback = it) }
                }
            }

            SettingsGroup("Connection", Icons.Outlined.Cloud) {
                BackendUrlField(
                    saved = s.backendUrl,
                    onSave = { viewModel.update { cur -> cur.copy(backendUrl = it) } },
                )
                Field("API key", s.apiKey, "Sent as X-Api-Key; leave empty if the backend has no API_KEY set") {
                    viewModel.update { cur -> cur.copy(apiKey = it) }
                }
            }

            SettingsGroup("Device", Icons.Outlined.Smartphone) {
                Text("FCM token", style = MaterialTheme.typography.labelMedium)
                Text(
                    s.fcmToken.ifBlank { "Not registered yet — open the app once with Google Play services." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A settings section: blue header with leading icon, then a white card. */
@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Helper(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Backend URL is buffered locally and only persisted once it validates —
 * a half-typed URL must never reach CallRepository.
 */
@Composable
private fun BackendUrlField(saved: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    val shown = text ?: saved
    val normalized = CallRepository.normalizeBackendUrl(shown)
    OutlinedTextField(
        value = shown,
        onValueChange = {
            text = it
            CallRepository.normalizeBackendUrl(it)?.let(onSave)
        },
        label = { Text("Backend URL") },
        isError = shown.isNotBlank() && normalized == null,
        supportingText = {
            Text(
                if (shown.isNotBlank() && normalized == null) "Not a valid URL"
                else "Screening server, e.g. https://your-app.up.railway.app",
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    helper: String = "",
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (helper.isNotBlank()) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    helper: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(helper) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Wallpaper picker + scrim slider for the incoming-call screen, with a live
 * preview that mirrors the real caller name + Answer/Reject layout so the user
 * can dial in a readable overlay before a call ever comes in.
 */
@Composable
private fun IncomingAppearance(
    wallpaperPath: String,
    scrimPercent: Int,
    onPickWallpaper: (android.net.Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onScrimChange: (Int) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPickWallpaper) }

    Helper("Set a photo behind incoming calls, then adjust the overlay so the caller's name stays readable.")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Brand.Blue),
        ) { Text(if (wallpaperPath.isBlank()) "Choose photo" else "Change photo") }
        if (wallpaperPath.isNotBlank()) {
            OutlinedButton(onClick = onClearWallpaper) { Text("Remove") }
        }
    }

    // Device-wallpaper fallback (#1): only relevant when no custom photo is set.
    if (wallpaperPath.isBlank()) {
        DeviceWallpaperAccess()
    }

    Text("Overlay darkness: $scrimPercent%", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = scrimPercent.toFloat(),
        onValueChange = { onScrimChange(it.toInt()) },
        valueRange = 0f..100f,
    )

    IncomingPreview(wallpaperPath = wallpaperPath, scrimPercent = scrimPercent)
}

/**
 * All-files-access control for the device-wallpaper fallback (#1). When no custom
 * photo is set, the call screen tries to use the device's current wallpaper — but
 * reading it needs the "all files access" toggle on Android 11+, which has no
 * runtime dialog. This shows the current grant state and deep-links to the system
 * settings screen, re-checking when the user returns.
 */
@Composable
private fun DeviceWallpaperAccess() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    // Re-check whenever we come back to the foreground (e.g. from system settings).
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = android.os.Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(Modifier.height(8.dp))
    if (granted) {
        Helper("Using your device wallpaper as the default incoming-call background. Pick a photo above to override it.")
    } else {
        Helper(
            "No photo set — incoming calls use a neutral background. Grant \"all files access\" to use your " +
                "device wallpaper instead.",
        )
        OutlinedButton(
            onClick = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
                // Fall back to the list screen if the per-app deep link isn't handled.
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        )
                    }
                }
            },
        ) { Text("Use device wallpaper") }
    }
}

/** Miniature of the real incoming-call screen used as the settings preview. */
@Composable
private fun IncomingPreview(wallpaperPath: String, scrimPercent: Int) {
    val bitmap = rememberFileImageBitmap(wallpaperPath.ifBlank { null })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1A2B)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep))),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimPercent / 100f)))
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text("A", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
            Text("Alex Morgan", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Incoming call", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PreviewAction(Icons.Filled.CallEnd, Brand.SpamRed)
                PreviewAction(Icons.Filled.Call, Brand.SafeGreen)
            }
        }
    }
}

@Composable
private fun PreviewAction(icon: ImageVector, bg: Color) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, display) ->
                FilterChip(
                    selected = selected == key,
                    onClick = { onSelect(key) },
                    label = { Text(display) },
                )
            }
        }
    }
}
