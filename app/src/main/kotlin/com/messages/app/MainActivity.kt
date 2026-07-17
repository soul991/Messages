package com.messages.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.messages.app.ui.chat.ChatScreen
import com.messages.app.ui.home.HomeScreen
import com.messages.app.ui.why.WhyFilteredScreen
import com.messages.designsystem.MessagesTheme

class MainActivity : ComponentActivity() {

    private var isDefaultSmsApp by mutableStateOf(false)

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshDefaultState() }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshDefaultState()
        requestCorePermissions()

        val initialThreadId = intent.getLongExtra("threadId", -1L)
        val initialFolder = intent.getStringExtra("folder")

        setContent {
            MessagesTheme {
                val nav = rememberNavController()
                NavHost(
                    navController = nav,
                    startDestination = when {
                        initialThreadId != -1L -> "chat/$initialThreadId"
                        else -> "home"
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            isDefaultSmsApp = isDefaultSmsApp,
                            initialFolder = initialFolder,
                            onRequestDefault = ::requestDefaultRole,
                            onOpenThread = { threadId -> nav.navigate("chat/$threadId") },
                        )
                    }
                    composable("chat/{threadId}") { entry ->
                        val threadId = entry.arguments?.getString("threadId")?.toLongOrNull() ?: return@composable
                        ChatScreen(
                            threadId = threadId,
                            onBack = { nav.popBackStack() },
                            onWhy = { messageId -> nav.navigate("why/$messageId") },
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
