package com.messages.app.ui.secret

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.messages.app.R
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
                        contentDescription = stringResource(
                            if (revealed) R.string.secret_hide_password
                            else R.string.secret_show_password,
                        ),
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
            stringResource(R.string.secret_tap_to_type_pin),
            style = MaterialTheme.typography.bodySmall,
            color = idle,
            modifier = Modifier.clickable { focusRequester.requestFocus() },
        )
    }
}

/** Minimum dots in a valid pattern — the floor the drag and tap paths share. */
const val PATTERN_MIN_DOTS = 4

/**
 * V2-38. What tapping dot [index] does to the current selection.
 *
 * Pure so the rule can be tested without a gesture. Appending is the ordinary
 * case; tapping a dot that is already in the pattern truncates back to just
 * before it, which is the only undo a screen-reader user can reach — with a
 * drag, lifting the finger is the undo, and there is no lift here.
 */
fun patternAfterTap(selected: List<Int>, index: Int): List<Int> {
    val at = selected.indexOf(index)
    return if (at < 0) selected + index else selected.take(at)
}

/**
 * Row/column of dot [index], 1-based — how a person describes a grid.
 *
 * V2-36. The arithmetic is what the tests care about and what must stay true in
 * every language; the sentence built from it lives in the resource table. A
 * screen-reader user navigating a 3×3 grid hears this nine times, so it is not
 * a string that can be left in English.
 */
fun patternDotRowCol(index: Int): Pair<Int, Int> = (index / 3 + 1) to (index % 3 + 1)

/** [patternDotRowCol] as the label a screen reader reads out. */
fun patternDotLabel(context: Context, index: Int): String {
    val (row, column) = patternDotRowCol(index)
    return context.getString(R.string.secret_pattern_dot, row, column)
}

/**
 * 3×3 pattern grid, system-pattern-lock semantics: drag through ≥4 distinct
 * dots; releasing commits. The drawn trace is shown while dragging and the
 * selected dots stay highlighted; the caller clears via [clearSignal].
 *
 * V2-38: the grid was a bare [Canvas] with [detectDragGestures] — one node with
 * no children, no actions and no state, so TalkBack, Switch Access, a D-pad and
 * a keyboard could not enter a pattern at all. Since this is the credential for
 * the locked space, that is not a degraded experience, it is a locked door.
 *
 * The fix is an overlay of nine real semantic nodes on top of the canvas, each
 * focusable, each carrying its position, its selection state and a click action.
 * They deliberately declare **no pointer input**: `Modifier.clickable` would
 * consume the down event and break the drag for everyone else. Semantics
 * actions and key events do not touch the pointer pipeline, so both input
 * methods drive the same [selected] list and neither knows about the other.
 *
 * Privacy note: exploring the grid by touch makes TalkBack speak the position
 * of each dot, so a pattern entered this way is audible to anyone nearby. That
 * is inherent to operating a spatial credential non-visually and is what the
 * platform's "Speak passwords" setting (headphones-only speech) exists for. The
 * status line below the grid therefore reports the dot *count* only, never
 * which dots, and the caller's error text never echoes the pattern.
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

    fun tap(index: Int) {
        if (!enabled) return
        val next = patternAfterTap(selected.toList(), index)
        selected.clear()
        selected.addAll(next)
        com.messages.designsystem.Haptics.tick(view)
    }

    fun commit() {
        if (!enabled || selected.size < PATTERN_MIN_DOTS) return
        onPattern(selected.toList())
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .padding(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
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
                                if (selected.size >= PATTERN_MIN_DOTS) onPattern(selected.toList())
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

            // The accessible/keyboard surface. Cells are laid out on the same
            // uniform 3×3 division the canvas uses, so each node sits exactly
            // over the dot it represents and touch exploration lands where a
            // sighted user would tap.
            Column(Modifier.matchParentSize()) {
                repeat(3) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(3) { column ->
                            val index = row * 3 + column
                            PatternDotTarget(
                                index = index,
                                position = selected.indexOf(index),
                                enabled = enabled,
                                onTap = ::tap,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        // Tap-entry controls. Always present rather than shown only under a
        // detected screen reader: touch exploration is not on for a D-pad or a
        // physical keyboard, and a control that appears only for some users is
        // a control nobody can be told about.
        Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { selected.clear() },
                enabled = enabled && selected.isNotEmpty(),
            ) { Text(stringResource(R.string.action_clear)) }
            TextButton(
                onClick = { commit() },
                enabled = enabled && selected.size >= PATTERN_MIN_DOTS,
            ) { Text(stringResource(R.string.action_done)) }
        }

        Text(
            patternStatusText(LocalContext.current, selected.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                // Announces progress as it changes. Count only — never which
                // dots, and never the pattern itself.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The three states of the pattern-progress line under the grid. */
