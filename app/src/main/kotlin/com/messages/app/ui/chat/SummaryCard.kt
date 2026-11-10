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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import com.messages.designsystem.groupCard
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
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.Motion
import com.messages.protection.CardExtractor

import com.messages.designsystem.DesignTokens
import com.messages.designsystem.LocalDarkTheme

private data class CardThemeColors(
    val background: Color,
    val borderColor: Color,
    val tint: Color,
    val badgeText: String?,
    val badgeBg: Color,
    val badgeBorder: Color,
    val badgeTextColor: Color,
    val amountColor: Color,
)

@Composable
fun SummaryCard(
    card: CardExtractor.Card,
    /** Removes one field the user says is wrong. */
    onDismissField: (CardExtractor.FieldKind) -> Unit,
    /** Hides the card for this message only. */
    onHideCard: () -> Unit,
    /** Deprecated: moved to conversation overflow menu. */
    onTurnOff: () -> Unit = {},
    /** The message body, so a field can point back at the words it came from. */
    body: String,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf<CardExtractor.FieldKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val cs = MaterialTheme.colorScheme
    val direction = card.direction

    // Direction-aware translucent tinted glass styling
    // Source of truth: design-tokens.json & Thread.html
    val themeColors = when (direction) {
        CardExtractor.Direction.CREDIT -> {
            val bg = DesignTokens.SummaryCards.Credit.Fill // rgba(48,209,88,0.10)
            val border = DesignTokens.SummaryCards.Credit.Border // rgba(48,209,88,0.28)
            val tint = DesignTokens.SummaryCards.Credit.Text // #6EE7A0
            val badge = stringResource(R.string.summary_badge_credited)
            val bBg = DesignTokens.SummaryCards.Credit.BadgeFill // rgba(48,209,88,0.12)
            val bBorder = DesignTokens.SummaryCards.Credit.BadgeBorder // rgba(48,209,88,0.40)
            val bText = tint
            CardThemeColors(bg, border, tint, badge, bBg, bBorder, bText, amountColor = tint)
        }
        CardExtractor.Direction.DEBIT -> {
            val bg = DesignTokens.SummaryCards.Debit.Fill // rgba(255,69,58,0.10)
            val border = DesignTokens.SummaryCards.Debit.Border // rgba(255,69,58,0.28)
            val tint = DesignTokens.SummaryCards.Debit.Text // #FF8A80
            val badge = stringResource(R.string.summary_badge_debited)
            val bBg = DesignTokens.SummaryCards.Debit.BadgeFill // rgba(255,69,58,0.12)
            val bBorder = DesignTokens.SummaryCards.Debit.BadgeBorder // rgba(255,69,58,0.40)
            val bText = tint
            CardThemeColors(bg, border, tint, badge, bBg, bBorder, bText, amountColor = tint)
        }
        CardExtractor.Direction.NEUTRAL -> {
            val bg = if (isDark) DesignTokens.SummaryCards.Neutral.DarkFill else DesignTokens.SummaryCards.Neutral.LightFill
            val border = if (isDark) DesignTokens.SummaryCards.Neutral.DarkBorder else DesignTokens.SummaryCards.Neutral.LightBorder
            val tint = cs.onSurface
            CardThemeColors(bg, border, tint, null, Color.Transparent, Color.Transparent, Color.Transparent, amountColor = cs.onSurface)
        }
    }

    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .padding(top = 4.dp)
            .animateContentSize(animationSpec = Motion.spatialDefault()),
        shape = shape,
        color = themeColors.background,
        border = BorderStroke(1.dp, themeColors.borderColor),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.summary_card_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeColors.tint,
                    modifier = Modifier.weight(1f),
                )

                // Direction Badge Pill: e.g. CREDITED / DEBITED
                if (themeColors.badgeText != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeColors.badgeBg)
                            .border(1.dp, themeColors.badgeBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = themeColors.badgeText,
                            color = themeColors.badgeTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }

                // Anchored Three-Dot Menu
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.summary_card_options),
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) DesignTokens.Dark.TextMuted else DesignTokens.Light.TextMuted,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(8.dp),
                        containerColor = cs.surface,
                        border = BorderStroke(1.dp, cs.outlineVariant),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.summary_action_hide),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onHideCard()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Field List
            for (field in card.fields) {
                SummaryFieldRow(
                    field = field,
                    body = body,
                    expanded = expanded == field.kind,
                    direction = direction,
                    headerTint = themeColors.tint,
                    amountColor = themeColors.amountColor,
                    onToggle = {
                        expanded = if (expanded == field.kind) null else field.kind
                    },
                    onWrong = { onDismissField(field.kind) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.summary_card_footer),
                fontSize = 11.sp,
                color = if (isDark) DesignTokens.Dark.TextMuted else DesignTokens.Light.TextMuted,
                lineHeight = 14.sp,
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
    amountColor: Color,
    onToggle: () -> Unit,
    onWrong: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val mutedColor = if (isDark) DesignTokens.Dark.TextMuted else DesignTokens.Light.TextMuted
    val normalTextColor = MaterialTheme.colorScheme.onSurface

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

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.spatialFast(),
        label = "chevron",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = if (isAmount) 4.dp else 3.dp),
    ) {
        Row(
            verticalAlignment = if (isAmount) Alignment.Bottom else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = mutedColor,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    fontSize = if (isAmount) 20.sp else 13.sp,
                    fontWeight = if (isAmount) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isAmount) amountColor else normalTextColor,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = mutedColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                )
            }
        }

        // Expanded Evidence Panel
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
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
                        color = mutedColor,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.summary_in_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    excerpt(body, field),
                    style = MaterialTheme.typography.bodySmall,
                    color = normalTextColor,
                )
                Spacer(Modifier.height(4.dp))
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

/**
 * Trims excerpt snippets strictly at word boundaries.
 */
internal fun excerpt(body: String, field: CardExtractor.Field): AnnotatedString {
    val start = field.start.coerceIn(0, body.length)
    val end = field.end.coerceIn(start, body.length)
    if (start == end) return AnnotatedString(field.raw)

    var from = (start - EXCERPT_CONTEXT).coerceAtLeast(0)
    if (from > 0) {
        // Find next word boundary so we don't start mid-word
        val spaceIndex = body.indexOfAny(charArrayOf(' ', '\t', '\n'), startIndex = from)
        if (spaceIndex in from until start) {
            from = spaceIndex + 1
        }
    }

    var to = (end + EXCERPT_CONTEXT).coerceAtMost(body.length)
    if (to < body.length) {
        // Find previous word boundary so we don't end mid-word
        val spaceIndex = body.lastIndexOfAny(charArrayOf(' ', '\t', '\n'), startIndex = to)
        if (spaceIndex in end..to) {
            to = spaceIndex
        }
    }

    return buildAnnotatedString {
        if (from > 0) append("… ")
        append(body.substring(from, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(body.substring(start, end))
        }
        append(body.substring(end, to))
        if (to < body.length) append(" …")
    }
}
