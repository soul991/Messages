package com.messages.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messages.app.R
import com.messages.designsystem.LocalDarkTheme

/**
 * Unified Transient Message Component (§Phase C4 / G2):
 * - Dark rounded surface (no pale frosted white bar).
 * - High-contrast text (> 12:1 against 0xFF1C1C1E, well above 4.5:1 requirement).
 * - Clear 'Undo' action in green (#30D158 dark, #1E9A44 light).
 * - Close (X) icon with 48dp touch target to dismiss instantly.
 * - Sits cleanly above bottom bars with proper clearance.
 */
@Composable
fun AppTransientSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val undoGreen = if (isDark) Color(0xFF30D158) else Color(0xFF1E9A44)

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C1E),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF7F7F4),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                TextButton(
                    onClick = { data.performAction() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = undoGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            IconButton(
                onClick = { data.dismiss() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
