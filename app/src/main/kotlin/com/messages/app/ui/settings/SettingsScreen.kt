package com.messages.app.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.messages.designsystem.ThemeMode
import kotlinx.coroutines.Dispatchers
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

    fun setAppLock(enabled: Boolean) {
        AppLock.setEnabled(getApplication(), enabled)
        appLock.value = enabled
    }

    fun setHidePreviews(hide: Boolean) {
        AppLock.setHidePreviews(getApplication(), hide)
        hidePreviews.value = hide
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

    fun addRule(kind: String, target: String, pattern: String, category: String) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            val position = (rules.value.maxOfOrNull { it.position } ?: 0) + 1
            repo.db.userRules().insert(
                UserRuleEntity(
                    position = position, kind = kind, target = target,
                    pattern = pattern.trim(), category = category,
                )
            )
        }
    }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.db.userRules().delete(id) }

    fun importPack(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
            }.getOrNull()
        }
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
        AddRuleDialog(
            kind = addRuleKind!!,
            onDismiss = { addRuleKind = null },
            onAdd = { target, pattern, category ->
                vm.addRule(addRuleKind!!, target, pattern, category)
                addRuleKind = null
            },
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

            // ---- Appearance (§8.2 / §9) ----
            item {
                SectionHeader("Appearance")
                ThemeModePickerRow(
                    selected = themeMode,
                    onSelect = onThemeModeChange,
                )
            }

            // ---- Protection sensitivity (§3 Stage 5) ----
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Protection sensitivity")
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
                SectionHeader("Your rules")
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Notifications")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenNotificationSettings)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Notification behavior", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Per-folder alerts, sounds, and OTP copy options.",
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

            // ---- Privacy & security (§8.2) ----
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Privacy & security")
                SettingSwitchRow(
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
                    Spacer(Modifier.height(12.dp))
                    LockAfterPickerRow(
                        selectedMs = lockAfterMs,
                        onSelect = { vm.setLockAfter(it) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow(
                    title = "Hide message previews",
                    subtitle = "Notifications show \"New message\" instead of the text.",
                    checked = hidePreviews,
                    enabled = true,
                    onChange = { vm.setHidePreviews(it) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tip: lock individual conversations from the ⋮ menu inside a chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // ---- Conversations (§8.1/§8.2): swipe actions + delivery reports ----
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Conversations")
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val rightAction by com.messages.app.ui.home.SwipeActions.right.collectAsState()
                val leftAction by com.messages.app.ui.home.SwipeActions.left.collectAsState()
                SwipeActionPickerRow(
                    title = "Swipe right",
                    subtitle = "Left-to-right swipe on a conversation",
                    selectedId = rightAction,
                    onSelect = { com.messages.app.ui.home.SwipeActions.setRight(ctx, it) },
                )
                SwipeActionPickerRow(
                    title = "Swipe left",
                    subtitle = "Right-to-left swipe on a conversation",
                    selectedId = leftAction,
                    onSelect = { com.messages.app.ui.home.SwipeActions.setLeft(ctx, it) },
                )
                Spacer(Modifier.height(12.dp))
                var deliveryReports by remember {
                    mutableStateOf(
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .getBoolean("delivery_reports", true)
                    )
                }
                SettingSwitchRow(
                    title = "Delivery reports",
                    subtitle = "Show \"Delivered\" on sent messages when the carrier confirms.",
                    checked = deliveryReports,
                    enabled = true,
                    onChange = {
                        deliveryReports = it
                        ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("delivery_reports", it).apply()
                    },
                )
                Spacer(Modifier.height(12.dp))
                // Link previews (Phase 4 item 9) — the one opt-in network
                // feature for message content; scope stated in the subtitle.
                var linkPreviews by remember {
                    mutableStateOf(com.messages.app.ui.chat.LinkPreview.enabled(ctx))
                }
                SettingSwitchRow(
                    title = "Link previews",
                    subtitle = "Show a small preview card for links in Inbox messages. " +
                        "Never for filtered folders or dangerous messages; no cookies are sent.",
                    checked = linkPreviews,
                    enabled = true,
                    onChange = {
                        linkPreviews = it
                        com.messages.app.ui.chat.LinkPreview.setEnabled(ctx, it)
                    },
                )
                Spacer(Modifier.height(12.dp))
                // Quick-reply templates (Phase 4 item 8).
                QuickRepliesEditor(ctx)
            }

            // ---- Auto-clean features ----
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Auto-clean features")
                SettingSwitchRow(
                    title = "Delete OTP messages after 24 hours",
                    subtitle = "Only OTP-labeled messages in your Inbox. Starred OTPs and " +
                        "filtered folders are never touched.",
                    checked = otpAutoDelete,
                    enabled = true,
                    onChange = { vm.setOtpAutoDelete(it) },
                )
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow(
                    title = "Delete Spam after 90 days",
                    subtitle = "Automatically delete messages in the Spam and Blocked folders that are older than 90 days.",
                    checked = spamAutoClean,
                    enabled = true,
                    onChange = { vm.setSpamAutoClean(it) },
                )
            }

            // ---- Pattern library (§7.5) ----
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Pattern library")
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Backup & restore")
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Message import")
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
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionHeader("Trash")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "Deleted messages are kept for 60 days and can be restored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    val trashCount by vm.trashCount.collectAsState(initial = 0)
                    TextButton(onClick = onOpenTrash) {
                        Text(if (trashCount > 0) "Open Trash ($trashCount)" else "Open Trash")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Persisted theme choice; changing it updates the root Material theme immediately. */
@Composable
private fun ThemeModePickerRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Choose how Messages looks across the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Text(
                selected.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ThemeMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName()) },
                        onClick = {
                            expanded = false
                            onSelect(mode)
                        },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED black"
}

/** Picker row for a swipe direction's action (§8.2). */
@Composable
private fun SwipeActionPickerRow(
    title: String,
    subtitle: String,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Text(
                com.messages.app.ui.home.SwipeActions.label(selectedId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                com.messages.app.ui.home.SwipeActions.options.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LockAfterPickerRow(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Lock after", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Skip re-unlock when returning within this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Text(
                com.messages.app.security.LockGrace.label(selectedMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                com.messages.app.security.LockGrace.options.forEach { (ms, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(ms)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Quick-reply template manager (Phase 4 item 8): list + add + delete. */
@Composable
private fun QuickRepliesEditor(ctx: android.content.Context) {
    val templates by com.messages.app.ui.chat.QuickReplies.templates.collectAsState()
    LaunchedEffect(Unit) { com.messages.app.ui.chat.QuickReplies.load(ctx) }
    var newTemplate by remember { mutableStateOf("") }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Quick replies", style = MaterialTheme.typography.bodyLarge)
        Text(
            "One-tap templates offered by the ⚡ button in the composer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    modifier = Modifier.fillMaxWidth(),
                )
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
