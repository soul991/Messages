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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.MessagesApp
import androidx.compose.ui.res.stringResource
import com.messages.app.R

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
    val notifyTransactions by vm.notifyTransactions.collectAsStateWithLifecycle()
    val notifyPromotions by vm.notifyPromotions.collectAsStateWithLifecycle()
    val notifyReview by vm.notifyReview.collectAsStateWithLifecycle()
    val otpAutoCopy by vm.otpAutoCopy.collectAsStateWithLifecycle()

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
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            SettingsSectionHeader(stringResource(R.string.notif_section_folders))

            SettingsNavRow(
                icon = Icons.Outlined.Inbox,
                title = stringResource(R.string.category_inbox),
                subtitle = stringResource(R.string.notif_inbox_subtitle),
                onClick = { openChannelSettings(MessagesApp.CH_PERSONAL) },
                external = true,
            )

            SettingsSwitchRow(
                icon = Icons.Outlined.Receipt,
                title = stringResource(R.string.category_transactions),
                subtitle = stringResource(R.string.notif_transactions_subtitle),
                checked = notifyTransactions,
                onChange = { vm.setNotifyTransactions(it) },
            )
            if (notifyTransactions) {
                SettingsNavRow(
                    icon = null,
                    title = stringResource(R.string.notif_sound_transactions),
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_TRANSACTIONS) },
                    external = true,
                    indented = true,
                )
            }

            SettingsSwitchRow(
                icon = Icons.Outlined.LocalOffer,
                title = stringResource(R.string.category_promotions),
                subtitle = stringResource(R.string.notif_promotions_subtitle),
                checked = notifyPromotions,
                onChange = { vm.setNotifyPromotions(it) },
            )
            if (notifyPromotions) {
                SettingsNavRow(
                    icon = null,
                    title = stringResource(R.string.notif_sound_promotions),
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_PROMOTIONS) },
                    external = true,
                    indented = true,
                )
            }

            SettingsSwitchRow(
                icon = Icons.Outlined.RateReview,
                title = stringResource(R.string.notif_review_title),
                subtitle = stringResource(R.string.notif_review_subtitle),
                checked = notifyReview,
                onChange = { vm.setNotifyReview(it) },
            )
            if (notifyReview) {
                SettingsNavRow(
                    icon = null,
                    title = stringResource(R.string.notif_sound_review),
                    subtitle = null,
                    onClick = { openChannelSettings(MessagesApp.CH_REVIEW) },
                    external = true,
                    indented = true,
                )
            }

            Text(
                stringResource(R.string.notif_silent_folders_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.notif_section_protection))
            val warnDangerous by vm.warnDangerous.collectAsStateWithLifecycle()
            SettingsSwitchRow(
                icon = Icons.Outlined.GppMaybe,
                title = stringResource(R.string.notif_warn_dangerous_title),
                subtitle = stringResource(R.string.notif_warn_dangerous_subtitle),
                checked = warnDangerous,
                onChange = { vm.setWarnDangerous(it) },
            )

            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.notif_section_otp))
            SettingsSwitchRow(
                icon = Icons.Outlined.ContentCopy,
                title = stringResource(R.string.notif_otp_autocopy_title),
                subtitle = stringResource(R.string.notif_otp_autocopy_subtitle),
                checked = otpAutoCopy,
                onChange = { vm.setOtpAutoCopy(it) },
            )
            // R-30: below Android 13 the clipboard has no system-level access
            // notice and no sensitive-content masking, so any app with a
            // foreground window can read what we put there. Say so plainly
            // rather than letting the switch imply parity across versions.
            if (android.os.Build.VERSION.SDK_INT < 33) {
                Text(
                    stringResource(R.string.notif_otp_clipboard_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            Text(
                stringResource(R.string.notif_otp_copy_button_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
