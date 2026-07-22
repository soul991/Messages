package com.messages.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
 * shown as facts, not options.
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Folders")
            Spacer(Modifier.height(4.dp))

            ChannelLinkRow(
                title = "Inbox",
                subtitle = "Personal and important messages always notify. " +
                    "Tap to pick sound and style.",
                onClick = { openChannelSettings(MessagesApp.CH_PERSONAL) },
            )
            Spacer(Modifier.height(12.dp))

            SettingSwitchRow(
                title = "Transactions",
                subtitle = "Notify for bank alerts, receipts, and bills.",
                checked = notifyTransactions,
                enabled = true,
                onChange = { vm.setNotifyTransactions(it) },
            )
            if (notifyTransactions) {
                ChannelLinkRow(
                    title = null,
                    subtitle = "Sound & style for Transactions",
                    onClick = { openChannelSettings(MessagesApp.CH_TRANSACTIONS) },
                )
            }
            Spacer(Modifier.height(12.dp))

            SettingSwitchRow(
                title = "Promotions",
                subtitle = "Notify for offers and marketing messages. Off keeps them badge-only.",
                checked = notifyPromotions,
                enabled = true,
                onChange = { vm.setNotifyPromotions(it) },
            )
            if (notifyPromotions) {
                ChannelLinkRow(
                    title = null,
                    subtitle = "Sound & style for Promotions",
                    onClick = { openChannelSettings(MessagesApp.CH_PROMOTIONS) },
                )
            }
            Spacer(Modifier.height(12.dp))

            SettingSwitchRow(
                title = "Review folder",
                subtitle = "One quiet, batched notification when messages arrive here.",
                checked = notifyReview,
                enabled = true,
                onChange = { vm.setNotifyReview(it) },
            )
            if (notifyReview) {
                ChannelLinkRow(
                    title = null,
                    subtitle = "Sound & style for Review",
                    onClick = { openChannelSettings(MessagesApp.CH_REVIEW) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Spam and Blocked never notify — they stay silent with badge counts only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionHeader("OTP codes")
            Spacer(Modifier.height(4.dp))
            SettingSwitchRow(
                title = "Auto-copy OTP codes",
                subtitle = "Copy the code to the clipboard the moment an OTP arrives. " +
                    "Android may show a clipboard notice each time.",
                checked = otpAutoCopy,
                enabled = true,
                onChange = { vm.setOtpAutoCopy(it) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "OTP notifications always include a one-tap Copy button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChannelLinkRow(title: String?, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