enum class PatternStatus { EMPTY, BELOW_MINIMUM, READY }

/**
 * Which status applies at [dots].
 *
 * V2-36. Which state applies is the tested part; the counting words live in
 * the resource table. The line is spoken aloud on every change, so it must
 * count the dots and never name them.
 */
fun patternStatus(dots: Int): PatternStatus = when {
    dots == 0 -> PatternStatus.EMPTY
    dots < PATTERN_MIN_DOTS -> PatternStatus.BELOW_MINIMUM
    else -> PatternStatus.READY
}

/** [patternStatus] as the sentence read out under the grid. */
fun patternStatusText(context: Context, dots: Int): String = when (patternStatus(dots)) {
    PatternStatus.EMPTY -> context.getString(R.string.secret_pattern_status_empty)
    PatternStatus.BELOW_MINIMUM ->
        context.resources.getQuantityString(
            R.plurals.secret_pattern_status_progress,
            PATTERN_MIN_DOTS, dots, PATTERN_MIN_DOTS,
        )
    PatternStatus.READY ->
        context.resources.getQuantityString(R.plurals.secret_pattern_status_ready, dots, dots)
}

/**
 * One dot of the accessible overlay.
 *
 * Carries semantics and focus only. No [androidx.compose.foundation.clickable]:
 * that would consume the pointer down and kill the drag path underneath it.
 * Key handling is explicit for the same reason — D-pad centre / Enter / Space
 * activate it without any pointer involvement.
 */
@Composable
private fun PatternDotTarget(
    index: Int,
    position: Int,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = position >= 0
    // `semantics { }` is not a composable scope, so every phrase it reads is
    // resolved out here first.
    val dotLabel = patternDotLabel(LocalContext.current, index)
    // Position, not identity: "3rd dot" tells the user where they are in their
    // own pattern without naming anyone else's.
    val dotState = if (chosen) {
        stringResource(R.string.secret_pattern_dot_selected, position + 1)
    } else {
        stringResource(R.string.secret_pattern_dot_unselected)
    }
    val dotAction = stringResource(
        if (chosen) R.string.secret_pattern_dot_remove else R.string.secret_pattern_dot_add,
    )
    Box(
        modifier
            .focusable(enabled)
            .onKeyEvent { event ->
                if (!enabled) return@onKeyEvent false
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                val activates = event.key == Key.Enter ||
                    event.key == Key.NumPadEnter ||
                    event.key == Key.Spacebar ||
                    event.key == Key.DirectionCenter
                if (activates) { onTap(index); true } else false
            }
            .semantics {
                contentDescription = dotLabel
                role = Role.Checkbox
                selected = chosen
                stateDescription = dotState
                onClick(label = dotAction) { onTap(index); true }
            },
    )
}

