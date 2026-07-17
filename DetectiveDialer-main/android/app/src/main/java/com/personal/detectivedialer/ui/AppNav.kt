package com.personal.detectivedialer.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.detectivedialer.ui.contacts.ContactsScreen
import com.personal.detectivedialer.ui.person.PersonDetailScreen
import com.personal.detectivedialer.ui.detail.CallDetailScreen
import com.personal.detectivedialer.ui.dialer.DialerScreen
import com.personal.detectivedialer.ui.lists.AllowlistScreen
import com.personal.detectivedialer.ui.lists.BlocklistScreen
import com.personal.detectivedialer.ui.dashboard.DashboardScreen
import com.personal.detectivedialer.ui.messages.ComposeMessageScreen
import com.personal.detectivedialer.ui.messages.MessagesScreen
import com.personal.detectivedialer.ui.messages.MessagesViewModel
import com.personal.detectivedialer.ui.messages.ThreadScreen
import com.personal.detectivedialer.ui.onboarding.OnboardingScreen
import com.personal.detectivedialer.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CALL_DETAIL = "call/{callId}"
    const val SETTINGS = "settings"
    const val BLOCKLIST = "blocklist"
    const val ALLOWLIST = "allowlist"
    const val MESSAGES = "messages"
    const val SMS_THREAD = "sms_thread/{sender}"
    const val SMS_COMPOSE = "sms_compose?to={to}&body={body}"
    const val DIALER = "dialer?number={number}"
    const val CONTACTS = "contacts"
    const val PERSON_DETAIL = "number/{number}"
    const val NEW_MESSAGE = "new_message"

    fun dialer(number: String = "") = "dialer?number=${Uri.encode(number)}"
    fun callDetail(id: String) = "call/$id"
    fun personDetail(number: String) = "number/${Uri.encode(number)}"
    fun smsThread(sender: String) = "sms_thread/${Uri.encode(sender)}"
    fun smsCompose(to: String = "", body: String = "") =
        "sms_compose?to=${Uri.encode(to)}&body=${Uri.encode(body)}"
}

/** What the activity was launched with (notification tap / SENDTO / DIAL intent). */
data class LaunchTarget(
    val callId: String? = null,
    val smsSender: String? = null,
    val composeRecipient: String? = null,
    val composeBody: String? = null,
    /** Non-null when launched via ACTION_DIAL: open the dialpad (possibly pre-filled). */
    val dialNumber: String? = null,
)

/** Bottom navigation tabs; each maps onto an existing top-level route. */
private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val BottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Calls", Icons.Default.Call),
    BottomTab(Routes.CONTACTS, "Contacts", Icons.Default.Contacts),
    BottomTab(Routes.MESSAGES, "Messages", Icons.AutoMirrored.Filled.Message),
    BottomTab(Routes.BLOCKLIST, "Blocked", Icons.Default.Block),
)

/** Ordered bottom-tab routes — the basis for tab-to-tab slide direction (#7). */
private val BottomTabRoutes: List<String> = BottomTabs.map { it.route }

/**
 * Slide direction for a transition, computed from the two tabs' fixed indices (see
 * [TabSlide]). The from/to routes come from the transition's own initialState/
 * targetState — the actual endpoints — so this never reads stale external state.
 */
private fun tabSlideDirection(fromRoute: String?, toRoute: String?): Int =
    TabSlide.direction(BottomTabRoutes, fromRoute, toRoute)

