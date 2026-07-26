package com.messages.app.ui.secret

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    // Password entry (Phase 7): proper M3 field with a reveal toggle.
    var revealed by remember { mutableStateOf(false) }
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
        visualTransformation = if (revealed) androidx.compose.ui.text.input.VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = if (kind == SecretCrypto.KIND_PASSWORD) {
            {
                androidx.compose.material3.IconButton(onClick = { revealed = !revealed }) {
                    androidx.compose.material3.Icon(
                        if (revealed) androidx.compose.material.icons.Icons.Filled.VisibilityOff
                        else androidx.compose.material.icons.Icons.Filled.Visibility,
                        contentDescription = if (revealed) "Hide password" else "Show password",
                    )
                }
            }
        } else null,
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
 * Vault PIN entry (Phase 7 unlock screen): real dot indicators that fill with
 * a spatial spring and shake horizontally on a wrong code. A hidden
 * BasicTextField carries focus/IME (NumberPassword); the dots are the only
 * visible surface. Bump [errorSignal] to trigger the shake.
 */
@Composable
fun PinDotsEntry(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    errorSignal: Int = 0,
    onDone: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val shakeX = remember { androidx.compose.animation.core.Animatable(0f) }

    // Wrong code → horizontal shake on the spring system (spatial fast).
    LaunchedEffect(errorSignal) {
        if (errorSignal == 0) return@LaunchedEffect
        listOf(-14f, 11f, -7f, 4f, 0f).forEach { target ->
            shakeX.animateTo(target, com.messages.designsystem.Motion.spatialFast())
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { text ->
                if (text.all { it.isDigit() } && text.length <= 12) onValueChange(text)
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    // Keep the (invisible) field laid out for IME plumbing.
                    Box(Modifier.height(1.dp)) { inner() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = shakeX.value }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val slots = maxOf(4, value.length + if (value.length < 12) 1 else 0)
                        repeat(slots) { i ->
                            val filled = i < value.length
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (filled) 1f else 0.55f,
                                animationSpec = com.messages.designsystem.Motion.spatialDefault(),
                                label = "pin-dot-$i",
                            )
                            Box(
                                Modifier
                                    .padding(horizontal = 7.dp)
                                    .size(14.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .background(
                                        color = if (filled) primary else idle.copy(alpha = 0.35f),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    ),
                            )
                        }
                    }
                }
            },
        )
        Text(
            "Tap to type your PIN",
            style = MaterialTheme.typography.bodySmall,
            color = idle,
            modifier = Modifier.clickable { focusRequester.requestFocus() },
        )
    }
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
    // Phase 7: light tick per captured node (design-system haptic), accent
    // nodes, rounded-cap stroke with a fading tail.
    val view = androidx.compose.ui.platform.LocalView.current
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
                                    com.messages.designsystem.Haptics.tick(view)
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
            // Trace: rounded caps, tail fading out behind the finger — the
            // newest segment is full-strength accent, older ones recede.
            val path = selected.map { cs[it] }
            val segments = path.size - 1
            for (i in 1 until path.size) {
                val age = (segments - i).coerceAtLeast(0)
                val alpha = (1f - age * 0.22f).coerceAtLeast(0.28f)
                drawLine(
                    color = activeColor.copy(alpha = alpha),
                    start = path[i - 1], end = path[i],
                    strokeWidth = dotR / 1.4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            if (path.isNotEmpty() && dragPoint != null) {
                drawLine(
                    color = activeColor,
                    start = path.last(), end = dragPoint!!,
                    strokeWidth = dotR / 1.4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            cs.forEachIndexed { i, c ->
                if (i in selected) {
                    drawCircle(activeColor.copy(alpha = 0.25f), radius = activeR, center = c)
                    drawCircle(activeColor, radius = dotR, center = c)
                } else {
                    drawCircle(dotColor.copy(alpha = 0.6f), radius = dotR, center = c)
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

/**
 * The ONE credential-creation UI, shared verbatim by first-time setup and
 * the in-space "change secret code" flow (so changing the code always offers
 * the full type re-pick — PIN / pattern / password — exactly like setup):
 * kind chooser → enter → confirm. Calls [onChosen] once the confirmation
 * matches; validation floors come from [SecretCrypto.setupError].
 */
@Composable
fun CredentialCreationSteps(
    heading: String = "Choose a secret code",
    subtitle: String? = null,
    working: Boolean = false,
    onChosen: (kind: String, credential: CharArray) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(SecretCrypto.KIND_PIN) }
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var patternClear by remember { mutableStateOf(0) }

    fun chosenCredential(): CharArray =
        if (kind == SecretCrypto.KIND_PATTERN) SecretCrypto.patternToCredential(firstPattern)
        else first.toCharArray()

    fun advance(credential: CharArray) {
        val setupError = SecretCrypto.setupError(kind, credential)
        if (setupError != null) {
            error = setupError
            return
        }
        error = null
        confirming = true
        patternClear++
    }

    Column(verticalArrangement = SecretScreenSpacing) {
        Text(
            if (!confirming) heading else "Confirm your secret code",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!confirming && subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!confirming) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CREDENTIAL_KINDS.forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = {
                            kind = k; first = ""; error = null
                            firstPattern = emptyList(); patternClear++
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, CREDENTIAL_KINDS.size),
                    ) { Text(label) }
                }
            }
            if (kind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    firstPattern = cells
                    advance(SecretCrypto.patternToCredential(cells))
                }
            } else {
                PinOrPasswordField(
                    kind = kind, value = first, onValueChange = { first = it },
                    label = if (kind == SecretCrypto.KIND_PIN) "Enter a PIN (4+ digits)"
                    else "Enter a password (4+ characters)",
                    enabled = !working,
                    isError = error != null,
                    onDone = { advance(first.toCharArray()) },
                )
                Button(
                    onClick = { advance(first.toCharArray()) },
                    enabled = first.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next") }
            }
        } else {
            if (kind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    if (cells == firstPattern) {
                        error = null
                        onChosen(kind, chosenCredential())
                    } else {
                        error = "Patterns don't match — try again"
                        patternClear++
                    }
                }
            } else {
                PinOrPasswordField(
                    kind = kind, value = confirm, onValueChange = { confirm = it },
                    label = "Re-enter to confirm",
                    enabled = !working,
                    isError = error != null,
                    onDone = {},
                )
                Button(
                    onClick = {
                        if (confirm == first) {
                            error = null
                            onChosen(kind, chosenCredential())
                        } else {
                            error = "Codes don't match — try again"; confirm = ""
                        }
                    },
                    enabled = confirm.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm") }
            }
            TextButton(
                onClick = {
                    confirming = false; first = ""; confirm = ""
                    firstPattern = emptyList(); error = null; patternClear++
                },
                enabled = !working,
            ) { Text("Start over") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
