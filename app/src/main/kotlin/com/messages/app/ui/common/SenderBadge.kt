package com.messages.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.messages.app.R
import com.messages.protection.SenderBadges

/** The one blue used for the verified check, light and dark (AA on both
 *  surface tones; not theme-dynamic — trust chrome shouldn't restyle). */
private val VerifiedBlue = Color(0xFF1A73E8)

/**
 * Verified-sender badge chrome (Phase 2). Renders whatever
 * [SenderBadges.badgeFor] decided — no detection logic lives here.
 *
 * V2-41: the badge used to be its own click target, which meant a 16 dp tap
 * area — a third of the minimum in each axis, so a ninth of the area. When
 * [onClick] is supplied the glyph now sits inside a [minTouchTarget] box that
 * carries the click, the button role and the description, and the glyph itself
 * stays 16 dp. Reserving that space costs the title next to it some width; a
 * title ellipsizes gracefully, a missed tap does not.
 *
 * Without [onClick] this is pure decoration, so it stays glyph-sized and
 * contributes only its description to the row it is read out with.
 */
@Composable
fun SenderBadgeIcon(
    badge: SenderBadges.Badge,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
) {
    // User requested removal of blue check ticks from message senders
    if (badge == SenderBadges.Badge.VERIFIED) return

    val label = stringResource(
        when (badge) {
            SenderBadges.Badge.VERIFIED -> R.string.badge_verified_sender
            SenderBadges.Badge.BUSINESS -> R.string.badge_business_sender
        },
    )
    // `clickable`'s onClickLabel and `semantics { }` are not composable scopes.
    val explainLabel = stringResource(R.string.badge_explain)
    if (onClick == null) {
        BadgeGlyph(badge, modifier.semantics { contentDescription = label }, size)
        return
    }
    Box(
        modifier = modifier
            .minTouchTarget()
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                // Read out as "double tap to explain this badge" — the action,
                // not just the state, which is what the badge is for.
                onClickLabel = explainLabel,
            )
            // One node for the scanner and for TalkBack, rather than a
            // clickable box and a separately-described glyph inside it.
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BadgeGlyph(badge, Modifier, size)
    }
}

/** The badge's visual only: no click, no semantics, no reserved space. */
@Composable
private fun BadgeGlyph(badge: SenderBadges.Badge, modifier: Modifier, size: Dp) {
    when (badge) {
        SenderBadges.Badge.VERIFIED -> Unit
        SenderBadges.Badge.BUSINESS -> Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = modifier,
        ) {
            Text(
                stringResource(R.string.badge_business),
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
