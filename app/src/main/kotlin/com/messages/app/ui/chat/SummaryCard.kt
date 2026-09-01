package com.messages.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.app.R
import com.messages.designsystem.LocalDarkTheme
import com.messages.designsystem.Motion
import com.messages.protection.CardExtractor

private data class CardThemeColors(
    val background: Color,
    val border: Brush,
    val tint: Color,
    val badgeText: String?,
    val badgeBg: Color,
    val badgeTextColor: Color,
)

@Composable
fun SummaryCard(
    card: CardExtractor.Card,
    /** Removes one field the user says is wrong. */
    onDismissField: (CardExtractor.FieldKind) -> Unit,
    /** Hides the card for this message only. */
    onHideCard: () -> Unit,
    /** Turns the whole feature off, from the place the user is objecting to it. */
    onTurnOff: () -> Unit,
    /** The message body, so a field can point back at the words it came from. */
    body: String,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf<CardExtractor.FieldKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val direction = card.direction

    // Direction-aware colors and styling
    val themeColors = when (direction) {
        CardExtractor.Direction.CREDIT -> {
            val bg = if (isDark) Color(0xDE052E1B) else Color(0xF2F0FDF4)
            val border = if (isDark) {
                Brush.linearGradient(
                    listOf(
                        Color(0x8034D399),
                        Color(0x3310B981),
                        Color(0x10059669),
                    )
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        Color(0xB386EFAC),
                        Color(0x804ADE80),
                        Color(0x3316A34A),
                    )
                )
            }
            val tint = if (isDark) Color(0xFF34D399) else Color(0xFF15803D)
            val badge = stringResource(R.string.summary_badge_credited)
            val bBg = if (isDark) Color(0x4010B981) else Color(0x3334D399)
            val bText = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
            CardThemeColors(bg, border, tint, badge, bBg, bText)
        }
        CardExtractor.Direction.DEBIT -> {
            val bg = if (isDark) Color(0xDE2B0B0E) else Color(0xF2FFF1F2)
            val border = if (isDark) {
                Brush.linearGradient(
                    listOf(
                        Color(0x80F43F5E),
                        Color(0x33E11D48),
                        Color(0x10BE123C),
                    )
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        Color(0xB3FDA4AF),
                        Color(0x80FB7185),
                        Color(0x33E11D48),
                    )
                )
            }
            val tint = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)
            val badge = stringResource(R.string.summary_badge_debited)
            val bBg = if (isDark) Color(0x40EF4444) else Color(0x33F87171)
            val bText = if (isDark) Color(0xFFFDA4AF) else Color(0xFF9F1239)
            CardThemeColors(bg, border, tint, badge, bBg, bText)
        }
        CardExtractor.Direction.NEUTRAL -> {
            val bg = if (isDark) Color(0xDE1A1D26) else Color(0xF0F8FAFC)
            val border = if (isDark) {
                Brush.linearGradient(
                    listOf(
                        Color(0x40FFFFFF),
                        Color(0x0AFFFFFF),
                    )
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        Color(0xB3FFFFFF),
                        Color(0x1A000000),
                    )
                )
            }
            val tint = MaterialTheme.colorScheme.primary
            CardThemeColors(bg, border, tint, null, Color.Transparent, Color.Transparent)
        }
    }

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .padding(top = 4.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = themeColors.tint.copy(alpha = 0.15f),
                spotColor = themeColors.tint.copy(alpha = 0.2f),
            )
            .clip(shape)
            .background(themeColors.background, shape)
            .border(1.25.dp, themeColors.border, shape)
            .animateContentSize(animationSpec = Motion.spatialDefault()),
    ) {
        Column(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val headerIcon = when (direction) {
                    CardExtractor.Direction.CREDIT -> Icons.Outlined.CheckCircle
                    CardExtractor.Direction.DEBIT -> Icons.Outlined.Payment
                    CardExtractor.Direction.NEUTRAL -> Icons.Outlined.Summarize
                }
                Icon(
                    headerIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = themeColors.tint,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.summary_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = themeColors.tint,
                    modifier = Modifier.weight(1f),
                )

                // Direction Badge Pill
                if (themeColors.badgeText != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeColors.badgeBg)
                            .border(0.75.dp, themeColors.tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = themeColors.badgeText,
                            color = themeColors.badgeTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.summary_card_options),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.summary_action_hide)) },
                        onClick = { menuOpen = false; onHideCard() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.summary_action_turn_off)) },
                        onClick = { menuOpen = false; onTurnOff() },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Field List
            for (field in card.fields) {
                SummaryFieldRow(
                    field = field,
                    body = body,
                    expanded = expanded == field.kind,
                    direction = direction,
                    headerTint = themeColors.tint,
                    onToggle = {
                        expanded = if (expanded == field.kind) null else field.kind
                    },
                    onWrong = { onDismissField(field.kind) },
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.summary_card_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryFieldRow(
    field: CardExtractor.Field,
    body: String,
    expanded: Boolean,
    direction: CardExtractor.Direction,
    headerTint: Color,
    onToggle: () -> Unit,
    onWrong: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val label = when (field.kind) {
        CardExtractor.FieldKind.AMOUNT -> when (field.direction) {
            CardExtractor.Direction.CREDIT -> stringResource(R.string.summary_field_credited)
            CardExtractor.Direction.DEBIT -> stringResource(R.string.summary_field_debited)
            CardExtractor.Direction.NEUTRAL -> stringResource(R.string.summary_field_amount)
        }
        else -> stringResource(labelFor(field.kind))
    }

    val value = displayValue(field)
    val isAmount = field.kind == CardExtractor.FieldKind.AMOUNT
    val amountColor = when (field.direction) {
        CardExtractor.Direction.CREDIT -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
        CardExtractor.Direction.DEBIT -> if (isDark) Color(0xFFFDA4AF) else Color(0xFF9F1239)
        CardExtractor.Direction.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.spatialFast(),
        label = "chevron",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 3.dp, horizontal = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isAmount) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isAmount) headerTint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAmount) FontWeight.Bold else FontWeight.Medium,
                color = if (isAmount) amountColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp, start = 8.dp, end = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(8.dp),
            ) {
                Text(
                    field.explanation,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = headerTint,
                )
                if (field.confidence == CardExtractor.Confidence.MEDIUM) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.summary_confidence_medium),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.summary_in_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    excerpt(body, field),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = onWrong) {
                        Text(
                            stringResource(R.string.summary_action_wrong),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** The card's own rendering of a field. */
private fun displayValue(field: CardExtractor.Field): String = when (field.kind) {
    CardExtractor.FieldKind.AMOUNT -> when (field.direction) {
        CardExtractor.Direction.CREDIT -> "+ " + field.raw.trim()
        CardExtractor.Direction.DEBIT -> "- " + field.raw.trim()
        CardExtractor.Direction.NEUTRAL -> field.raw.trim()
    }
    CardExtractor.FieldKind.BALANCE -> field.raw.trim()
    CardExtractor.FieldKind.ACCOUNT_TAIL -> "••" + (field.normalized ?: field.raw)
    else -> field.normalized ?: field.raw
}

private fun labelFor(kind: CardExtractor.FieldKind): Int = when (kind) {
    CardExtractor.FieldKind.AMOUNT -> R.string.summary_field_amount
    CardExtractor.FieldKind.BALANCE -> R.string.summary_field_balance
    CardExtractor.FieldKind.ACCOUNT_TAIL -> R.string.summary_field_account
    CardExtractor.FieldKind.DUE_DATE -> R.string.summary_field_due
    CardExtractor.FieldKind.REFERENCE -> R.string.summary_field_reference
    CardExtractor.FieldKind.TRACKING -> R.string.summary_field_tracking
    CardExtractor.FieldKind.DELIVERY_STATUS -> R.string.summary_field_status
}

private const val EXCERPT_CONTEXT = 34

internal fun excerpt(body: String, field: CardExtractor.Field): AnnotatedString {
    val start = field.start.coerceIn(0, body.length)
    val end = field.end.coerceIn(start, body.length)
    if (start == end) return AnnotatedString(field.raw)
    val from = (start - EXCERPT_CONTEXT).coerceAtLeast(0)
    val to = (end + EXCERPT_CONTEXT).coerceAtMost(body.length)
    return buildAnnotatedString {
        if (from > 0) append("…")
        append(body.substring(from, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(body.substring(start, end))
        }
        append(body.substring(end, to))
        if (to < body.length) append("…")
    }
}
