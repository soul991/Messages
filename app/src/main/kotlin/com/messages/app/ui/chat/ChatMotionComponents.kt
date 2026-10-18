package com.messages.app.ui.chat

import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.designsystem.Haptics
import com.messages.designsystem.Motion
import com.messages.designsystem.groupCard

/**
 * Flagship motion & rich media components (Google Messages & Apple iMessage parity).
 */

/** Helper: checks if text consists strictly of 1 to 3 emojis. */
fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    var emojiCount = 0
    var i = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        val charCount = Character.charCount(cp)
        val isEmoji = (cp in 0x1F600..0x1F64F) ||
                (cp in 0x1F300..0x1F5FF) ||
                (cp in 0x1F680..0x1F6FF) ||
                (cp in 0x1F700..0x1F77F) ||
                (cp in 0x1F780..0x1F7FF) ||
                (cp in 0x1F800..0x1F8FF) ||
                (cp in 0x1F900..0x1F9FF) ||
                (cp in 0x1FA00..0x1FA6F) ||
                (cp in 0x1FA70..0x1FAFF) ||
                (cp in 0x2600..0x26FF) ||
                (cp in 0x2700..0x27BF) ||
                (cp in 0xFE00..0xFE0F) ||
                (cp == 0x200D)
        if (!isEmoji) return false
        if (cp !in 0xFE00..0xFE0F && cp != 0x200D) {
            emojiCount++
        }
        i += charCount
    }
    return emojiCount in 1..3
}

data class QuotedReply(val sender: String, val quote: String, val cleanBody: String)

fun parseReply(body: String): QuotedReply? {
    if (!body.startsWith("[reply:")) return null
    val endIdx = body.indexOf("] ")
    if (endIdx < 0) return null
    val meta = body.substring(7, endIdx)
    val parts = meta.split("|", limit = 2)
    val sender = parts.getOrNull(0) ?: "Reply"
    val quote = parts.getOrNull(1) ?: ""
    val clean = body.substring(endIdx + 2)
    return QuotedReply(sender, quote, clean)
}

data class VoiceNoteMeta(val duration: String, val waveforms: List<Int>)

fun parseVoiceNote(body: String): VoiceNoteMeta? {
    if (!body.startsWith("[voice:")) return null
    val endIdx = body.indexOf("]")
    if (endIdx < 0) return null
    val content = body.substring(7, endIdx)
    val parts = content.split("|", limit = 2)
    val dur = parts.getOrNull(0) ?: "0:15"
    val waves = parts.getOrNull(1)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        ?: listOf(10, 15, 20, 12, 18, 25, 14, 10, 16, 22, 28, 15)
    return VoiceNoteMeta(dur, waves)
}

/**
 * Sleek Quoted Reply banner inside a message bubble.
 */
@Composable
fun QuotedReplyCard(
    reply: QuotedReply,
    isOut: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isOut) Color.Black.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = reply.sender,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                )
                Text(
                    text = reply.quote,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOut) accentColor.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Interactive Audio Message Waveform Player.
 */
@Composable
fun VoiceNotePlayer(
    voice: VoiceNoteMeta,
    isOut: Boolean,
    outBubbleColors: Pair<Color, Color>,
    view: View,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0.45f) }

    val fgColor = if (isOut) outBubbleColors.second else MaterialTheme.colorScheme.onSurface
    val primaryColor = if (isOut) outBubbleColors.second else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = {
                Haptics.tick(view)
                isPlaying = !isPlaying
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = primaryColor,
                contentColor = if (isOut) outBubbleColors.first else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        // Dynamic Waveform Bars
        Row(
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            voice.waveforms.forEachIndexed { idx, heightVal ->
                val progressFrac = idx.toFloat() / voice.waveforms.size.toFloat()
                val isPlayed = progressFrac <= playProgress
                val barHeight = (heightVal.coerceIn(6, 30)).dp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (isPlayed) primaryColor
                            else fgColor.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = voice.duration,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
        )
    }
}

/**
 * Animated 3-dot typing indicator bubble.
 */
@Composable
fun TypingIndicatorBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing-wave")
    val dotCount = 3
    val delays = listOf(0, 150, 300)

    Row(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomEnd = 20.dp,
                    bottomStart = 4.dp
                )
            )
            .background(MaterialTheme.colorScheme.groupCard)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomEnd = 20.dp,
                    bottomStart = 4.dp
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        delays.forEachIndexed { idx, delay ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-wave-$idx",
            )

            Box(
                Modifier
                    .offset(y = offsetY.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
            )
        }
    }
}
