package com.messages.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.messages.designsystem.categoryPalette

/**
 * Avatar-first rows (§9): monogram avatar tinted by the conversation's
 * category — fraud red, promo amber, protected green, review slate,
 * neutral primary for the calm Inbox. AA pairs come from [categoryPalette].
 */
@Composable
fun ContactAvatar(
    name: String,
    category: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    /** Contact photo (thumbnail URI); monogram renders beneath as fallback. */
    photoUri: String? = null,
) {
    val palette = categoryPalette(category)
    val bg = palette?.container ?: MaterialTheme.colorScheme.primary
    val fg = palette?.onContainer ?: MaterialTheme.colorScheme.onPrimary
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
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
