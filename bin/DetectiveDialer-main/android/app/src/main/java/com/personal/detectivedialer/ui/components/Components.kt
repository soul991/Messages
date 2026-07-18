package com.personal.detectivedialer.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.detectivedialer.ui.CategoryUi
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Circular avatar with a colored initial — contact-photo style without
 * contact photos. Severe (spam/blocked) callers get the red treatment.
 */
@Composable
fun InitialAvatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onColor: Color = Color.White,
) {
    val initial = name.trim().firstOrNull { it.isLetter() }?.uppercaseChar()
        ?: name.trim().firstOrNull { it.isDigit() }
        ?: '#'
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial.toString(),
            color = onColor,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Deterministic avatar tint for non-severe callers. */
private val AvatarPalette = listOf(
    Color(0xFF0099FF), Color(0xFF7E57C2), Color(0xFF26A69A),
    Color(0xFFF57C00), Color(0xFF5C6BC0), Color(0xFF2E9E5B),
)

fun avatarColorFor(seed: String): Color =
    AvatarPalette[(seed.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]

/** Small rounded verdict chip: solid red for spam/blocked, tinted otherwise. */
@Composable
fun VerdictChip(category: CategoryUi, modifier: Modifier = Modifier) {
    val bg = if (category.severe) category.color else category.color.copy(alpha = 0.14f)
    val fg = if (category.severe) Color.White else category.color
    Text(
        text = category.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Sticky-style day header: Today / Yesterday / weekday+date. */
@Composable
fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Buckets a timestamp into Today / Yesterday / Previous for list grouping. */
fun dayLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = { a: Calendar, b: Calendar ->
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
    if (sameDay(now, then)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday"
    return "Previous"
}

/**
 * Actual call time for history rows: clock time for Today/Yesterday,
 * date + clock time for Previous. Honors the device's 12/24-hour setting.
 */
fun callTimeLabel(context: Context, timestamp: Long): String {
    val time = android.text.format.DateFormat.getTimeFormat(context)
        .format(java.util.Date(timestamp))
    return when (dayLabel(timestamp)) {
        "Today", "Yesterday" -> time
        else -> "${android.text.format.DateFormat.format("d MMM yyyy", timestamp)}, $time"
    }
}

/** Shared empty state: big muted icon, title, hint. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun AddNumberDialog(
    title: String,
    showLabel: Boolean = false,
    onConfirm: (number: String, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                )
                if (showLabel) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (number.isNotBlank()) onConfirm(number.trim(), label.trim()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Conversational relative time for message lists: "just now", "5 min ago",
 * clock time earlier today, "Yesterday", then the date.
 */
fun chatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0) return "now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        dayLabel(timestamp) == "Today" ->
            android.text.format.DateFormat.format("h:mm a", timestamp).toString()
        dayLabel(timestamp) == "Yesterday" -> "Yesterday"
        else -> android.text.format.DateFormat.format("d MMM", timestamp).toString()
    }
}

/** Day divider label inside a conversation: Today / Yesterday / full date. */
fun conversationDayLabel(timestamp: Long): String = when (dayLabel(timestamp)) {
    "Today" -> "Today"
    "Yesterday" -> "Yesterday"
    else -> android.text.format.DateFormat.format("EEEE, d MMM yyyy", timestamp).toString()
}

/** Compact relative time like "5m", "2h", "3d". */
fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
