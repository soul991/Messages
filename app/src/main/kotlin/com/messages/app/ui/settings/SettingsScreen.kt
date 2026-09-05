package com.messages.app.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.security.AppLock
import com.messages.app.ui.common.categoryLabelRes
import com.messages.app.ui.secret.SecretEntryAccess
import com.messages.core.MessageRepository
import com.messages.core.backup.BackupCrypto
import com.messages.core.backup.BackupManager
import com.messages.core.backup.LocalArchive
import com.messages.core.secret.SecretStrength
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.messages.app.R

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

    val advancedFiltering = MutableStateFlow(repo.isAdvancedFilteringEnabled())

    fun setAdvancedFiltering(enabled: Boolean) {
        repo.setAdvancedFilteringEnabled(enabled)
        advancedFiltering.value = enabled
    }

    /**
     * V2-36. Status and error text raised from here lands on screen, so it comes
     * out of the resource table like every other user-visible string. A
     * ViewModel has no composition to read `stringResource` from — the
     * Application it already holds is the context that resolves them.
     */
    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    /**
     * The counted-noun form of [string]. [count] selects the grammatical form
     * and is *not* passed as an argument — repeat it in [args] where the number
     * should appear, because a language may need the count in a different slot
     * than English does, or not at all.
     */
    private fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(id, count, *args)

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
            ruleError.value = plural(
                R.plurals.settings_rule_error_too_long,
                SafeRegexPolicy.MAX_REGEX_LENGTH,
                SafeRegexPolicy.MAX_REGEX_LENGTH,
            )
            return
        }
        val isRegex = runCatching { Regex(trimmed) }.isSuccess
        if (isRegex && !SafeRegexPolicy.accepts(trimmed)) {
            ruleError.value = runCatching { SafeRegexPolicy.requireAccepted(trimmed) }
                .exceptionOrNull()?.message ?: string(R.string.settings_rule_error_rejected)
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
            importStatus.value = string(
                R.string.settings_pack_too_large,
                com.messages.protection.PatternPackPolicy.MAX_PACK_BYTES / 1024,
            )
            return@launch
        }
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (text == null) {
            importStatus.value = string(R.string.settings_file_unreadable)
            return@launch
        }
        repo.importPatternPack(text).fold(
            onSuccess = { (version, count) ->
                importStatus.value =
                    plural(R.plurals.settings_pack_imported, count, version, count)
                refreshLibraryInfo()
            },
            onFailure = {
                importStatus.value = string(R.string.settings_pack_invalid, it.message.orEmpty())
            },
        )
    }

    fun revertPack() = viewModelScope.launch {
        repo.revertToBundledPatterns()
        importStatus.value = string(R.string.settings_pack_reverted)
        refreshLibraryInfo()
    }

    private fun refreshLibraryInfo() {
        libraryInfo.value = repo.engine.libraryVersion to repo.engine.patternCount
        hasImportedPack.value = repo.hasImportedPatternPack()
    }

    // ---- Backup/restore (§8.2) ----

    val backupStatus = MutableStateFlow<String?>(null)

    /**
     * V2-49: which password the UI is currently waiting for, if any. Null
     * whenever no archive operation is mid-flight.
     */
    enum class ArchivePromptKind { EXPORT, IMPORT }
    data class ArchivePrompt(val kind: ArchivePromptKind, val uri: Uri)

    val archivePrompt = MutableStateFlow<ArchivePrompt?>(null)

    fun cancelArchivePrompt() { archivePrompt.value = null }

    /** The picker returned a destination; the password comes next. */
    fun askExportPassword(uri: Uri) {
        archivePrompt.value = ArchivePrompt(ArchivePromptKind.EXPORT, uri)
    }

    /**
     * The picker returned a file. Whether a password is needed is a property of
     * the file, not a question for the user — an encrypted envelope announces
     * itself in its first four bytes, so plain files are never asked for a
     * password they do not have.
     */
    fun beginImport(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val encrypted = withContext(Dispatchers.IO) { LocalArchive.isEncrypted(app, uri) }
        when (encrypted) {
            null -> backupStatus.value = string(R.string.settings_file_unreadable)
            true -> archivePrompt.value = ArchivePrompt(ArchivePromptKind.IMPORT, uri)
            false -> importBackup(uri, null)
        }
    }

    /**
     * V2-49. [password] is zeroed here rather than by the caller: this is the
     * last hop before the crypto, the UI has no reason to keep holding it, and
     * a `finally` is the only place that survives a failed export.
     */
    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        val app = getApplication<Application>()
        backupStatus.value = string(R.string.settings_archive_verifying)
        val result = try {
            LocalArchive.export(app, uri, password)
        } finally {
            password.fill('\u0000')
        }
        backupStatus.value = result.fold(
            onSuccess = {
                plural(
                    R.plurals.settings_archive_saved,
                    it.messageCount, it.messageCount,
                )
            },
            onFailure = { string(R.string.settings_backup_failed, it.message.orEmpty()) },
        )
    }

    fun importBackup(uri: Uri, password: CharArray?) = viewModelScope.launch {
        val app = getApplication<Application>()
        backupStatus.value = string(R.string.settings_backup_restoring)
        // V2-12: read against this device's budget rather than reading first
        // and discovering the size afterwards — the picker can hand back a file
        // of any size, and the old readText() allocated all of it.
        // V2-49: LocalArchive routes to the encrypted or the plain reader; both
        // sides of that branch keep those bounds.
        val result = try {
            LocalArchive.import(app, uri, password)
        } finally {
            password?.fill('\u0000')
        }
        result.fold(
            onSuccess = { stats ->
                // Each count agrees with its own noun, then the frame puts the
                // three finished clauses in whatever order the language wants.
                backupStatus.value = string(
                    R.string.settings_backup_restored,
                    plural(
                        R.plurals.settings_backup_restored_messages,
                        stats.messagesRestored, stats.messagesRestored,
                    ),
                    plural(
                        R.plurals.settings_backup_restored_skipped,
                        stats.messagesSkipped, stats.messagesSkipped,
                    ),
                    plural(
                        R.plurals.settings_backup_restored_rules,
                        stats.rulesRestored, stats.rulesRestored,
                    ),
                )
                // Restored settings may have changed these.
                sensitivity.value = repo.sensitivityName()
                otpAutoDelete.value = OtpCleanup.isEnabled(app)
                hidePreviews.value = AppLock.hidePreviews(app)
                refreshLibraryInfo()
            },
            onFailure = {
                backupStatus.value = when (it) {
                    // V2-49: "wrong password" is a different instruction from
                    // "this file is broken", and only one of them is the user's
                    // to act on.
                    is BackupCrypto.WrongPasswordException ->
                        string(R.string.settings_archive_wrong_password)
                    else -> string(R.string.settings_restore_failed, it.message.orEmpty())
                }
            },
        )
    }
}

