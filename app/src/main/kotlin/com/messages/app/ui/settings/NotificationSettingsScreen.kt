package com.messages.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.MessagesApp
import com.messages.app.R
import com.messages.designsystem.LocalDarkTheme

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
            SettingsSectionHeader("NOTIFICATION PREVIEW & THEME")
            NotificationPreviewCard()
            Spacer(Modifier.height(12.dp))

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

/**
 * Interactive Live Notification Preview Card demonstrating the Emerald/Category
 * theming, branded monogram avatar, and quick action buttons.
 */
@Composable
private fun NotificationPreviewCard() {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xF0181A22) else Color(0xFFFFFFFF)
    val emerald = Color(0xFF10B981)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = emerald.copy(alpha = 0.2f),
                spotColor = emerald.copy(alpha = 0.25f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        emerald.copy(alpha = 0.6f),
                        emerald.copy(alpha = 0.2f),
                        Color.Transparent,
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
    ) {
        Column {
            // Notification Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Messages • TRANSACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = emerald,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Now",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Notification Body with Avatar & Hero Text
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Branded Circular Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF047857),
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "AB",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "₹ 20.00 — AX-AIRBNK-S",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "₹ 20.00 debited from Airtel Payments Bank a/c Txn ID 624565526106 Bal: 1412.48",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(emerald.copy(alpha = 0.12f))
                        .border(0.75.dp, emerald.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Mark as read",
                        color = emerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Reply",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
