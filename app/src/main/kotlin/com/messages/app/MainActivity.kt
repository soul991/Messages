package com.messages.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.messages.app.ui.chat.ChatScreen
import com.messages.app.ui.compose.NewMessageScreen
import com.messages.app.ui.home.HomeScreen
import com.messages.app.ui.onboarding.OnboardingScreen
import com.messages.app.ui.settings.SettingsScreen
import com.messages.app.ui.why.WhyFilteredScreen
import com.messages.core.backfill.Backfill
import com.messages.designsystem.MessagesTheme

class MainActivity : ComponentActivity() {

    private var isDefaultSmsApp by mutableStateOf(false)

    /** Set once the NavHost is up; routes intents arriving via onNewIntent (singleTask). */
    private var intentNavigator: ((Intent) -> Unit)? = null

    /** Folder to show on Home (e.g. Review notification tap); observed by HomeScreen. */
    private var folderRequest by mutableStateOf<String?>(null)

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshDefaultState() }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // First-run backfill: classify existing history once we can read it (§10).
        if (grants[android.Manifest.permission.READ_SMS] == true) Backfill.ensureScheduled(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshDefaultState()
        requestCorePermissions()

        val initialRoute = routeFor(intent)
        folderRequest = intent.getStringExtra("folder")
        val onboardingPrefs = getSharedPreferences("onboarding", MODE_PRIVATE)

        setContent {
            MessagesTheme {
                val nav = rememberNavController()
                // Route intents that arrive while this singleTask activity is alive
                // (notification taps, ACTION_SENDTO from other apps).
                DisposableEffect(nav) {
                    intentNavigator = { newIntent ->
                        routeFor(newIntent)?.let { route ->
                            nav.navigate(route) { launchSingleTop = true }
                        }
                    }
                    onDispose { intentNavigator = null }
                }
                NavHost(
                    navController = nav,
                    startDestination = when {
                        initialRoute != null -> initialRoute
                        !onboardingPrefs.getBoolean("done", false) -> "onboarding"
                        else -> "home"
                    },
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            isDefaultSmsApp = isDefaultSmsApp,
                            onRequestDefault = ::requestDefaultRole,
                            onDone = {
                                onboardingPrefs.edit().putBoolean("done", true).apply()
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            },
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            isDefaultSmsApp = isDefaultSmsApp,
                            initialFolder = folderRequest,
                            onRequestDefault = ::requestDefaultRole,
                            onOpenThread = { threadId -> nav.navigate("chat/$threadId") },
                            onCompose = { nav.navigate("compose") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
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
                        "chat/{threadId}?address={address}",
                        arguments = listOf(
                            navArgument("address") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val threadId = entry.arguments?.getString("threadId")?.toLongOrNull() ?: return@composable
                        ChatScreen(
                            threadId = threadId,
                            onBack = { nav.popBackStack() },
                            onWhy = { messageId -> nav.navigate("why/$messageId") },
                            fallbackAddress = entry.arguments?.getString("address"),
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

    override fun onResume() {
        super.onResume()
        refreshDefaultState()
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
     */
    private fun routeFor(intent: Intent): String? {
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId != -1L) return "chat/$threadId"

        val sendToAddress = intent.data
            ?.takeIf { it.scheme in listOf("sms", "smsto", "mms", "mmsto") }
            ?.schemeSpecificPart?.substringBefore('?')?.trim()
            ?.takeIf { it.isNotBlank() }
        if (sendToAddress != null) {
            val sendToThreadId = try {
                Telephony.Threads.getOrCreateThreadId(this, sendToAddress)
            } catch (_: Exception) {
                sendToAddress.hashCode().toLong()
            }
            return "chat/$sendToThreadId?address=${Uri.encode(sendToAddress)}"
        }

        intent.getStringExtra("folder")?.let { folder ->
            folderRequest = folder
            return "home"
        }
        return null
    }

    private fun refreshDefaultState() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        isDefaultSmsApp = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun requestDefaultRole() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
    }

    private fun requestCorePermissions() {
        permissionRequest.launch(
            arrayOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        )
    }
}
