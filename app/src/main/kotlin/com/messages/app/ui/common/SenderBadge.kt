package com.messages.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.messages.protection.SenderBadges

/** The one blue used for the verified check, light and dark (AA on both
 *  surface tones; not theme-dynamic — trust chrome shouldn't restyle). */
private val VerifiedBlue = Color(0xFF1A73E8)

/**
 * Verified-sender badge chrome (Phase 2). Renders whatever
 * [SenderBadges.badgeFor] decided — no detection logic lives here.
 */
@Composable
fun SenderBadgeIcon(
    badge: SenderBadges.Badge,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
) {
    val clickMod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    when (badge) {
        SenderBadges.Badge.VERIFIED -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = VerifiedBlue,
            modifier = clickMod
                .size(size)
                .semantics { contentDescription = "Verified sender" },
        )
        SenderBadges.Badge.BUSINESS -> Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = clickMod.semantics { contentDescription = "Business sender" },
        ) {
            Text(
                "Business",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** One-line explanation sheet, opened by tapping a badge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderBadgeSheet(badge: SenderBadges.Badge, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderBadgeIcon(badge, size = 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    SenderBadges.explanation(badge),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