/** Kind chooser labels shared by setup + change flows. */
val CREDENTIAL_KINDS = listOf(
    SecretCrypto.KIND_PIN to R.string.secret_kind_pin,
    SecretCrypto.KIND_PATTERN to R.string.secret_kind_pattern,
    SecretCrypto.KIND_PASSWORD to R.string.secret_kind_password,
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
    heading: String? = null,
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
    // Raised from onClick / PatternGrid callbacks, neither of which is a
    // composable scope.
    val context = LocalContext.current
    val patternMismatch = stringResource(R.string.secret_patterns_dont_match)
    val codeMismatch = stringResource(R.string.secret_codes_dont_match)

    fun chosenCredential(): CharArray =
        if (kind == SecretCrypto.KIND_PATTERN) SecretCrypto.patternToCredential(firstPattern)
        else first.toCharArray()

    fun advance(credential: CharArray) {
        val setupError = SecretCrypto.setupError(kind, credential)
        if (setupError != null) {
            error = context.getString(setupErrorRes(setupError))
            return
        }
        error = null
        confirming = true
        patternClear++
    }

    Column(verticalArrangement = SecretScreenSpacing) {
        Text(
            if (!confirming) {
                heading ?: stringResource(R.string.secret_choose_code)
            } else {
                stringResource(R.string.secret_confirm_code)
            },
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
                CREDENTIAL_KINDS.forEachIndexed { i, (k, labelRes) ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = {
                            kind = k; first = ""; error = null
                            firstPattern = emptyList(); patternClear++
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, CREDENTIAL_KINDS.size),
                    ) { Text(stringResource(labelRes)) }
                }
            }
            // V2-50: pattern is operable with a screen reader (V2-38 gave every
            // dot a label, a state and an action) but it is not comfortable —
            // nine unlabelled positions in a remembered order, no echo of what
            // you have entered, a 30-second timeout, and a cooldown for getting
            // it wrong. So when a screen reader is driving the UI, name the two
            // methods the platform's own text input already handles. All three
            // stay selectable: this is advice, not a restriction.
            if (CredentialAccessibility.touchExplorationOn(context)) {
                Text(
                    stringResource(R.string.secret_setup_accessible_methods_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // V2-7: say what the lock does and does not protect against, at the
            // moment the choice is being made. The verifier travels inside
            // backups, so a PIN is attackable offline where the cooldown cannot
            // reach — the user deserves to know that before picking one.
            Text(
                stringResource(R.string.secret_setup_strength_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (kind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    firstPattern = cells
                    advance(SecretCrypto.patternToCredential(cells))
                }
            } else {
                PinOrPasswordField(
                    kind = kind, value = first, onValueChange = { first = it },
                    label = stringResource(
                        if (kind == SecretCrypto.KIND_PIN) R.string.secret_enter_pin_hint
                        else R.string.secret_enter_password_hint,
                    ),
                    enabled = !working,
                    isError = error != null,
                    onDone = { advance(first.toCharArray()) },
                )
                Button(
                    onClick = { advance(first.toCharArray()) },
                    enabled = first.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_next)) }
            }
        } else {
            if (kind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    if (cells == firstPattern) {
                        error = null
                        onChosen(kind, chosenCredential())
                    } else {
                        error = patternMismatch
                        patternClear++
                    }
                }
            } else {
                PinOrPasswordField(
                    kind = kind, value = confirm, onValueChange = { confirm = it },
                    label = stringResource(R.string.secret_reenter_to_confirm),
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
                            error = codeMismatch; confirm = ""
                        }
                    },
                    enabled = confirm.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.secret_confirm)) }
            }
            TextButton(
                onClick = {
                    confirming = false; first = ""; confirm = ""
                    firstPattern = emptyList(); error = null; patternClear++
                },
                enabled = !working,
            ) { Text(stringResource(R.string.secret_start_over)) }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** [SecretCrypto.SetupError] as the line shown under the field. */
@StringRes
private fun setupErrorRes(error: SecretCrypto.SetupError): Int = when (error) {
    SecretCrypto.SetupError.PIN_TOO_SHORT -> R.string.secret_error_pin_too_short
    SecretCrypto.SetupError.PIN_NOT_DIGITS -> R.string.secret_error_pin_not_digits
    SecretCrypto.SetupError.PIN_TOO_WEAK -> R.string.secret_error_pin_too_weak
    SecretCrypto.SetupError.PASSWORD_TOO_SHORT -> R.string.secret_error_password_too_short
    SecretCrypto.SetupError.PASSWORD_TOO_WEAK -> R.string.secret_error_password_too_weak
    SecretCrypto.SetupError.PATTERN_TOO_SHORT -> R.string.secret_error_pattern_too_short
    SecretCrypto.SetupError.PATTERN_TOO_WEAK -> R.string.secret_error_pattern_too_weak
    SecretCrypto.SetupError.UNKNOWN_KIND -> R.string.secret_error_unknown_kind
}
