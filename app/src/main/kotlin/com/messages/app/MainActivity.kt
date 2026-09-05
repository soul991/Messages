package com.messages.app

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.messages.app.security.AppLock
import com.messages.app.security.LockGrace
import com.messages.app.ui.common.LocalNavAnimatedVisibilityScope
import com.messages.app.ui.common.LocalSharedTransitionScope
import com.messages.app.ui.common.fadeThroughEnter
import com.messages.app.ui.common.fadeThroughExit
import com.messages.app.ui.common.sharedAxisEnter
import com.messages.app.ui.common.sharedAxisExit
import com.messages.app.ui.chat.ChatScreen
import com.messages.app.ui.compose.NewMessageScreen
import com.messages.app.ui.dashboard.DashboardScreen
import com.messages.app.ui.home.HomeScreen
import com.messages.app.ui.lock.LockScreen
import com.messages.app.ui.onboarding.OnboardingScreen
import com.messages.app.ui.settings.SettingsScreen
import com.messages.app.ui.why.WhyFilteredScreen
import com.messages.core.backfill.Backfill
import com.messages.designsystem.MessagesTheme
import com.messages.designsystem.ThemeMode

/**
 * App-lock state with the correct lifetime: a ViewModel survives configuration
 * changes (rotation must not re-prompt for auth) but dies with the process, so
 * a cold start is always locked when App lock is enabled.
 */
class AppLockStateViewModel : androidx.lifecycle.ViewModel() {
    var unlocked by mutableStateOf(false)

    /** elapsedRealtime stamp of the last true background trip (0 = none). */
    var backgroundedAt: Long = 0L

    /** elapsedRealtime stamp of an app-initiated startActivityForResult launch
     *  (camera, pickers, system dialogs); suppresses re-locking on return. */
    var externalLaunchAt: Long = 0L
}

/** Scopes route content so shared elements can find their nav animation scope. */
@Composable
private fun ProvideNavScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}

// Secret-space entry motion (Phase 7): a compartment opening, not a page
// push — scale+fade from near the long-press origin (the title, top-left
// region), spring-driven from Motion.kt. Exit runs on the FAST springs so
// leaving is quicker than entering. No new duration constants.
private fun vaultEnter() =
    androidx.compose.animation.scaleIn(
        animationSpec = com.messages.designsystem.Motion.spatialSlow(),
        initialScale = 0.92f,
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.18f, 0.08f),
    ) + androidx.compose.animation.fadeIn(com.messages.designsystem.Motion.effectsSlow())

private fun vaultExit() =
    androidx.compose.animation.scaleOut(
        animationSpec = com.messages.designsystem.Motion.spatialFast(),
        targetScale = 0.96f,
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.18f, 0.08f),
    ) + androidx.compose.animation.fadeOut(com.messages.designsystem.Motion.effectsFast())

