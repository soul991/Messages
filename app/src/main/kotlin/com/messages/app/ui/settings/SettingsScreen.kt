package com.messages.app.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.security.AppLock
import com.messages.core.MessageRepository
import com.messages.core.backup.BackupManager
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.db.UserRuleEntity
import com.messages.designsystem.AccentSeed
import com.messages.designsystem.ThemeMode
import com.messages.designsystem.schemeForSeed
import kotlinx.coroutines.Dispatchers
import com.messages.protection.SafeRegexPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val rules: StateFlow<List<UserRuleEntity>> =
        repo.db.userRules().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sensitivity = MutableStateFlow(repo.sensitivityName())
    val libraryInfo = MutableStateFlow(repo.engine.libraryVersion to repo.engine.patternCount)
    val hasImportedPack = MutableStateFlow(repo.hasImportedPatternPack())
    val importStatus = MutableStateFlow<String?>(null)
    val otpAutoDelete = MutableStateFlow(OtpCleanup.isEnabled(app))
    val spamAutoClean = MutableStateFlow(com.messages.core.cleanup.SpamCleanup.isEnabled(app))
    val appLock = MutableStateFlow(AppLock.isEnabled(app))
    val lockAfterMs = MutableStateFlow(AppLock.lockAfterMs(app))
    val hidePreviews = MutableStateFlow(AppLock.hidePreviews(app))
    val canAuthenticate = AppLock.canAuthenticate(app)

    /** Trash entry badge (§6.4). */
    val trashCount = repo.db.messages().trashCount()

    // R-03: widgets cache rendered text, so a privacy toggle must push a refresh
    // immediately — otherwise sender names and bodies stay on the launcher until
    // the next 30-minute system update or incoming message.
    fun setAppLock(enabled: Boolean) {
        AppLock.setEnabled(getApplication(), enabled)
        appLock.value = enabled
        com.messages.app.widget.WidgetUpdater.requestUpdate(getApplication())
    }

    fun setHidePreviews(hide: Boolean) {
        AppLock.setHidePreviews(getApplication(), hide)
        hidePreviews.value = hide
        com.messages.app.widget.WidgetUpdater.requestUpdate(getApplication())
    }

    fun setLockAfter(ms: Long) {
        AppLock.setLockAfterMs(getApplication(), ms)
        lockAfterMs.value = ms
    }

    fun setOtpAutoDelete(enabled: Boolean) {
        OtpCleanup.setEnabled(getApplication(), enabled)
        otpAutoDelete.value = enabled
    }

    fun setSpamAutoClean(enabled: Boolean) {
        com.messages.core.cleanup.SpamCleanup.setEnabled(getApplication(), enabled)
        spamAutoClean.value = enabled
    }

    val otpAutoCopy = MutableStateFlow(com.messages.app.notify.OtpClipboard.autoCopyEnabled(app))

    fun setOtpAutoCopy(enabled: Boolean) {
        com.messages.app.notify.OtpClipboard.setAutoCopy(getApplication(), enabled)
        otpAutoCopy.value = enabled
    }

    /** Phase 4 item 19: persistent fraud warning for Dangerous verdicts — default ON. */
    val warnDangerous = MutableStateFlow(
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("warn_dangerous", true)
    )

    fun setWarnDangerous(enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("warn_dangerous", enabled).apply()
        warnDangerous.value = enabled
    }

    val notifyTransactions = MutableStateFlow(app.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notify_transactions", true))
    val notifyPromotions = MutableStateFlow(app.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notify_promotions", false))
    val notifyReview = MutableStateFlow(app.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notify_review", true))

    fun setNotifyTransactions(enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("notify_transactions", enabled).apply()
        notifyTransactions.value = enabled
    }

    fun setNotifyPromotions(enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("notify_promotions", enabled).apply()
        notifyPromotions.value = enabled
    }

    fun setNotifyReview(enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("notify_review", enabled).apply()
        notifyReview.value = enabled
    }

    fun setSensitivity(name: String) {
        repo.setSensitivity(name)
        sensitivity.value = name
    }

    /** Non-null when the last [addRule] was refused; cleared on the next attempt. */
    val ruleError = MutableStateFlow<String?>(null)

    fun addRule(kind: String, target: String, pattern: String, category: String) {
        ruleError.value = null
        if (pattern.isBlank()) return
        val trimmed = pattern.trim()

        // R-21: a custom rule runs on the intake path for every message, so it
        // gets the same screening as an imported pack. A pattern that does not
        // compile as a regex is fine — matchesRule falls back to a literal
        // comparison, which is how plain-text rules like "+9198…" work. What we
        // refuse is a pattern that DOES compile and can backtrack pathologically.
        if (trimmed.length > SafeRegexPolicy.MAX_REGEX_LENGTH) {
            ruleError.value = "Rule is longer than ${SafeRegexPolicy.MAX_REGEX_LENGTH} characters"
            return
        }
        val isRegex = runCatching { Regex(trimmed) }.isSuccess
        if (isRegex && !SafeRegexPolicy.accepts(trimmed)) {
            ruleError.value = runCatching { SafeRegexPolicy.requireAccepted(trimmed) }
                .exceptionOrNull()?.message ?: "That pattern isn't allowed"
            return
        }

        viewModelScope.launch {
            val position = (rules.value.maxOfOrNull { it.position } ?: 0) + 1
            repo.db.userRules().insert(
                UserRuleEntity(
                    position = position, kind = kind, target = target,
                    pattern = trimmed, category = category,
                )
            )
        }
    }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.db.userRules().delete(id) }

    fun importPack(uri: Uri) = viewModelScope.launch {
        // R-21: bound the read BEFORE building a String. readText() on a picked
        // file is an unbounded allocation controlled by whoever supplied it.
        val bytes = withContext(Dispatchers.IO) {
            com.messages.core.io.BoundedRead.readUri(
                getApplication(), uri, com.messages.protection.PatternPackPolicy.MAX_PACK_BYTES,
            )
        }
        if (bytes == null) {
            importStatus.value = "Couldn't read the file, or it is larger than " +
                "${com.messages.protection.PatternPackPolicy.MAX_PACK_BYTES / 1024} KB"
            return@launch
        }
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (text == null) {
            importStatus.value = "Couldn't read the selected file"
            return@launch
        }
        repo.importPatternPack(text).fold(
            onSuccess = { (version, count) ->
                importStatus.value = "Imported pattern pack v$version — $count patterns active"
                refreshLibraryInfo()
            },
            onFailure = { importStatus.value = "Invalid pattern pack: ${it.message}" },
        )
    }

    fun revertPack() = viewModelScope.launch {
        repo.revertToBundledPatterns()
        importStatus.value = "Reverted to the bundled library"
        refreshLibraryInfo()
    }

    private fun refreshLibraryInfo() {
        libraryInfo.value = repo.engine.libraryVersion to repo.engine.patternCount
        hasImportedPack.value = repo.hasImportedPatternPack()
    }

    // ---- Backup/restore (§8.2) ----

    val backupStatus = MutableStateFlow<String?>(null)

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        backupStatus.value = "Exporting…"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = BackupManager.export(app)
                app.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray())
                } ?: error("Couldn't open the selected location")
            }
        }
        backupStatus.value = result.fold(
            onSuccess = { "Backup saved" },
            onFailure = { "Backup failed: ${it.message}" },
        )
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        backupStatus.value = "Restoring…"
        val text = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
        }
        if (text == null) {
            backupStatus.value = "Couldn't read the selected file"
            return@launch
        }
        BackupManager.import(app, text).fold(
            onSuccess = { stats ->
                backupStatus.value = "Restored ${stats.messagesRestored} messages " +
                    "(${stats.messagesSkipped} already present), ${stats.rulesRestored} rules"
                // Restored settings may have changed these.
                sensitivity.value = repo.sensitivityName()
                otpAutoDelete.value = OtpCleanup.isEnabled(app)
                hidePreviews.value = AppLock.hidePreviews(app)
                refreshLibraryInfo()
            },
            onFailure = { backupStatus.value = "Restore failed: ${it.message}" },
        )
    }
}

