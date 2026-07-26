package com.messages.app.ui.secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import com.messages.core.secret.SecretCrypto

/**
 * Credential inputs for the secret locked space — PIN, password, and a drawn
 * 3×3 pattern. All three normalize to a CharArray fed to [SecretCrypto].
 */

@Composable
fun PinOrPasswordField(
    kind: String, // SecretCrypto.KIND_PIN or KIND_PASSWORD
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            if (kind == SecretCrypto.KIND_PIN) {
                if (text.all { it.isDigit() } && text.length <= 12) onValueChange(text)
            } else {
                if (text.length <= 64) onValueChange(text)
            }
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == SecretCrypto.KIND_PIN) KeyboardType.NumberPassword
            else KeyboardType.Password,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 3×3 pattern grid, system-pattern-lock semantics: drag through ≥4 distinct
 * dots; releasing commits. The drawn trace is shown while dragging and the
 * selected dots stay highlighted; the caller clears via [clearSignal].
 */
@Composable
fun PatternGrid(
    enabled: Boolean = true,
    /** Bump this counter to clear the current trace (e.g. after a wrong try). */
    clearSignal: Int = 0,
    onPattern: (List<Int>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val selected = remember(clearSignal) { mutableStateListOf<Int>() }
    var dragPoint by remember(clearSignal) { mutableStateOf<Offset?>(null) }
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary

    fun centers(size: androidx.compose.ui.geometry.Size): List<Offset> {
        val cell = size.width / 3f
        return (0..8).map { i ->
            Offset((i % 3) * cell + cell / 2f, (i / 3) * cell + cell / 2f)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .padding(8.dp)
                .pointerInput(enabled, clearSignal) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { pos ->
                            selected.clear()
                            dragPoint = pos
                        },
                        onDrag = { change, _ ->
                            dragPoint = change.position
                            val cs = centers(
                                androidx.compose.ui.geometry.Size(
                                    size.width.toFloat(), size.height.toFloat(),
                                )
                            )
                            val hitRadius = size.width / 8f
                            cs.forEachIndexed { i, c ->
                                if (i !in selected &&
                                    (change.position - c).getDistance() < hitRadius
                                ) {
                                    selected.add(i)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        },
                        onDragEnd = {
                            dragPoint = null
                            if (selected.size >= 4) onPattern(selected.toList())
                            else selected.clear()
                        },
                        onDragCancel = {
                            dragPoint = null
                            selected.clear()
                        },
                    )
                },
        ) {
            val cs = centers(size)
            val dotR = size.width / 24f
            val activeR = size.width / 14f
            // Trace lines between selected dots (+ to the live drag point).
            val path = selected.map { cs[it] }
            for (i in 1 until path.size) {
                drawLine(activeColor, path[i - 1], path[i], strokeWidth = dotR / 1.5f)
            }
            if (path.isNotEmpty() && dragPoint != null) {
                drawLine(activeColor, path.last(), dragPoint!!, strokeWidth = dotR / 1.5f)
            }
            cs.forEachIndexed { i, c ->
                if (i in selected) {
                    drawCircle(activeColor.copy(alpha = 0.25f), radius = activeR, center = c)
                    drawCircle(activeColor, radius = dotR, center = c)
                } else {
                    drawCircle(dotColor, radius = dotR, center = c)
                }
            }
        }
        Text(
            if (selected.isEmpty()) "Draw your pattern" else "${selected.size} dots",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Kind chooser labels shared by setup + change flows. */
val CREDENTIAL_KINDS = listOf(
    SecretCrypto.KIND_PIN to "PIN",
    SecretCrypto.KIND_PATTERN to "Pattern",
    SecretCrypto.KIND_PASSWORD to "Password",
)

/** Column arrangement helper used by the secret screens. */
val SecretScreenSpacing = Arrangement.spacedBy(16.dp)