@Composable
fun AppNav(startOnboarding: Boolean, launchTarget: LaunchTarget) {
    val navController = rememberNavController()
    // Capture once: finishing onboarding flips the flag, which must not
    // re-seed the NavHost with a different start destination.
    val start = remember { if (startOnboarding) Routes.ONBOARDING else Routes.DASHBOARD }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BottomTabs.map { it.route }

    // Unread-blocked-SMS badge on the Blocked tab (activity-scoped VM).
    val badgeVm: MessagesViewModel = hiltViewModel()
    val blockedBadge by badgeVm.blockedBadge.collectAsStateWithLifecycle()

    Scaffold(
        // Screens own their status-bar insets (blue headers paint behind the
        // transparent status bar); this shell only reserves the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                DialerBottomBar(
                    currentRoute = currentRoute,
                    blockedBadge = blockedBadge,
                    onSelect = { route -> navController.navigateToTab(route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
            // Tab-to-tab moves slide by the tabs' relative order (so every pair
            // reverses correctly, #7); all other navigation keeps the push/pop feel.
            // The tab slide keeps the app's existing subtle 1/6-width + fade — only
            // the *direction* is fixed, so no new animation cost is introduced.
            enterTransition = {
                val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                if (dir != 0) {
                    fadeIn(tween(260)) + slideInHorizontally(tween(260)) { dir * (it / 6) }
                } else {
                    fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 6 }
                }
            },
            exitTransition = {
                val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                if (dir != 0) {
                    // Outgoing screen leaves toward the opposite side the new one enters.
                    fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -dir * (it / 6) }
                } else {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 6 }
                }
            },
            popEnterTransition = {
                val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                if (dir != 0) {
                    fadeIn(tween(260)) + slideInHorizontally(tween(260)) { dir * (it / 6) }
                } else {
                    fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 6 }
                }
            },
            popExitTransition = {
                val dir = tabSlideDirection(initialState.destination.route, targetState.destination.route)
                if (dir != 0) {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -dir * (it / 6) }
                } else {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 6 }
                }
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenNumber = { navController.navigate(Routes.personDetail(it)) },
                    onOpenCall = { navController.navigate(Routes.callDetail(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDialer = { navController.navigate(Routes.dialer()) },
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onOpenContact = { navController.navigate(Routes.personDetail(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("number") { type = NavType.StringType }),
            ) {
                PersonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onMessage = { navController.navigate(Routes.smsThread(it)) },
                    onOpenCall = { navController.navigate(Routes.callDetail(it)) },
                )
            }
            composable(
                route = Routes.DIALER,
                arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                DialerScreen(
                    prefill = entry.arguments?.getString("number").orEmpty(),
                    onCallPlaced = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.CALL_DETAIL,
                arguments = listOf(navArgument("callId") { type = NavType.StringType }),
            ) { entry ->
                val callId = entry.arguments?.getString("callId").orEmpty()
                CallDetailScreen(callId = callId, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAllowlist = { navController.navigate(Routes.ALLOWLIST) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.BLOCKLIST) {
                BlocklistScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.ALLOWLIST) {
                AllowlistScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MESSAGES) {
                MessagesScreen(
                    onOpenThread = { navController.navigate(Routes.smsThread(it)) },
                    onCompose = { navController.navigate(Routes.NEW_MESSAGE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.NEW_MESSAGE) {
                com.personal.detectivedialer.ui.messages.NewMessageScreen(
                    onPick = { address ->
                        navController.navigate(Routes.smsThread(address)) {
                            popUpTo(Routes.MESSAGES)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SMS_THREAD,
                arguments = listOf(navArgument("sender") { type = NavType.StringType }),
            ) {
                ThreadScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SMS_COMPOSE,
                arguments = listOf(
                    navArgument("to") { type = NavType.StringType; defaultValue = "" },
                    navArgument("body") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ComposeMessageScreen(
                    initialRecipient = entry.arguments?.getString("to").orEmpty(),
                    initialBody = entry.arguments?.getString("body").orEmpty(),
                    onSent = { to ->
                        navController.navigate(Routes.smsThread(to)) {
                            popUpTo(Routes.MESSAGES)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // Jump straight to the launch target (notification tap / SENDTO intent).
    // Runs exactly once with the values captured at first composition.
    LaunchedEffect(Unit) {
        if (start == Routes.ONBOARDING) return@LaunchedEffect
        when {
            launchTarget.dialNumber != null ->
                navController.navigate(Routes.dialer(launchTarget.dialNumber))
            !launchTarget.callId.isNullOrBlank() ->
                navController.navigate(Routes.callDetail(launchTarget.callId))
            !launchTarget.smsSender.isNullOrBlank() -> {
                navController.navigate(Routes.MESSAGES)
                navController.navigate(Routes.smsThread(launchTarget.smsSender))
            }
            launchTarget.composeRecipient != null -> {
                navController.navigate(Routes.MESSAGES)
                navController.navigate(
                    Routes.smsCompose(launchTarget.composeRecipient, launchTarget.composeBody.orEmpty()),
                )
            }
        }
    }
}

/** Tab switch: single instance per tab, state saved, back returns to Calls. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun DialerBottomBar(
    currentRoute: String?,
    blockedBadge: Int,
    onSelect: (String) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        BottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab.route) },
                icon = {
                    val icon = @Composable {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                    if (tab.route == Routes.BLOCKLIST && blockedBadge > 0) {
                        BadgedBox(badge = { Badge { Text("$blockedBadge") } }) { icon() }
                    } else {
                        icon()
                    }
                },
                label = {
                    Text(
                        tab.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