private val SENSITIVITY_STEPS = listOf("RELAXED", "DEFAULT", "STRICT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenDriveBackup: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    accent: AccentSeed = AccentSeed.DYNAMIC,
    onAccentChange: (AccentSeed) -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsState()
    val sensitivity by vm.sensitivity.collectAsState()
    val libraryInfo by vm.libraryInfo.collectAsState()
    val hasImportedPack by vm.hasImportedPack.collectAsState()
    val importStatus by vm.importStatus.collectAsState()
    val otpAutoDelete by vm.otpAutoDelete.collectAsState()
    val spamAutoClean by vm.spamAutoClean.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val lockAfterMs by vm.lockAfterMs.collectAsState()
    val hidePreviews by vm.hidePreviews.collectAsState()
    val activity = androidx.compose.ui.platform.LocalContext.current
        as? androidx.fragment.app.FragmentActivity

    var addRuleKind by remember { mutableStateOf<String?>(null) } // ALLOW | BLOCK | CUSTOM

    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importPack(uri) }

    val backupStatus by vm.backupStatus.collectAsState()
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBackup(uri) }
    var confirmRestore by remember { mutableStateOf(false) }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "Messages, rules, and settings from the backup will be added. " +
                        "Nothing on this device is deleted or overwritten; duplicates are skipped."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    backupPicker.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                }) { Text("Choose file") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }

    if (addRuleKind != null) {
        val ruleError by vm.ruleError.collectAsState()
        AddRuleDialog(
            kind = addRuleKind!!,
            onDismiss = { addRuleKind = null },
            onAdd = { target, pattern, category ->
                vm.addRule(addRuleKind!!, target, pattern, category)
                // addRule validates synchronously, so a refusal is already
                // visible here — keep the dialog open so the user can fix it.
                if (vm.ruleError.value == null) addRuleKind = null
            },
            error = ruleError,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // ---- Appearance (§8.2 / §9, Phase 5 §4) ----
            item {
                SettingsSectionHeader("Appearance")
                SettingsDropdownRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    subtitle = "Choose how Messages looks across the app.",
                    value = themeMode.displayName(),
                    options = ThemeMode.values().map { it to it.displayName() },
                    onSelect = onThemeModeChange,
                )
                AccentPickerRow(selected = accent, onSelect = onAccentChange)
                MessageTextSizeRow()
            }

            // ---- Protection sensitivity (§3 Stage 5) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Protection sensitivity")
                val index = SENSITIVITY_STEPS.indexOf(sensitivity).coerceAtLeast(0)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { vm.setSensitivity(SENSITIVITY_STEPS[it.toInt().coerceIn(0, 2)]) },
                        valueRange = 0f..2f,
                        steps = 1,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SENSITIVITY_STEPS.forEachIndexed { i, step ->
                            Text(
                                step.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (sensitivity) {
                            "RELAXED" -> "Fewer messages filtered — borderline messages stay in your Inbox or Review."
                            "STRICT" -> "Aggressive filtering — borderline messages go to Spam sooner. Protected messages (OTPs, bank alerts) are never filtered at any level."
                            else -> "Balanced filtering, recommended for most people. Protected messages (OTPs, bank alerts) are never filtered."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // ---- Rules (§3 Stage 1) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Your rules")
                Text(
                    "Rules outrank everything, including the pattern library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item { RuleGroupHeader("Always allow", "ALLOW", onAdd = { addRuleKind = "ALLOW" }) }
            items(rules.filter { it.kind == "ALLOW" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }
            item { RuleGroupHeader("Always block", "BLOCK", onAdd = { addRuleKind = "BLOCK" }) }
            items(rules.filter { it.kind == "BLOCK" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }
            item { RuleGroupHeader("Custom rules", "CUSTOM", onAdd = { addRuleKind = "CUSTOM" }) }
            items(rules.filter { it.kind == "CUSTOM" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }

            // ---- Notifications (Phase 4 item 3: full per-folder screen) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Notifications")
                SettingsNavRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notification behavior",
                    subtitle = "Per-folder alerts, sounds, and OTP copy options.",
                    onClick = onOpenNotificationSettings,
                )
            }

            // ---- Privacy & security (§8.2) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Privacy & security")
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "App lock",
                    subtitle = if (vm.canAuthenticate) {
                        "Require fingerprint, face, or device PIN to open Messages."
                    } else {
                        "Set up a screen lock or biometrics on this device first."
                    },
                    checked = appLock,
                    enabled = vm.canAuthenticate,
                    onChange = { enable ->
                        // Both directions demand a successful auth: enabling
                        // proves the unlock works; disabling must not be a
                        // free action for whoever is holding an unlocked
                        // phone. Cancel/failure leaves the switch as-is.
                        if (activity != null) {
                            AppLock.authenticate(
                                activity,
                                if (enable) "Confirm to enable app lock"
                                else "Confirm to turn off app lock",
                                onSuccess = { vm.setAppLock(enable) },
                            )
                        }
                    },
                )
                if (appLock) {
                    SettingsDropdownRow(
                        icon = Icons.Outlined.Timer,
                        title = "Lock after",
                        subtitle = "Skip re-unlock when returning within this window.",
                        value = com.messages.app.security.LockGrace.label(lockAfterMs),
                        options = com.messages.app.security.LockGrace.options.map { it.first to it.second },
                        onSelect = { vm.setLockAfter(it) },
                    )
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Hide message previews",
                    subtitle = "Notifications show \"New message\" instead of the text.",
                    checked = hidePreviews,
                    onChange = { vm.setHidePreviews(it) },
                )
                Text(
                    "Tip: lock individual conversations from the ⋮ menu inside a chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // ---- Conversations (§8.1/§8.2): swipe actions + delivery reports ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Conversations")
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val rightAction by com.messages.app.ui.home.SwipeActions.right.collectAsState()
                val leftAction by com.messages.app.ui.home.SwipeActions.left.collectAsState()
                SettingsDropdownRow(
                    icon = Icons.Outlined.SwipeRight,
                    title = "Swipe right",
                    subtitle = "Left-to-right swipe on a conversation",
                    value = com.messages.app.ui.home.SwipeActions.label(rightAction),
                    options = com.messages.app.ui.home.SwipeActions.options.map { it.first to it.second },
                    onSelect = { com.messages.app.ui.home.SwipeActions.setRight(ctx, it) },
                )
                SettingsDropdownRow(
                    icon = Icons.Outlined.SwipeLeft,
                    title = "Swipe left",
                    subtitle = "Right-to-left swipe on a conversation",
                    value = com.messages.app.ui.home.SwipeActions.label(leftAction),
                    options = com.messages.app.ui.home.SwipeActions.options.map { it.first to it.second },
                    onSelect = { com.messages.app.ui.home.SwipeActions.setLeft(ctx, it) },
                )
                var deliveryReports by remember {
                    mutableStateOf(
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .getBoolean("delivery_reports", true)
                    )
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.DoneAll,
                    title = "Delivery reports",
                    subtitle = "Show \"Delivered\" on sent messages when the carrier confirms.",
                    checked = deliveryReports,
                    onChange = {
                        deliveryReports = it
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("delivery_reports", it).apply()
                    },
                )
                // Link previews (Phase 4 item 9) — the one opt-in network
                // feature for message content; scope stated in the subtitle.
                var linkPreviews by remember {
                    mutableStateOf(com.messages.app.ui.chat.LinkPreview.enabled(ctx))
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.Link,
                    title = "Link previews",
                    subtitle = "Show a small preview card for links in Inbox messages. " +
                        "Never for filtered folders or dangerous messages; no cookies are sent.",
                    checked = linkPreviews,
                    onChange = {
                        linkPreviews = it
                        com.messages.app.ui.chat.LinkPreview.setEnabled(ctx, it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                // Quick-reply templates (Phase 4 item 8).
                QuickRepliesEditor(ctx)
            }

            // ---- Auto-clean features ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Auto-clean features")
                SettingsSwitchRow(
                    icon = Icons.Outlined.AutoDelete,
                    title = "Delete OTP messages after 24 hours",
                    subtitle = "Only OTP-labeled messages in your Inbox. Starred OTPs and " +
                        "filtered folders are never touched.",
                    checked = otpAutoDelete,
                    onChange = { vm.setOtpAutoDelete(it) },
                )
                // §6.5: opt-in, confirmation required, Spam only, via Trash.
                var confirmSpamClean by remember { mutableStateOf(false) }
                SettingsSwitchRow(
                    icon = Icons.Outlined.CleaningServices,
                    title = "Auto-clean Spam older than 90 days",
                    subtitle = "Old Spam moves to Trash (restorable for 60 days). " +
                        "Starred messages, Review, and Blocked are never touched.",
                    checked = spamAutoClean,
                    onChange = { enable ->
                        if (enable) confirmSpamClean = true
                        else vm.setSpamAutoClean(false)
                    },
                )
                if (confirmSpamClean) {
                    AlertDialog(
                        onDismissRequest = { confirmSpamClean = false },
                        title = { Text("Auto-clean old Spam?") },
                        text = {
                            Text(
                                "Spam messages older than 90 days will be moved to " +
                                    "Trash automatically (once a week) and stay " +
                                    "restorable there for 60 days. Review and Blocked " +
                                    "folders are never cleaned."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmSpamClean = false
                                vm.setSpamAutoClean(true)
                            }) { Text("Turn on") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmSpamClean = false }) { Text("Cancel") }
                        },
                    )
                }
            }

            // ---- Pattern library (§7.5) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Pattern library")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Version ${libraryInfo.first} — ${libraryInfo.second} patterns" +
                            if (hasImportedPack) " (imported pack)" else " (bundled)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (importStatus != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            importStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            packPicker.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream")
                            )
                        }) { Text("Import pattern pack") }
                        if (hasImportedPack) {
                            TextButton(onClick = { vm.revertPack() }) { Text("Revert to bundled") }
                        }
                    }
                }
            }

            // ---- Backup & restore (§8.2) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Backup & restore")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Everything stays on this device: messages, categories, rules, " +
                            "sender trust, and settings go into one local file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (backupStatus != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            backupStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            val stamp = java.text.SimpleDateFormat(
                                "yyyy-MM-dd", java.util.Locale.US
                            ).format(java.util.Date())
                            backupCreator.launch("messages-backup-$stamp.json")
                        }) { Text("Back up now") }
                        TextButton(onClick = { confirmRestore = true }) { Text("Restore") }
                    }
                    // §8.3: encrypted, scheduled cloud backup.
                    TextButton(onClick = onOpenDriveBackup) { Text("Google Drive backup…") }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- Message import (BUG-1 safety net: §10 backfill re-run) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader("Message import")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val backfillInfos by com.messages.core.backfill.Backfill
                        .progressFlow(context).collectAsState(initial = emptyList())
                    val running = backfillInfos.firstOrNull()
                        ?.takeIf { it.state == androidx.work.WorkInfo.State.RUNNING }
                    Text(
                        "If your existing messages are missing from the app, import them " +
                            "again from the phone's SMS store. Already-imported messages are skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (running != null) {
                        val processed = running.progress.getInt(
                            com.messages.core.backfill.BackfillWorker.KEY_PROCESSED, 0)
                        val total = running.progress.getInt(
                            com.messages.core.backfill.BackfillWorker.KEY_TOTAL, 0)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (total > 0) "Importing… $processed of $total messages"
                            else "Importing…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = { com.messages.core.backfill.Backfill.reimport(context) },
                        enabled = running == null,
                    ) { Text("Re-import messages") }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- Trash (§6.4) ----
            item {
                SettingsSectionDivider()
                val trashCount by vm.trashCount.collectAsState(initial = 0)
                SettingsNavRow(
                    icon = Icons.Outlined.Delete,
                    title = if (trashCount > 0) "Trash ($trashCount)" else "Trash",
                    subtitle = "Deleted messages are kept for 60 days and can be restored.",
                    onClick = onOpenTrash,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

internal fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED black"
}

/**
 * Phase 5 §4 accent picker: Material You dynamic first, then the eight
 * curated seeds as tone-40 swatches. Swatches are decorative (the selection
 * state is carried by the check + row subtitle), so no AA pair is required
 * on the swatch fill itself.
 */
@Composable
private fun AccentPickerRow(selected: AccentSeed, onSelect: (AccentSeed) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Palette, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("App color", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (selected == AccentSeed.DYNAMIC) "Dynamic — follows your wallpaper"
                    else selected.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentSeed.values().forEach { seed ->
                AccentSwatch(seed, selected == seed) { onSelect(seed) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(seed: AccentSeed, selected: Boolean, onClick: () -> Unit) {
    val dynamic = seed == AccentSeed.DYNAMIC
    val fill = if (dynamic) {
        // The dynamic swatch previews the CURRENT dynamic primary; on pre-S
        // devices it shows the static fallback blue, which is equally honest.
        Brush.sweepGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            )
        )
    } else {
        SolidColor(schemeForSeed(seed, dark = false).primary)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.onSurface, CircleShape,
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${seed.displayName} accent" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // Scrim badge keeps the check readable on ANY fill — dynamic
            // swatches can be near-white pastels.
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** In-app message text size (Phase 4 item 15) — applies to chat bubbles. */
@Composable
private fun MessageTextSizeRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val options = listOf("Small" to 0.85f, "Default" to 1f, "Large" to 1.15f, "Extra large" to 1.3f)
    var scale by remember {
        mutableStateOf(
            ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .getFloat("message_text_scale", 1f)
        )
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.FormatSize, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Message text size", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Size of message text in conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 40.dp),
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = scale == value,
                    onClick = {
                        scale = value
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .edit().putFloat("message_text_scale", value).apply()
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}

/** Quick-reply template manager (Phase 4 item 8): list + add + delete. */@Composable
private fun QuickRepliesEditor(ctx: android.content.Context) {
    val templates by com.messages.app.ui.chat.QuickReplies.templates.collectAsState()
    LaunchedEffect(Unit) { com.messages.app.ui.chat.QuickReplies.load(ctx) }
    var newTemplate by remember { mutableStateOf("") }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Bolt, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Quick replies", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "One-tap templates offered by the ⚡ button in the composer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.padding(start = 40.dp)) {
            templates.forEach { template ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        template,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        com.messages.app.ui.chat.QuickReplies.remove(ctx, template)
                    }) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = "Delete template",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTemplate,
                    onValueChange = { newTemplate = it },
                    placeholder = { Text("New quick reply") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        com.messages.app.ui.chat.QuickReplies.add(ctx, newTemplate)
                        newTemplate = ""
                    },
                    enabled = newTemplate.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add template")
                }
            }
        }
    }
}

@Composable
private fun RuleGroupHeader(title: String, kind: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add $title rule")
        }
    }
}