// FragmentActivity (not ComponentActivity) so BiometricPrompt can attach (§8.2 app lock).
@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : FragmentActivity() {

    private var isDefaultSmsApp by mutableStateOf(false)
    private var roleRequestFailed by mutableStateOf(false)

    /** App lock: false until authenticated this foreground session. Held in a
     *  ViewModel so rotation doesn't force re-authentication (see AppLockStateViewModel). */
    private val lockState: AppLockStateViewModel by viewModels()
    private var appUnlocked: Boolean
        get() = lockState.unlocked
        set(value) { lockState.unlocked = value }

    /** Set once the NavHost is up; routes intents arriving via onNewIntent (singleTask). */
    private var intentNavigator: ((Intent) -> Unit)? = null

    /** Folder to show on Home (e.g. Review notification tap); observed by HomeScreen. */
    private var folderRequest by mutableStateOf<String?>(null)

    /** Resolved from settings before composition, then updated live from Settings. */
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var accentSeed by mutableStateOf(com.messages.designsystem.AccentSeed.VIOLET)
    private var customBrightnessEnabled by mutableStateOf(false)
    private var brightnessLevel by mutableStateOf(ThemePreferences.DEFAULT_BRIGHTNESS)

    private fun applyScreenBrightness(enabled: Boolean, level: Float) {
        val lp = window.attributes
        lp.screenBrightness = if (enabled) {
            level.coerceIn(0.05f, 1.0f)
        } else {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = lp
    }

    /** Keeps FLAG_SECURE in sync with the App lock toggle while Settings is
     *  open in this same activity. Field-held: SharedPreferences only keeps a
     *  weak reference to registered listeners. */
    private val secureFlagListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "app_lock") applyFlagSecure()
        }

    /** True while any secret-space route (setup/prompt/space/chat/settings)
     *  is on top — forces FLAG_SECURE regardless of the app-lock setting. */
    private var inSecretUi by mutableStateOf(false)

    /** Screenshots + Recents preview blocked while app lock is enabled (§8.2)
     *  and ALWAYS inside the secret locked space. */
    private fun applyFlagSecure() {
        if (AppLock.isEnabled(this) || inSecretUi) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshDefaultState()
        roleRequestFailed = !isDefaultSmsApp
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // First-run backfill: classify existing history once we can read it (§10).
        if (grants[android.Manifest.permission.READ_SMS] == true) Backfill.ensureScheduled(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFlagSecure()
        getSharedPreferences("settings", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(secureFlagListener)
        themeMode = ThemePreferences.current(this)
        accentSeed = ThemePreferences.currentAccent(this)
        customBrightnessEnabled = ThemePreferences.isCustomBrightnessEnabled(this)
        brightnessLevel = ThemePreferences.getBrightnessLevel(this)
        applyScreenBrightness(customBrightnessEnabled, brightnessLevel)
        refreshDefaultState()
        // Note: blind requestCorePermissions() removed on cold start.
        // Default SMS role automatically grants SMS permissions upon assignment.
        // Safety net: if READ_SMS is already granted (role granted in an
        // earlier session) and the backfill hasn't completed — e.g. its one
        // previous run failed — re-enqueue it here on every launch.
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Backfill.ensureScheduled(this)
        }

        // V2-21: resolving a `smsto:` recipient can need a Room round trip (the
        // persisted thread alias), so the route is computed in a coroutine
        // below rather than here. The launch intent is captured now — onNewIntent
        // replaces `intent` and the cold-start destination must not move.
        val startIntent = intent
        folderRequest = intent.getStringExtra("folder")
        val onboardingPrefs = getSharedPreferences("onboarding", MODE_PRIVATE)

        // Only fire the first-open default role prompt if onboarding is ALREADY COMPLETED.
        // During onboarding, the dedicated Onboarding screen asks for the role when the user taps
        // "Set as default". Firing here on cold start would cause a premature denial popup over
        // the intro onboarding page!
        val onboardingDone = onboardingPrefs.getBoolean("done", false)
        if (onboardingDone && !isDefaultSmsApp && !onboardingPrefs.getBoolean("role_prompted", false)) {
            onboardingPrefs.edit().putBoolean("role_prompted", true).apply()
            requestDefaultRole()
        }

        // R-26: resolve lock state BEFORE the first composition so the lock
        // screen never flashes on a cold start when app lock is disabled.
        // onStart repeats this check for foreground re-entries; the ViewModel
        // keeps the value across configuration changes.
        if (!AppLock.isEnabled(this)) appUnlocked = true

        setContent {
            MessagesTheme(mode = themeMode, accent = accentSeed) {
                // V2-37: the window theme guessed from the system night setting;
                // this corrects the bar icons to the scheme actually resolved.
                com.messages.app.ui.common.SyncSystemBars()
                // App lock gate (§8.2): everything below stays hidden until unlocked.
                if (!appUnlocked) {
                    LockScreen(
                        onRequestUnlock = {
                            AppLock.authenticate(
                                this, getString(R.string.lock_unlock_prompt),
                                onSuccess = { appUnlocked = true },
                            )
                        },
                    )
                    return@MessagesTheme
                }
                val nav = rememberNavController()
                // Secret locked space: leaving its routes re-locks the space
                // INSTANTLY (no grace period, ever) and keeps FLAG_SECURE in
                // sync — always secure while any secret route shows.
                DisposableEffect(nav) {
                    val listener =
                        androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                            val secret = destination.route?.startsWith("secret") == true
                            if (!secret) com.messages.app.ui.secret.SecretSession.lock()
                            inSecretUi = secret
                            applyFlagSecure()
                        }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose {
                        nav.removeOnDestinationChangedListener(listener)
                        inSecretUi = false
                    }
                }
                // Route intents that arrive while this singleTask activity is alive
                // (notification taps, ACTION_SENDTO from other apps).
                DisposableEffect(nav) {
                    intentNavigator = { newIntent ->
                        // lifecycleScope is main-dispatched, so navigation still
                        // happens on the main thread; only the thread-id lookup
                        // inside routeFor suspends.
                        lifecycleScope.launch {
                            routeFor(newIntent)?.let { route ->
                                // launchSingleTop would silently keep the OLD nav
                                // arguments when the target route pattern matches the
                                // current top (chat→chat via a notification tap for a
                                // different thread). Pop back to home instead so the
                                // stack stays home→chat and the new args always apply.
                                nav.navigate(route) {
                                    popUpTo("home")
                                }
                            }
                        }
                    }
                    onDispose { intentNavigator = null }
                }
                // Cold-start deep links (notification tap with the process
                // dead) must NOT become the start destination — a chat with
                // nothing underneath breaks every back affordance (top-bar
                // arrow no-ops, system back closes the app, and warm intent
                // routing's popUpTo("home") finds no home to pop to). Home is
                // always the graph root; the deep link navigates on top of it
                // once. rememberSaveable: after process death the restored nav
                // stack already contains the destination — never re-navigate.
                var deepLinkConsumed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!deepLinkConsumed) {
                        deepLinkConsumed = true
                        routeFor(startIntent)?.let { route ->
                            nav.navigate(route) { popUpTo("home") }
                        }
                    }
                }
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination =
                        if (!onboardingPrefs.getBoolean("done", false)) "onboarding" else "home",
                    // §9 motion: shared-axis X between sibling screens by
                    // default; the list↔chat pair overrides with fade-through
                    // so the shared avatar element carries the transition.
                    enterTransition = { sharedAxisEnter(forward = true) },
                    exitTransition = { sharedAxisExit(forward = true) },
                    popEnterTransition = { sharedAxisEnter(forward = false) },
                    popExitTransition = { sharedAxisExit(forward = false) },
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            isDefaultSmsApp = isDefaultSmsApp,
                            onRequestDefault = ::requestDefaultRole,
                            roleRequestFailed = roleRequestFailed,
                            onOpenAppSettings = { openAppSettings(this@MainActivity) },
                            onOpenDefaultApps = { openDefaultAppsSettings(this@MainActivity) },
                            onDone = {
                                onboardingPrefs.edit().putBoolean("done", true).apply()
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            },
                            onRestoreFromDrive = {
                                // §8.3: restore offer during onboarding.
                                onboardingPrefs.edit().putBoolean("done", true).apply()
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                                nav.navigate("drive_backup")
                            },
                        )
                    }
                    composable(
                        "home",
                        enterTransition = { fadeThroughEnter() },
                        exitTransition = { fadeThroughExit() },
                        popEnterTransition = { fadeThroughEnter() },
                        popExitTransition = { fadeThroughExit() },
                    ) {
                        ProvideNavScope(this) {
                        HomeScreen(
                            isDefaultSmsApp = isDefaultSmsApp,
                            initialFolder = folderRequest,
                            onRequestDefault = ::requestDefaultRole,
                            roleRequestFailed = roleRequestFailed,
                            onOpenAppSettings = { openAppSettings(this@MainActivity) },
                            onOpenDefaultApps = { openDefaultAppsSettings(this@MainActivity) },
                            onOpenThread = { threadId -> nav.navigate("chat/$threadId") },
                            onOpenSearchResult = { threadId, messageId, terms ->
                                val q = Uri.encode(terms.joinToString(" "))
                                nav.navigate("chat/$threadId?q=$q&target=$messageId")
                            },
                            onCompose = { nav.navigate("compose") },
                            onSettings = { nav.navigate("settings") },
                            onDashboard = { nav.navigate("dashboard") },
                            onOpenStarred = { nav.navigate("starred") },
                            onOpenArchived = { nav.navigate("archived") },
                            onOpenOutbox = { nav.navigate("outbox") },
                            // Secret space entry: 3s long-press on the title.
                            // First time → setup; afterwards → credential prompt.
                            onSecretEntry = {
                                if (com.messages.core.secret.SecretSpace.exists(this@MainActivity)) {
                                    nav.navigate("secret_prompt")
                                } else {
                                    nav.navigate("secret_setup")
                                }
                            },
                        )
                        }
                    }
                    composable(
                        "secret_setup",
                        enterTransition = { vaultEnter() },
                        exitTransition = { vaultExit() },
                        popEnterTransition = { vaultEnter() },
                        popExitTransition = { vaultExit() },
                    ) {
                        com.messages.app.ui.secret.VaultTheme {
                        com.messages.app.ui.secret.SecretSetupScreen(
                            onBack = { nav.popBackStack() },
                            onComplete = {
                                nav.navigate("secret_space") {
                                    popUpTo("home") // setup screens leave the stack
                                }
                            },
                        )
                        }
                    }
                    composable(
                        "secret_prompt",
                        enterTransition = { vaultEnter() },
                        exitTransition = { vaultExit() },
                        popEnterTransition = { vaultEnter() },
                        popExitTransition = { vaultExit() },
                    ) {
                        com.messages.app.ui.secret.VaultTheme {
                        com.messages.app.ui.secret.SecretPromptScreen(
                            onBack = { nav.popBackStack() },
                            onUnlocked = {
                                nav.navigate("secret_space") {
                                    popUpTo("home") // prompt never stays beneath the space
                                }
                            },
                            // Reset destroyed everything locked → straight into
                            // fresh setup for the new code + empty folder.
                            onReset = {
                                nav.navigate("secret_setup") { popUpTo("home") }
                            },
                        )
                        }
                    }
                    composable(
                        "secret_space",
                        enterTransition = { vaultEnter() },
                        exitTransition = { vaultExit() },
                        popEnterTransition = { vaultEnter() },
                        popExitTransition = { vaultExit() },
                    ) {
                        // Belt-and-braces: any way of reaching this route
                        // without the in-memory unlock bounces to Home.
                        if (!com.messages.app.ui.secret.SecretSession.unlocked) {
                            LaunchedEffect(Unit) { nav.navigate("home") { popUpTo("home") } }
                            return@composable
                        }
                        com.messages.app.ui.secret.VaultTheme {
                        com.messages.app.ui.secret.LockedSpaceScreen(
                            onBack = {
                                nav.navigate("home") { popUpTo("home") } // re-locks via listener
                            },
                            onOpenThread = { threadId -> nav.navigate("secret_chat/$threadId") },
                            onOpenSettings = { nav.navigate("secret_settings") },
                            onCompose = { nav.navigate("secret_compose") },
                        )
                        }
                    }
                    composable("secret_compose") {
                        if (!com.messages.app.ui.secret.SecretSession.unlocked) {
                            LaunchedEffect(Unit) { nav.navigate("home") { popUpTo("home") } }
                            return@composable
                        }
                        com.messages.app.ui.secret.VaultTheme {
                        NewMessageScreen(
                            onBack = { nav.popBackStack() },
                            space = com.messages.core.db.Spaces.LOCKED,
                            onOpenThread = { threadId, address ->
                                nav.navigate(
                                    "secret_chat/$threadId?address=${Uri.encode(address)}"
                                ) {
                                    popUpTo("secret_compose") { inclusive = true }
                                }
                            },
                        )
                        }
                    }
                    composable("secret_settings") {
                        if (!com.messages.app.ui.secret.SecretSession.unlocked) {
                            LaunchedEffect(Unit) { nav.navigate("home") { popUpTo("home") } }
                            return@composable
                        }
                        com.messages.app.ui.secret.VaultTheme {
                        com.messages.app.ui.secret.SecretSettingsScreen(
                            onBack = { nav.popBackStack() },
                        )
                        }
                    }
                    composable(
                        "secret_chat/{threadId}?address={address}",
                        arguments = listOf(
                            navArgument("address") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val threadId = entry.arguments?.getString("threadId")?.toLongOrNull()
                            ?: return@composable
                        if (!com.messages.app.ui.secret.SecretSession.unlocked) {
                            LaunchedEffect(Unit) { nav.navigate("home") { popUpTo("home") } }
                            return@composable
                        }
                        com.messages.app.ui.secret.VaultTheme {
                        ChatScreen(
                            threadId = threadId,
                            onBack = { nav.popBackStack() },
                            onWhy = { messageId -> nav.navigate("secret_why/$messageId") },
                            // Compose-from-space: brand-new thread may have no
                            // conversation row yet beyond the LOCKED shell.
                            fallbackAddress = entry.arguments?.getString("address"),
                            space = com.messages.core.db.Spaces.LOCKED,
                        )
                        }
                    }
                    composable("secret_why/{messageId}") { entry ->
                        val messageId = entry.arguments?.getString("messageId")?.toLongOrNull()
                            ?: return@composable
                        if (!com.messages.app.ui.secret.SecretSession.unlocked) {
                            LaunchedEffect(Unit) { nav.navigate("home") { popUpTo("home") } }
                            return@composable
                        }
                        com.messages.app.ui.secret.VaultTheme {
                        WhyFilteredScreen(messageId = messageId, onBack = { nav.popBackStack() })
                        }
                    }
                    composable("archived") {
                        com.messages.app.ui.archived.ArchivedScreen(
                            onBack = { nav.popBackStack() },
                            onOpenThread = { threadId -> nav.navigate("chat/$threadId") },
                        )
                    }
                    composable("outbox") {
                        com.messages.app.ui.outbox.OutboxScreen(
                            onBack = { nav.popBackStack() },
                            onOpenThread = { threadId -> nav.navigate("chat/$threadId") },
                        )
                    }
                    composable("dashboard") {
                        DashboardScreen(onBack = { nav.popBackStack() })
                    }
                    composable(
                        "starred?thread={thread}",
                        arguments = listOf(
                            navArgument("thread") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        com.messages.app.ui.starred.StarredScreen(
                            onBack = { nav.popBackStack() },
                            onOpenMessage = { threadId, messageId ->
                                nav.navigate("chat/$threadId?target=$messageId")
                            },
                            threadId = entry.arguments?.getString("thread")?.toLongOrNull(),
                        )
                    }
                    composable("notification_settings") {
                        com.messages.app.ui.settings.NotificationSettingsScreen(
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("about") {
                        com.messages.app.ui.settings.AboutScreen(
                            onBack = { nav.popBackStack() },
                            onOpenUpdateSettings = { nav.navigate("update_settings") },
                            onOpenUpdateCheck = { nav.navigate("update_check") },
                        )
                    }
                    composable("update_settings") {
                        com.messages.app.ui.update.UpdateSettingsScreen(
                            onBack = { nav.popBackStack() },
                            onNavigateToCheck = { nav.navigate("update_check") },
                        )
                    }
                    composable("update_check") {
                        com.messages.app.ui.update.UpdateScreen(
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenTrash = { nav.navigate("trash") },
                            onOpenDriveBackup = { nav.navigate("drive_backup") },
                            onOpenNotificationSettings = { nav.navigate("notification_settings") },
                            onOpenUpdateSettings = { nav.navigate("update_settings") },
                            onOpenAbout = { nav.navigate("about") },
                            // V2-39: the same destination as the Home-title
                            // press, reached without the gesture. Setup or
                            // prompt — never past either.
                            onSecretEntry = {
                                if (com.messages.core.secret.SecretSpace.exists(this@MainActivity)) {
                                    nav.navigate("secret_prompt")
                                } else {
                                    nav.navigate("secret_setup")
                                }
                            },
                            themeMode = themeMode,
                            accent = accentSeed,
                            onAccentChange = { seed ->
                                ThemePreferences.setAccent(this@MainActivity, seed)
                                accentSeed = seed
                            },
                            onThemeModeChange = { mode ->
                                ThemePreferences.set(this@MainActivity, mode)
                                themeMode = mode
                            },
                            customBrightness = customBrightnessEnabled,
                            onCustomBrightnessChange = { enabled ->
                                customBrightnessEnabled = enabled
                                ThemePreferences.setCustomBrightnessEnabled(this@MainActivity, enabled)
                                applyScreenBrightness(enabled, brightnessLevel)
                            },
                            brightnessLevel = brightnessLevel,
                            onBrightnessLevelChange = { level ->
                                brightnessLevel = level
                                ThemePreferences.setBrightnessLevel(this@MainActivity, level)
                                applyScreenBrightness(customBrightnessEnabled, level)
                            },
                        )
                    }
                    composable("drive_backup") {
                        com.messages.app.ui.drivebackup.DriveBackupScreen(
                            onBack = { nav.popBackStack() },
                            onPickSpamMessages = { nav.navigate("spam_backup_picker") },
                        )
                    }
                    composable("spam_backup_picker") {
                        com.messages.app.ui.drivebackup.SpamBackupPickerScreen(
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("trash") {
                        com.messages.app.ui.trash.TrashScreen(onBack = { nav.popBackStack() })
                    }
                    composable("compose") {
                        NewMessageScreen(
                            onBack = { nav.popBackStack() },
                            onOpenThread = { threadId, address ->
                                nav.navigate("chat/$threadId?address=${Uri.encode(address)}") {
                                    popUpTo("compose") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(
                        "chat/{threadId}?address={address}&q={q}&target={target}&draft={draft}&search={search}",
                        enterTransition = { fadeThroughEnter() },
                        exitTransition = { fadeThroughExit() },
                        popEnterTransition = { fadeThroughEnter() },
                        popExitTransition = { fadeThroughExit() },
                        arguments = listOf(
                            navArgument("address") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("q") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("target") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("draft") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("search") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val threadId = entry.arguments?.getString("threadId")?.toLongOrNull() ?: return@composable
                        ProvideNavScope(this) {
                        ChatScreen(
                            threadId = threadId,
                            onBack = { nav.popBackStack() },
                            onWhy = { messageId -> nav.navigate("why/$messageId") },
                            fallbackAddress = entry.arguments?.getString("address"),
                            // §8.5.3: opened from search — highlight terms + jump to the match.
                            initialSearchTerms = entry.arguments?.getString("q")
                                ?.split(' ')?.filter { it.isNotBlank() } ?: emptyList(),
                            initialSearchActive = entry.arguments?.getString("search") == "1",
                            targetMessageId = entry.arguments?.getString("target")?.toLongOrNull(),
                            // Direct share (§8.2): shared text lands as the draft.
                            initialDraft = entry.arguments?.getString("draft") ?: "",
                            onOpenContact = { nav.navigate("contactDetail/$threadId") },
                            // Phase 4 item 14: forward → target chat with the text as draft.
                            onForward = { targetThread, text ->
                                nav.navigate("chat/$targetThread?draft=${Uri.encode(text)}")
                            },
                        )
                        }
                    }
                    composable("contactDetail/{threadId}") { entry ->
                        val threadId = entry.arguments?.getString("threadId")?.toLongOrNull() ?: return@composable
                        com.messages.app.ui.contact.ContactDetailScreen(
                            threadId = threadId,
                            onBack = { nav.popBackStack() },
                            // Trio "Message" = return to the chat beneath.
                            onMessage = { nav.popBackStack() },
                            onOpenStarred = { nav.navigate("starred?thread=$threadId") },
                            // Fresh chat entry with search active; popUpTo keeps
                            // the stack home→chat (no duplicate chat entries —
                            // see the nav-cluster fix notes).
                            onSearchInChat = {
                                nav.navigate("chat/$threadId?search=1") { popUpTo("home") }
                            },
                        )
                    }
                    composable("why/{messageId}") { entry ->
                        val messageId = entry.arguments?.getString("messageId")?.toLongOrNull() ?: return@composable
                        WhyFilteredScreen(messageId = messageId, onBack = { nav.popBackStack() })
                    }
                }
                    }
                }
            }
        }
    }

    /** All ActivityResult launches (camera, pickers, SAF, role/permission
     *  dialogs, Drive sign-in) funnel through here — arm the suppression stamp
     *  so an app-initiated round trip never throws up the lock on return. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        lockState.externalLaunchAt = android.os.SystemClock.elapsedRealtime()
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStart() {
        super.onStart()
        // Contact names: register the observer (no-op until READ_CONTACTS is
        // granted) and heal stale cached names on every foreground (throttled).
        com.messages.core.contacts.ContactSync.ensureObserver(this)
        com.messages.core.contacts.ContactSync.refreshOnForeground(this)
        // No lock configured → always unlocked; locked → wait for authentication.
        if (!AppLock.isEnabled(this)) {
            appUnlocked = true
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val externalTrip = LockGrace.externalTripActive(lockState.externalLaunchAt, now)
        // "Lock after" grace (§8.2): a session left unlocked at onStop re-locks
        // only if the background trip exceeded the configured grace. An
        // app-initiated external-result trip (camera/picker/dialog) suppresses
        // re-locking entirely, regardless of the Lock-after setting.
        if (appUnlocked && !externalTrip &&
            LockGrace.shouldRelock(AppLock.lockAfterMs(this), lockState.backgroundedAt, now)
        ) {
            appUnlocked = false
        }
        lockState.backgroundedAt = 0L
        lockState.externalLaunchAt = 0L
    }

    override fun onStop() {
        super.onStop()
        // Secret locked space: re-locks IMMEDIATELY on any background trip —
        // no grace period ever, regardless of the app-lock "Lock after"
        // setting, and even during app-initiated external trips (camera etc.).
        // A configuration change is the single exception (rotation must not
        // eject the user); process death locks by construction.
        if (!isChangingConfigurations) {
            com.messages.app.ui.secret.SecretSession.lock()
        }
        // Re-lock whenever the app truly leaves the foreground. A configuration
        // change (rotation, fold, locale) also passes through onStop but must
        // not throw the user back to the biometric prompt. With a "Lock after"
        // grace configured — or an app-initiated external launch in flight —
        // only stamp the trip; onStart decides. FLAG_SECURE keeps the Recents
        // preview blank in the meantime.
        if (AppLock.isEnabled(this) && !isChangingConfigurations) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (LockGrace.externalTripActive(lockState.externalLaunchAt, now)) {
                lockState.backgroundedAt = now
            } else if (AppLock.lockAfterMs(this) <= 0L) {
                appUnlocked = false
            } else {
                lockState.backgroundedAt = now
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyScreenBrightness(customBrightnessEnabled, brightnessLevel)
        refreshDefaultState()
    }

    override fun onDestroy() {
        getSharedPreferences("settings", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(secureFlagListener)
        super.onDestroy()
    }

    // singleTask: notification taps and SENDTO while alive land here, not onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentNavigator?.invoke(intent)
    }

    /**
     * Maps a launch intent to a nav route: notification `threadId` extra or an
     * ACTION_SENDTO/ACTION_SEND `sms:/smsto:/mms:/mmsto:` data URI → the chat for
     * that thread/recipient; a `folder` extra (Review notification) → home with
     * that folder selected. Null when the intent carries no destination.
     *
     * Suspends: a recipient with no Telephony thread has to be resolved through
     * the repository's persisted alias allocator (V2-21).
     */
    private suspend fun routeFor(intent: Intent): String? {
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId != -1L) return "chat/$threadId"

        // Direct share (§8.2): chooser target carries the conversation
        // shortcut id + the shared text, which becomes the draft.
        if (intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_SHORTCUT_ID)) {
            val sharedThreadId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
                ?.removePrefix("thread_")?.toLongOrNull()
            if (sharedThreadId != null) {
                val draft = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.let { "?draft=${Uri.encode(it)}" } ?: ""
                return "chat/$sharedThreadId$draft"
            }
        }

        val sendToAddress = intent.data
            ?.takeIf { it.scheme in listOf("sms", "smsto", "mms", "mmsto") }
            ?.schemeSpecificPart?.substringBefore('?')?.trim()
            ?.takeIf { it.isNotBlank() }
        if (sendToAddress != null) {
            // V2-21: this used to fall back to `sendToAddress.hashCode()`. Java
            // string hashes collide (and are only 32 bits), so two unrelated
            // recipients could land in the same local conversation — and the
            // value collides with real Telephony thread IDs besides. The
            // repository owns the one collision-free allocator: it tries
            // Telephony first and otherwise hands out a *persisted negative*
            // alias keyed by the normalized address.
            val sendToThreadId = com.messages.core.MessageRepository.get(application)
                .threadIdFor(sendToAddress)
            return "chat/$sendToThreadId?address=${Uri.encode(sendToAddress)}"
        }

        intent.getStringExtra("folder")?.let { folder ->
            folderRequest = folder
            return "home"
        }
        if (intent.getBooleanExtra("dashboard", false)) return "dashboard"
        return null
    }

    /**
     * Are we the default SMS app?
     *
     * [RoleManager] is API 29. `minSdk` is 26, and this runs from `onCreate`,
     * so before the version guard existed the app crashed on launch for every
     * Android 8.0/8.1/9 device — `getSystemService("role")` returns null there
     * and the non-null cast blew up. Android 8-9 use the pre-role mechanism:
     * ask the Telephony provider which package currently holds the default.
     */
    private fun refreshDefaultState() {
        val wasDefault = isDefaultSmsApp
        isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (getSystemService(Context.ROLE_SERVICE) as? RoleManager)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
        if (isDefaultSmsApp) {
            roleRequestFailed = false
            if (!wasDefault) {
                requestCorePermissions()
                Backfill.ensureScheduled(this)
            }
        }
    }

    /**
     * Ask to become the default SMS app. Both branches go through
     * [roleRequest], whose result callback re-reads the real state rather than
     * trusting the result code — the pre-29 chooser reports success even when
     * the user backs out.
     */
    private fun requestDefaultRole() {
        roleRequestFailed = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    try {
                        roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                        return
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to launch createRequestRoleIntent", e)
                        roleRequestFailed = true
                    }
                }
            }
        }
        if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
            try {
                roleRequest.launch(
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                )
                return
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch ACTION_CHANGE_DEFAULT", e)
                roleRequestFailed = true
            }
        }
        // Fallback for OEMs with custom role management
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) {
            roleRequestFailed = true
        }
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isDefaultSmsApp) {
            permissions.add(android.Manifest.permission.READ_SMS)
            permissions.add(android.Manifest.permission.RECEIVE_SMS)
            permissions.add(android.Manifest.permission.SEND_SMS)
        }
        permissionRequest.launch(permissions.toTypedArray())
    }

    companion object {
        fun isRestrictedSettingsActive(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
                val mode = appOps.unsafeCheckOpNoThrow(
                    "android:access_restricted_settings",
                    context.applicationInfo.uid,
                    context.packageName,
                )
                mode != AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }
        }

        fun openAppSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to open app details settings", e)
            }
        }

        fun openDefaultAppsSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {}
            }
        }
    }
}
