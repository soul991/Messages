package com.messages.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Neutral letter avatars matching Main.html and Thread.html:
 * 44dp (list) / 36dp (thread header) neutral circle (surface background),
 * 1px hairline border, muted text monogram.
 */
@Composable
fun ContactAvatar(
    name: String,
    category: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    textStyle: TextStyle = TextStyle(
        fontSize = if (size <= 38.dp) 14.sp else 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    /** Contact photo (thumbnail URI); monogram renders beneath as fallback. */
    photoUri: String? = null,
) {
    val bg = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outlineVariant
    val fg = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            // TalkBack: the monogram letter is decoration, not content —
            // the row/top-bar already announces the contact name.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                ?: name.firstOrNull()?.toString() ?: "?",
            style = textStyle,
            color = fg,
        )
        if (photoUri != null) {
            coil.compose.AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}
