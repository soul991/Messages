package com.messages.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.groupCard

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
        val pageEntrancePlayed = androidx.compose.runtime.saveable.rememberSaveable {
            androidx.compose.runtime.mutableStateOf(false)
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(250)
            pageEntrancePlayed.value = true
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSettingsPageAnimated provides pageEntrancePlayed.value
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
            SettingsSectionHeader(stringResource(R.string.notification_preview_header))
            NotificationPreviewCard()
            Spacer(Modifier.height(12.dp))

            SettingsSectionHeader(stringResource(R.string.notif_section_folders))

            SettingsGroupCard(
                footer = {
                    Text(
                        stringResource(R.string.notif_silent_folders_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            ) {
                SettingsNavRow(
                    icon = Icons.Outlined.Inbox,
                    categoryBadge = "INBOX",
                    title = stringResource(R.string.category_inbox),
                    subtitle = stringResource(R.string.notif_inbox_subtitle),
                    onClick = { openChannelSettings(MessagesApp.CH_PERSONAL) },
                    external = true,
                )
                SettingsCardDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Receipt,
                    categoryBadge = "TRANSACTIONS",
                    title = stringResource(R.string.category_transactions),
                    subtitle = stringResource(R.string.notif_transactions_subtitle),
                    checked = notifyTransactions,
                    onChange = { vm.setNotifyTransactions(it) },
                )
                if (notifyTransactions) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = null,
                        title = stringResource(R.string.notif_sound_transactions),
                        subtitle = null,
                        onClick = { openChannelSettings(MessagesApp.CH_TRANSACTIONS) },
                        external = true,
                        indented = true,
                    )
                }
                SettingsCardDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.LocalOffer,
                    categoryBadge = "PROMOTIONS",
                    title = stringResource(R.string.category_promotions),
                    subtitle = stringResource(R.string.notif_promotions_subtitle),
                    checked = notifyPromotions,
                    onChange = { vm.setNotifyPromotions(it) },
                )
                if (notifyPromotions) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = null,
                        title = stringResource(R.string.notif_sound_promotions),
                        subtitle = null,
                        onClick = { openChannelSettings(MessagesApp.CH_PROMOTIONS) },
                        external = true,
                        indented = true,
                    )
                }
                SettingsCardDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.RateReview,
                    categoryBadge = "REVIEW",
                    title = stringResource(R.string.notif_review_title),
                    subtitle = stringResource(R.string.notif_review_subtitle),
                    checked = notifyReview,
                    onChange = { vm.setNotifyReview(it) },
                )
                if (notifyReview) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = null,
                        title = stringResource(R.string.notif_sound_review),
                        subtitle = null,
                        onClick = { openChannelSettings(MessagesApp.CH_REVIEW) },
                        external = true,
                        indented = true,
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.notif_section_protection))
            val warnDangerous by vm.warnDangerous.collectAsStateWithLifecycle()
            SettingsGroupCard {
                SettingsSwitchRow(
                    icon = Icons.Outlined.GppMaybe,
                    categoryBadge = "SPAM",
                    title = stringResource(R.string.notif_warn_dangerous_title),
                    subtitle = stringResource(R.string.notif_warn_dangerous_subtitle),
                    checked = warnDangerous,
                    onChange = { vm.setWarnDangerous(it) },
                )
            }

            SettingsSectionHeader(stringResource(R.string.notif_section_otp))
            SettingsGroupCard(
                footer = {
                    Column {
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            Text(
                                stringResource(R.string.notif_otp_clipboard_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            stringResource(R.string.notif_otp_copy_button_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            ) {
                SettingsSwitchRow(
                    icon = Icons.Outlined.ContentCopy,
                    categoryBadge = null,
                    title = stringResource(R.string.notif_otp_autocopy_title),
                    subtitle = stringResource(R.string.notif_otp_autocopy_subtitle),
                    checked = otpAutoCopy,
                    onChange = { vm.setOtpAutoCopy(it) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

/**
 * Interactive Live Notification Preview Card demonstrating the Emerald/Category
 * theming, branded monogram avatar, and quick action buttons.
 */
@Composable
private fun NotificationPreviewCard() {
    val emerald = categoryPalette("TRANSACTIONS")?.tint ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Notification Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(emerald),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
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

            Spacer(Modifier.height(12.dp))

            // Notification Body with Avatar & Hero Text
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Branded Solid Circular Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(emerald),
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

            Spacer(Modifier.height(14.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = emerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, emerald.copy(alpha = 0.35f)),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Mark as read",
                            color = emerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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
}
