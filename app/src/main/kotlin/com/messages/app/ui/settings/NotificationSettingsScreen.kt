package com.messages.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.MessagesApp

/**
 * Per-folder notification behavior (Phase 4 item 3, PRD §4 "per-folder
 * notification behavior is user-configurable"). The app-level switches decide
 * WHETHER a folder notifies; the system channel rows decide HOW (sound,
 * vibration, importance). Spam and Blocked are hard-silent by design and are
 * shown as facts, not options. Phase 5 §4: built from the shared settings
 * list language so this screen reads as one system with the main Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel()
    val notifyTransactions by vm.notifyTransactions.collectAsState()
    val notifyPromotions by vm.notifyPromotions.collectAsState()
    val notifyReview by vm.notifyReview.collectAsState()
    val otpAutoCopy by vm.otpAutoCopy.collectAsState()

    fun openChannelSettings(channelId: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader("Folders")

            SettingsNavRow(
                icon = Icons.Outlined.Inbox,
                title = "Inbox",
                subtitle = "Personal and important messages always notify. " +
                    "Tap to pick sound and style.",
                onClick = { openChannelSettings(MessagesApp.CH_PERSONAL) },
                external = true,
            )

            SettingsSwitchRow(
                icon = Icons.Outlined.Receipt,
                title = "Transactions",
                subtitle = "Notify for bank alerts, receipts, and bills.",
                checked = notifyTransactions,
                onChange = { vm.setNotifyTransactions(it) },
            )
            if (notifyTransactions) {
                SettingsNavRow(
                    icon = null,
                    title = "Sound & style for Transactions",
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_TRANSACTIONS) },
                    external = true,
                    indented = true,
                )
            }

            SettingsSwitchRow(
                icon = Icons.Outlined.LocalOffer,
                title = "Promotions",
                subtitle = "Notify for offers and marketing messages. Off keeps them badge-only.",
                checked = notifyPromotions,
                onChange = { vm.setNotifyPromotions(it) },
            )
            if (notifyPromotions) {
                SettingsNavRow(
                    icon = null,
                    title = "Sound & style for Promotions",
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_PROMOTIONS) },
                    external = true,
                    indented = true,
                )
            }

            SettingsSwitchRow(
                icon = Icons.Outlined.RateReview,
                title = "Review folder",
                subtitle = "One quiet, batched notification when messages arrive here.",
                checked = notifyReview,
                onChange = { vm.setNotifyReview(it) },
            )
            if (notifyReview) {
                SettingsNavRow(
                    icon = null,
                    title = "Sound & style for Review",
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_REVIEW) },
                    external = true,
                    indented = true,
                )
            }

            Text(
                "Spam and Blocked never notify — they stay silent with badge counts only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            SettingsSectionDivider()
            SettingsSectionHeader("Protection")
            val warnDangerous by vm.warnDangerous.collectAsState()
            SettingsSwitchRow(
                icon = Icons.Outlined.GppMaybe,
                title = "Warn me about dangerous messages",
                subtitle = "A red warning notification when a message looks like fraud. " +
                    "It stays until you dismiss it. Ordinary spam never notifies.",
                checked = warnDangerous,
                onChange = { vm.setWarnDangerous(it) },
            )

            SettingsSectionDivider()
            SettingsSectionHeader("OTP codes")
            SettingsSwitchRow(
                icon = Icons.Outlined.ContentCopy,
                title = "Auto-copy OTP codes",
                subtitle = "Copy the code to the clipboard the moment an OTP arrives. " +
                    "Android may show a clipboard notice each time.",
                checked = otpAutoCopy,
                onChange = { vm.setOtpAutoCopy(it) },
            )
            Text(
                "OTP notifications always include a one-tap Copy button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
