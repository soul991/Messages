package com.messages.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messages.app.R
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.groupCard

/** Page keys shared by the hub and [SettingsScreen]'s section gating. */
internal object SettingsPage {
    const val APPEARANCE = "appearance"
    const val NOTIFICATIONS = "notifications"
    const val CONVERSATIONS = "conversations"
    const val PROTECTION = "protection"
    const val PRIVACY = "privacy"
    const val BACKUP = "backup"
    const val STORAGE = "storage"
}

private enum class Tone { PRIMARY, PROMO, REVIEW, PROTECTED }

private data class HubEntry(
    val icon: ImageVector,
    val tone: Tone,
    val title: Int,
    val subtitle: Int,
    val page: String?, // null = leaves Settings (About)
)

/**
 * Settings hub: grouped, icon-tiled entry points. Each entry opens one slice
 * of the existing settings content (same state, same rows) instead of
 * scrolling a single 14-section list.
 */
@Composable
internal fun SettingsHub(onPick: (String?) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        DefaultSmsStatusCard()
        HubGroup(
            R.string.settings_hub_personalize,
            listOf(
                HubEntry(Icons.Outlined.Palette, Tone.PRIMARY, R.string.settings_section_appearance, R.string.settings_hub_appearance_sub, SettingsPage.APPEARANCE),
                HubEntry(Icons.Outlined.Notifications, Tone.PROMO, R.string.settings_section_notifications, R.string.settings_hub_notifications_sub, SettingsPage.NOTIFICATIONS),
                HubEntry(Icons.Outlined.Chat, Tone.REVIEW, R.string.settings_section_conversations, R.string.settings_hub_conversations_sub, SettingsPage.CONVERSATIONS),
            ),
            onPick,
        )
        HubGroup(
            R.string.settings_hub_protect,
            listOf(
                HubEntry(Icons.Outlined.Shield, Tone.PROTECTED, R.string.settings_hub_protection, R.string.settings_hub_protection_sub, SettingsPage.PROTECTION),
                HubEntry(Icons.Outlined.Lock, Tone.PRIMARY, R.string.settings_section_privacy, R.string.settings_hub_privacy_sub, SettingsPage.PRIVACY),
            ),
            onPick,
        )
        HubGroup(
            R.string.settings_hub_data,
            listOf(
                HubEntry(Icons.Outlined.Cloud, Tone.REVIEW, R.string.settings_hub_backup, R.string.settings_hub_backup_sub, SettingsPage.BACKUP),
                HubEntry(Icons.Outlined.DeleteSweep, Tone.PROMO, R.string.settings_hub_storage, R.string.settings_hub_storage_sub, SettingsPage.STORAGE),
            ),
            onPick,
        )
        HubGroup(
            R.string.settings_hub_about_group,
            listOf(
                HubEntry(Icons.Outlined.Info, Tone.PRIMARY, R.string.settings_about, R.string.settings_about_subtitle, null),
            ),
            onPick,
        )
    }
}

@Composable
private fun HubGroup(label: Int, entries: List<HubEntry>, onPick: (String?) -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.groupCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            entries.forEachIndexed { i, e ->
                HubRow(e, onClick = { onPick(e.page) })
                if (i < entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 74.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HubRow(e: HubEntry, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg: Color, fg: Color) = when (e.tone) {
        Tone.PRIMARY -> cs.primaryContainer to cs.onPrimaryContainer
        Tone.PROMO -> categoryPalette("PROMOTIONS")?.let { it.container to it.onContainer }
            ?: (cs.tertiaryContainer to cs.onTertiaryContainer)
        Tone.REVIEW -> categoryPalette("REVIEW")?.let { it.container to it.onContainer }
            ?: (cs.secondaryContainer to cs.onSecondaryContainer)
        Tone.PROTECTED -> categoryPalette("TRANSACTIONS")?.let { it.container to it.onContainer }
            ?: (cs.primaryContainer to cs.onPrimaryContainer)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(e.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(e.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
        )
    }
}

/** Green status card shown while Messages is the default SMS app (hidden otherwise). */
@Composable
private fun DefaultSmsStatusCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDefault = remember(context) {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
    if (!isDefault) return
    val palette = categoryPalette("TRANSACTIONS")
    val container = palette?.container ?: MaterialTheme.colorScheme.primaryContainer
    val onContainer = palette?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer
    val tint = palette?.tint ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = container,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_default_sms_on),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                Text(
                    stringResource(R.string.settings_default_sms_on_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}
