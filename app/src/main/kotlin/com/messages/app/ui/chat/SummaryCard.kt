package com.messages.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.messages.app.R
import com.messages.protection.CardExtractor

/**
 * V2-51: the summary card that sits under a transactional or delivery bubble.
 *
 * It is a **reading aid, and only that**. There is no button on it that does
 * anything to the outside world — no "Pay", no dialler, no link, not even a
 * copy action on a reference. Every field is text. That restraint is the point:
 * the most effective SMS fraud is a message that looks like an institution
 * asking for something, and a card with an action button would be this app
 * lending its own credibility to whatever the sender wrote.
 *
 * The raw body stays on screen in the bubble directly above — the card never
 * replaces it. Tapping a field expands it to show *why* the extractor read the
 * message that way, together with the surrounding words from the body with the
 * matched text emphasised. That is the honest form of "view original": the card
 * points at the source rather than describing it.
 *
 * Both correction controls the finding asked for live here: a field the user
 * says is wrong disappears for that message, and the whole card can be hidden.
 * Neither is a nag — both are remembered ([MessageCards]).
 */
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.LiquidGlassSurface

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

    LiquidGlassSurface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .padding(top = 4.dp),
        shape = RoundedCornerShape(18.dp),
        depth = GlassDepth.LOW,
    ) {
        Column(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Summarize,
                    contentDescription = null,
                    modifier = Modifier.width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.summary_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.summary_card_options),
                        modifier = Modifier.width(18.dp),
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

            for (field in card.fields) {
                SummaryFieldRow(
                    field = field,
                    body = body,
                    expanded = expanded == field.kind,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onToggle: () -> Unit,
    onWrong: () -> Unit,
) {
    val label = stringResource(labelFor(field.kind))
    val value = displayValue(field)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(84.dp),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp)) {
                // Why the extractor read it this way. Every field has one; a
                // number with no stated reason is a number the card should not
                // be showing.
                Text(
                    field.explanation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (field.confidence == CardExtractor.Confidence.MEDIUM) {
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
                        Text(stringResource(R.string.summary_action_wrong))
                    }
                }
            }
        }
    }
}

/** The card's own rendering of a field — normalized where one exists, the
 *  message's own words where normalizing would have meant guessing. */
private fun displayValue(field: CardExtractor.Field): String = when (field.kind) {
    CardExtractor.FieldKind.AMOUNT, CardExtractor.FieldKind.BALANCE ->
        field.raw.trim()
    CardExtractor.FieldKind.ACCOUNT_TAIL ->
        "••" + (field.normalized ?: field.raw)
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

/**
 * The words around a field, with the matched text in bold.
 *
 * Spans come from [CardExtractor] and index into the body, but a body that
 * changed since extraction (or a bug) must not crash a chat — the range is
 * clamped, and a range that survives clamping to nothing falls back to the raw
 * text the extractor recorded.
 */
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