private val SENSITIVITY_STEPS = listOf("RELAXED", "DEFAULT", "STRICT")

/**
 * V2-36. The slider ticks used to be the step id lowercased and re-capitalised.
 * That is a locale-dependent transform of an internal constant — it stays
 * English everywhere, and in Turkish "STRICT" lowercases to "strıct".
 */
@StringRes
private fun sensitivityLabelRes(step: String): Int = when (step) {
    "RELAXED" -> R.string.settings_sensitivity_relaxed
    "STRICT" -> R.string.settings_sensitivity_strict
    else -> R.string.settings_sensitivity_default
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenDriveBackup: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenUpdateSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    /**
     * V2-39: routes to the locked space's credential prompt — the same
     * destination as the Home-title press, not a way around it.
     */
    onSecretEntry: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    accent: AccentSeed = AccentSeed.VIOLET,
    onAccentChange: (AccentSeed) -> Unit = {},
    customBrightness: Boolean = false,
    onCustomBrightnessChange: (Boolean) -> Unit = {},
    brightnessLevel: Float = 0.70f,
    onBrightnessLevelChange: (Float) -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()
    val libraryInfo by vm.libraryInfo.collectAsStateWithLifecycle()
    val hasImportedPack by vm.hasImportedPack.collectAsStateWithLifecycle()
    val importStatus by vm.importStatus.collectAsStateWithLifecycle()
    val otpAutoDelete by vm.otpAutoDelete.collectAsStateWithLifecycle()
    val spamAutoClean by vm.spamAutoClean.collectAsStateWithLifecycle()
    val appLock by vm.appLock.collectAsStateWithLifecycle()
    val lockAfterMs by vm.lockAfterMs.collectAsStateWithLifecycle()
    val hidePreviews by vm.hidePreviews.collectAsStateWithLifecycle()
    val advancedFiltering by vm.advancedFiltering.collectAsStateWithLifecycle()
    val activity = androidx.compose.ui.platform.LocalContext.current
        as? androidx.fragment.app.FragmentActivity

    var addRuleKind by remember { mutableStateOf<String?>(null) } // ALLOW | BLOCK | CUSTOM

    val packPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importPack(uri) }

    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    // V2-49: the archive is an encrypted envelope, not JSON. Declaring the type
    // honestly matters — a `.json` claim invites other apps to open it and
    // present the user with a parse error instead of a backup.
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LocalArchive.MIME_TYPE)
    ) { uri -> if (uri != null) vm.askExportPassword(uri) }
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.beginImport(uri) }
    val archivePrompt by vm.archivePrompt.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }

    archivePrompt?.let { prompt ->
        ArchivePasswordDialog(
            kind = prompt.kind,
            onDismiss = { vm.cancelArchivePrompt() },
            onConfirm = { password ->
                vm.cancelArchivePrompt()
                when (prompt.kind) {
                    SettingsViewModel.ArchivePromptKind.EXPORT ->
                        vm.exportBackup(prompt.uri, password)
                    SettingsViewModel.ArchivePromptKind.IMPORT ->
                        vm.importBackup(prompt.uri, password)
                }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_restore_confirm_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    backupPicker.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                }) { Text(stringResource(R.string.settings_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (addRuleKind != null) {
        val ruleError by vm.ruleError.collectAsStateWithLifecycle()
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {

            // ---- Appearance (§8.2 / §9, Phase 5 §4) ----
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
                SettingsDropdownRow(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                    value = stringResource(themeMode.labelRes()),
                    options = ThemeMode.values().map { it to stringResource(it.labelRes()) },
                    onSelect = onThemeModeChange,
                )
                AccentPickerRow(selected = accent, onSelect = onAccentChange)
                MessageTextSizeRow()
            }

            // ---- In-app brightness ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_brightness))
                InAppBrightnessSection(
                    customEnabled = customBrightness,
                    brightness = brightnessLevel,
                    onCustomEnabledChange = onCustomBrightnessChange,
                    onBrightnessChange = onBrightnessLevelChange,
                )
            }

            // ---- Protection sensitivity (§3 Stage 5) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_sensitivity))
                SettingsSwitchRow(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.settings_advanced_filtering_title),
                    subtitle = stringResource(R.string.settings_advanced_filtering_subtitle),
                    checked = advancedFiltering,
                    onChange = { vm.setAdvancedFiltering(it) },
                )
                Spacer(Modifier.height(12.dp))
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
                                stringResource(sensitivityLabelRes(step)),
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
                            "RELAXED" -> stringResource(R.string.settings_sensitivity_relaxed_body)
                            "STRICT" -> stringResource(R.string.settings_sensitivity_strict_body)
                            else -> stringResource(R.string.settings_sensitivity_default_body)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // ---- Rules (§3 Stage 1) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_rules))
                Text(
                    stringResource(R.string.settings_rules_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item { RuleGroupHeader(stringResource(R.string.settings_rules_always_allow), "ALLOW", onAdd = { addRuleKind = "ALLOW" }) }
            items(rules.filter { it.kind == "ALLOW" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }
            item { RuleGroupHeader(stringResource(R.string.settings_rules_always_block), "BLOCK", onAdd = { addRuleKind = "BLOCK" }) }
            items(rules.filter { it.kind == "BLOCK" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }
            item { RuleGroupHeader(stringResource(R.string.settings_rules_custom), "CUSTOM", onAdd = { addRuleKind = "CUSTOM" }) }
            items(rules.filter { it.kind == "CUSTOM" }, key = { it.id }) { rule ->
                RuleRow(rule, onDelete = { vm.deleteRule(rule.id) })
            }

            // ---- Notifications (Phase 4 item 3: full per-folder screen) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
                SettingsNavRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.settings_notification_behavior),
                    subtitle = stringResource(R.string.settings_notification_behavior_subtitle),
                    onClick = onOpenNotificationSettings,
                )
            }

            // ---- Privacy & security (§8.2) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_privacy))
                // The biometric prompt is raised from onChange, which is not a
                // composable scope; both titles are resolved here instead.
                val enablePrompt = stringResource(R.string.settings_app_lock_prompt_enable)
                val disablePrompt = stringResource(R.string.settings_app_lock_prompt_disable)
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = if (vm.canAuthenticate) {
                        stringResource(R.string.settings_app_lock_subtitle)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
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
                                if (enable) enablePrompt else disablePrompt,
                                onSuccess = { vm.setAppLock(enable) },
                            )
                        }
                    },
                )
                if (appLock) {
                    SettingsDropdownRow(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.settings_lock_after),
                        subtitle = stringResource(R.string.settings_lock_after_subtitle),
                        value = com.messages.app.security.LockGrace.label(lockAfterMs),
                        options = com.messages.app.security.LockGrace.options.map { it.first to it.second },
                        onSelect = { vm.setLockAfter(it) },
                    )
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_hide_previews),
                    subtitle = stringResource(R.string.settings_hide_previews_subtitle),
                    checked = hidePreviews,
                    onChange = { vm.setHidePreviews(it) },
                )
                // V2-39. The locked space's only entrance was a 1.5-second
                // press on the Home title — raw pointer input, so it does not
                // exist for a screen reader, Switch Access, a keyboard or a
                // D-pad, and is out of reach for many motor impairments.
                //
                // This switch is the user's own call on the trade: a labelled
                // action is discoverable by definition, which is the opposite
                // of what concealment wants. It ships on every install and
                // defaults off, so its presence says something about the app,
                // never about this user. Both rows lead to the credential
                // prompt; nothing here weakens it.
                val entryCtx = androidx.compose.ui.platform.LocalContext.current
                var accessibleEntry by remember {
                    mutableStateOf(SecretEntryAccess.enabled(entryCtx))
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.Accessibility,
                    title = stringResource(R.string.settings_accessible_entry_title),
                    subtitle = stringResource(R.string.settings_accessible_entry_subtitle),
                    checked = accessibleEntry,
                    onChange = {
                        accessibleEntry = it
                        SecretEntryAccess.setEnabled(entryCtx, it)
                    },
                )
                if (accessibleEntry) {
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(SecretEntryAccess.ACTION_LABEL),
                        subtitle = stringResource(
                            R.string.settings_accessible_entry_row_subtitle,
                        ),
                        onClick = onSecretEntry,
                        indented = true,
                    )
                }
                Text(
                    stringResource(R.string.settings_lock_chat_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // ---- Conversations (§8.1/§8.2): swipe actions + delivery reports ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_conversations))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val rightAction by com.messages.app.ui.home.SwipeActions.right.collectAsStateWithLifecycle()
                val leftAction by com.messages.app.ui.home.SwipeActions.left.collectAsStateWithLifecycle()
                SettingsDropdownRow(
                    icon = Icons.Outlined.SwipeRight,
                    title = stringResource(R.string.settings_swipe_right),
                    subtitle = stringResource(R.string.settings_swipe_right_subtitle),
                    value = com.messages.app.ui.home.SwipeActions.label(rightAction),
                    options = com.messages.app.ui.home.SwipeActions.options.map { it.first to it.second },
                    onSelect = { com.messages.app.ui.home.SwipeActions.setRight(ctx, it) },
                )
                SettingsDropdownRow(
                    icon = Icons.Outlined.SwipeLeft,
                    title = stringResource(R.string.settings_swipe_left),
                    subtitle = stringResource(R.string.settings_swipe_left_subtitle),
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
                    title = stringResource(R.string.settings_delivery_reports),
                    subtitle = stringResource(R.string.settings_delivery_reports_subtitle),
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
                    title = stringResource(R.string.settings_link_previews),
                    subtitle = stringResource(R.string.settings_link_previews_subtitle),
                    checked = linkPreviews,
                    onChange = {
                        linkPreviews = it
                        com.messages.app.ui.chat.LinkPreview.setEnabled(ctx, it)
                    },
                )
                // V2-51: summary cards. On by default and entirely offline —
                // the subtitle says where the numbers come from, because a card
                // that looks like it phoned the bank is exactly what this
                // feature must not be mistaken for.
                var summaryCards by remember {
                    mutableStateOf(com.messages.app.ui.chat.MessageCards.enabled(ctx))
                }
                SettingsSwitchRow(
                    icon = Icons.Outlined.Summarize,
                    title = stringResource(R.string.settings_summary_cards),
                    subtitle = stringResource(R.string.settings_summary_cards_subtitle),
                    checked = summaryCards,
                    onChange = {
                        summaryCards = it
                        com.messages.app.ui.chat.MessageCards.setEnabled(ctx, it)
                    },
                )
                Spacer(Modifier.height(8.dp))
                // Quick-reply templates (Phase 4 item 8).
                QuickRepliesEditor(ctx)
            }

            // ---- Auto-clean features ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_auto_clean))
                SettingsSwitchRow(
                    icon = Icons.Outlined.AutoDelete,
                    title = stringResource(R.string.settings_otp_auto_delete),
                    subtitle = stringResource(R.string.settings_otp_auto_delete_subtitle),
                    checked = otpAutoDelete,
                    onChange = { vm.setOtpAutoDelete(it) },
                )
                // §6.5: opt-in, confirmation required, Spam only, via Trash.
                var confirmSpamClean by remember { mutableStateOf(false) }
                SettingsSwitchRow(
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(R.string.settings_spam_auto_clean),
                    subtitle = stringResource(R.string.settings_spam_auto_clean_subtitle),
                    checked = spamAutoClean,
                    onChange = { enable ->
                        if (enable) confirmSpamClean = true
                        else vm.setSpamAutoClean(false)
                    },
                )
                if (confirmSpamClean) {
                    AlertDialog(
                        onDismissRequest = { confirmSpamClean = false },
                        title = { Text(stringResource(R.string.settings_spam_auto_clean_confirm_title)) },
                        text = {
                            Text(
                                stringResource(R.string.settings_spam_auto_clean_confirm_body)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmSpamClean = false
                                vm.setSpamAutoClean(true)
                            }) { Text(stringResource(R.string.settings_turn_on)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmSpamClean = false }) { Text(stringResource(R.string.action_cancel)) }
                        },
                    )
                }
            }

            // ---- Pattern library (§7.5) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_pattern_library))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        pluralStringResource(
                            if (hasImportedPack) R.plurals.settings_library_version_imported
                            else R.plurals.settings_library_version_bundled,
                            libraryInfo.second,
                            libraryInfo.first, libraryInfo.second,
                        ),
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
                        }) { Text(stringResource(R.string.settings_import_pattern_pack)) }
                        if (hasImportedPack) {
                            TextButton(onClick = { vm.revertPack() }) { Text(stringResource(R.string.settings_revert_to_bundled)) }
                        }
                    }
                }
            }

            // ---- Backup & restore (§8.2) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_backup))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.settings_backup_subtitle),
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
                            // V2-45: deliberately NOT AppDateFormat. This goes
                            // into a file name, so it must stay invariant — a
                            // localized month name or a non-Gregorian calendar
                            // would make backups sort wrongly and, in some
                            // scripts, produce characters the picker rejects.
                            // Built per click, so no stale-locale risk either.
                            val stamp = java.text.SimpleDateFormat(
                                "yyyy-MM-dd", java.util.Locale.US
                            ).format(java.util.Date())
                            backupCreator.launch(LocalArchive.suggestedName(stamp))
                        }) { Text(stringResource(R.string.settings_back_up_now)) }
                        TextButton(onClick = { confirmRestore = true }) { Text(stringResource(R.string.settings_restore)) }
                    }
                    // §8.3: encrypted, scheduled cloud backup.
                    TextButton(onClick = onOpenDriveBackup) { Text(stringResource(R.string.settings_google_drive_backup)) }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- Message import (BUG-1 safety net: §10 backfill re-run) ----
            item {
                SettingsSectionDivider()
                SettingsSectionHeader(stringResource(R.string.settings_section_import))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val backfillInfos by com.messages.core.backfill.Backfill
                        .progressFlow(context).collectAsStateWithLifecycle(initialValue = emptyList())
                    val running = backfillInfos.firstOrNull()
                        ?.takeIf { it.state == androidx.work.WorkInfo.State.RUNNING }
                    Text(
                        stringResource(R.string.settings_reimport_subtitle),
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
                            if (total > 0) {
                                pluralStringResource(
                                    R.plurals.settings_importing_progress,
                                    total, processed, total,
                                )
                            } else {
                                stringResource(R.string.settings_importing)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = { com.messages.core.backfill.Backfill.reimport(context) },
                        enabled = running == null,
                    ) { Text(stringResource(R.string.settings_reimport)) }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- Trash (§6.4) ----
            item {
                SettingsSectionDivider()
                val trashCount by vm.trashCount.collectAsStateWithLifecycle(initialValue = 0)
                SettingsNavRow(
                    icon = Icons.Outlined.Delete,
                    title = if (trashCount > 0) {
                        stringResource(R.string.settings_trash_with_count, trashCount)
                    } else {
                        stringResource(R.string.settings_trash)
                    },
                    subtitle = stringResource(R.string.settings_trash_subtitle),
                    onClick = onOpenTrash,
                )
                Spacer(Modifier.height(24.dp))
            }

            // ---- About: version, legal, updates, source ----
            item {
                SettingsSectionDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    onClick = onOpenAbout,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@StringRes
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.AMOLED -> R.string.settings_theme_amoled
}

/**
 * V2-36. [AccentSeed.displayName] is the enum's own stable identifier, kept in
 * :design-system where there is no resource table. The name the user reads
 * comes from here.
 */
@StringRes
internal fun AccentSeed.labelRes(): Int = when (this) {
    AccentSeed.DYNAMIC -> R.string.accent_dynamic
    AccentSeed.VIOLET -> R.string.accent_violet
    AccentSeed.BLUE -> R.string.accent_blue
    AccentSeed.TEAL -> R.string.accent_teal
    AccentSeed.GREEN -> R.string.accent_green
    AccentSeed.AMBER -> R.string.accent_amber
    AccentSeed.CORAL -> R.string.accent_coral
    AccentSeed.PINK -> R.string.accent_pink
    AccentSeed.PURPLE -> R.string.accent_purple
    AccentSeed.GRAPHITE -> R.string.accent_graphite
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
                Text(stringResource(R.string.settings_app_color), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (selected == AccentSeed.DYNAMIC) {
                        stringResource(R.string.settings_accent_dynamic_subtitle)
                    } else {
                        stringResource(selected.labelRes())
                    },
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
    // `semantics { }` is not a composable scope; resolved here and read inside.
    val swatchDescription =
        stringResource(R.string.settings_accent_swatch, stringResource(seed.labelRes()))
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
            .semantics { contentDescription = swatchDescription },
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
    val options = listOf(stringResource(R.string.settings_text_size_small) to 0.85f, stringResource(R.string.settings_text_size_default) to 1f, stringResource(R.string.settings_text_size_large) to 1.15f, stringResource(R.string.settings_text_size_extra_large) to 1.3f)
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
                Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_text_size_subtitle),
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

/** In-app brightness adjustment section. */
@Composable
private fun InAppBrightnessSection(
    customEnabled: Boolean,
    brightness: Float,
    onCustomEnabledChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
) {
    Column {
        SettingsSwitchRow(
            icon = Icons.Outlined.BrightnessMedium,
            title = stringResource(R.string.settings_brightness_custom_title),
            subtitle = stringResource(R.string.settings_brightness_custom_subtitle),
            checked = customEnabled,
            onChange = onCustomEnabledChange,
        )

        AnimatedVisibility(
            visible = customEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.BrightnessLow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Slider(
                        value = brightness,
                        onValueChange = { onBrightnessChange(it.coerceIn(0.05f, 1.0f)) },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Outlined.BrightnessHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    stringResource(R.string.settings_brightness_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Quick-reply template manager (Phase 4 item 8): list + add + delete. */@Composable
private fun QuickRepliesEditor(ctx: android.content.Context) {
    val templates by com.messages.app.ui.chat.QuickReplies.templates.collectAsStateWithLifecycle()
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
                Text(stringResource(R.string.settings_quick_replies), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_quick_replies_subtitle),
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
                            Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_delete_template),
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
                    placeholder = { Text(stringResource(R.string.settings_new_quick_reply)) },
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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_add_template))
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
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.settings_add_rule_to_group, title),
            )
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
                    stringResource(
                        if (rule.target == "TEXT") R.string.settings_rule_summary_text
                        else R.string.settings_rule_summary_sender,
                        stringResource(categoryLabelRes(rule.category)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_delete_rule),
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
                    "ALLOW" -> stringResource(R.string.settings_rule_dialog_allow_title)
                    "BLOCK" -> stringResource(R.string.settings_rule_dialog_block_title)
                    else -> stringResource(R.string.settings_rule_dialog_custom_title)
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = {
                        Text(if (kind == "CUSTOM" && target == "TEXT") stringResource(R.string.settings_rule_pattern_hint_text) else stringResource(R.string.settings_rule_pattern_hint_sender))
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
                    Text(stringResource(R.string.settings_rule_match_against), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = target == "SENDER",
                            onClick = { target = "SENDER" },
                            label = { Text(stringResource(R.string.settings_rule_target_sender)) },
                        )
                        FilterChip(
                            selected = target == "TEXT",
                            onClick = { target = "TEXT" },
                            label = { Text(stringResource(R.string.settings_rule_target_text)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_rule_move_to), style = MaterialTheme.typography.labelMedium)
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
                                        stringResource(categoryLabelRes(cat)),
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
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * V2-49: the one place a local archive's password is asked for.
 *
 * Export and import share a dialog because they share a shape, but they do not
 * share a policy. On **export** the password is being *created*, so it is
 * screened by [SecretStrength.isWeakPassword] — the same floor and the same
 * blocklist the locked space applies at setup — and typed twice, because the
 * one thing that cannot be recovered later is a password that was mistyped
 * once. On **import** the password already exists and is only being repeated
 * back; screening it there would refuse to open a file the user legitimately
 * owns, which is the failure mode a strength rule must never have.
 *
 * The confirm button is disabled rather than the entry being rejected on
 * press: a rule that is visible before the mistake costs nothing, and one that
 * fires afterwards costs a round trip through a file picker.
 *
 * ### The unavoidable caveat
 *
 * `OutlinedTextField` holds its value as a `String`, so between the keystroke
 * and [onConfirm] the password exists as an immutable object this code cannot
 * zero — only drop and leave to the collector. Everything below that boundary
 * ([LocalArchive], [BackupCrypto]) takes a `CharArray` and zeroes it, so the
 * exposure is the composition's lifetime rather than the process's. Closing it
 * properly needs a text field that never builds a String, which Compose does
 * not currently offer.
 */
@Composable
private fun ArchivePasswordDialog(
    kind: SettingsViewModel.ArchivePromptKind,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    val exporting = kind == SettingsViewModel.ArchivePromptKind.EXPORT
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }

    val weak = exporting && password.isNotEmpty() &&
        SecretStrength.isWeakPassword(password.toCharArray())
    val mismatch = exporting && repeated.isNotEmpty() && repeated != password
    val ready = if (exporting) {
        password.isNotEmpty() && !weak && password == repeated
    } else {
        password.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (exporting) R.string.settings_archive_export_title
                    else R.string.settings_archive_import_title,
                ),
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (exporting) R.string.settings_archive_export_body
                        else R.string.settings_archive_import_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.settings_archive_password)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (exporting) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeated,
                        onValueChange = { repeated = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.settings_archive_password_repeat)) },
                        isError = mismatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Only one line at a time: "too weak" and "they don't
                    // match" answered together read as two failures for one
                    // mistake.
                    val problem = when {
                        weak -> R.string.settings_archive_weak
                        mismatch -> R.string.settings_archive_mismatch
                        else -> 0
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (problem != 0) stringResource(problem)
                        else stringResource(R.string.settings_archive_no_recovery),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (problem != 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password.toCharArray()) },
                enabled = ready,
            ) {
                Text(
                    stringResource(
                        if (exporting) R.string.settings_archive_export_action
                        else R.string.settings_archive_import_action,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