@Composable
private fun RuleRow(rule: UserRuleEntity, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.pattern, style = MaterialTheme.typography.bodyMedium)
            if (rule.kind == "CUSTOM") {
                Text(
                    "When ${if (rule.target == "TEXT") "message text" else "sender"} matches → " +
                        rule.category.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete, contentDescription = "Delete rule",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private val CUSTOM_CATEGORIES = listOf("INBOX", "TRANSACTIONS", "PROMOTIONS", "SPAM", "REVIEW")

@Composable
private fun AddRuleDialog(
    kind: String,
    onDismiss: () -> Unit,
    onAdd: (target: String, pattern: String, category: String) -> Unit,
    error: String? = null,
) {
    var pattern by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("SENDER") }
    var category by remember { mutableStateOf("SPAM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    "ALLOW" -> "Always allow sender"
                    "BLOCK" -> "Always block sender"
                    else -> "Add custom rule"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = {
                        Text(if (kind == "CUSTOM" && target == "TEXT") "Text pattern (regex ok)" else "Sender number, header, or regex")
                    },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                // R-21: the rule was refused by SafeRegexPolicy — say why, in
                // the dialog, instead of silently dropping what the user typed.
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (kind == "CUSTOM") {
                    Spacer(Modifier.height(12.dp))
                    Text("Match against", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = target == "SENDER",
                            onClick = { target = "SENDER" },
                            label = { Text("Sender") },
                        )
                        FilterChip(
                            selected = target == "TEXT",
                            onClick = { target = "TEXT" },
                            label = { Text("Message text") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Move to", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CUSTOM_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = {
                                    Text(
                                        cat.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(target, pattern, category) },
                enabled = pattern.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
