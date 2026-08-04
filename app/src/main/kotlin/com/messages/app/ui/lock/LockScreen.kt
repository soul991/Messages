package com.messages.app.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.messages.app.R

/**
 * Full-screen lock gate (§8.2). Auto-triggers the system unlock sheet on
 * entry; the button covers cancels/errors.
 */
@Composable
fun LockScreen(
    onRequestUnlock: () -> Unit,
    title: String = "Messages is locked",
    /**
     * Shown under [title] when unlocking cannot proceed. V2-16: the gate fails
     * closed, so the user needs to be told why rather than left tapping a
     * button that can never succeed.
     */
    message: String? = null,
) {
    LaunchedEffect(Unit) { onRequestUnlock() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestUnlock) { Text(stringResource(R.string.secret_unlock)) }
        }
    }
}
