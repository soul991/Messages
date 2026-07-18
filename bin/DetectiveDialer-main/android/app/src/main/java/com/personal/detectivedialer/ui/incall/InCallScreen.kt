package com.personal.detectivedialer.ui.incall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import com.personal.detectivedialer.telecom.AudioRoute
import com.personal.detectivedialer.telecom.AudioUi
import com.personal.detectivedialer.telecom.CallState
import com.personal.detectivedialer.telecom.CallUi
import com.personal.detectivedialer.ui.components.rememberFileImageBitmap
import com.personal.detectivedialer.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The whole in-call surface. Picks the call to feature (an incoming ringing call
 * wins; otherwise the active/most-recent one) and renders either the incoming
 * slide-to-answer UI or the ongoing-call controls, over the user's chosen
 * wallpaper + scrim. A second call is surfaced as a slim banner at the top.
 */
@Composable
fun InCallScreen(
    calls: List<CallUi>,
    audio: AudioUi,
    appearance: CallAppearance,
    deviceWallpaper: androidx.compose.ui.graphics.ImageBitmap? = null,
    vm: InCallViewModel,
) {
    if (calls.isEmpty()) return

    val primary = calls.firstOrNull { it.isRinging }
        ?: calls.firstOrNull { it.isActive }
        ?: calls.first()
    val secondary = calls.firstOrNull { it.id != primary.id }
    val incoming = primary.isRinging && primary.isIncoming

    CallBackground(appearance, deviceWallpaper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            secondary?.let { HeldCallBanner(it, vm) }

            Spacer(Modifier.height(24.dp))
            // Fade + rise the caller identity in when the screen appears.
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 },
            ) {
                CallerHeader(primary)
            }

            Spacer(Modifier.weight(1f))

            if (incoming) {
                IncomingControls(primary, vm)
            } else {
                OngoingControls(primary, audio, vm)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The incoming/in-call background, in priority order (bug batch #1):
 *   1. the user's custom incoming-call wallpaper, if set
 *   2. the device's current system wallpaper, if we could read it
 *   3. a neutral deep-blue gradient — always present, so the screen is never blank
 * A scrim is drawn over any real photo (custom or device) so the caller name and
 * controls stay readable; the gradient needs none.
 */
@Composable
private fun CallBackground(
    appearance: CallAppearance,
    deviceWallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    content: @Composable () -> Unit,
) {
    val custom = rememberFileImageBitmap(appearance.wallpaperPath.ifBlank { null })
    // Custom wins; else fall back to the device wallpaper. Null → gradient tier.
    val photo = custom ?: deviceWallpaper
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1A2B)) {
        Box(Modifier.fillMaxSize()) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = appearance.scrimPercent / 100f)),
                )
            } else {
                // Neutral fallback — never a blank/broken background.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep))),
                )
            }
            content()
        }
    }
}

@Composable
private fun HeldCallBanner(call: CallUi, vm: InCallViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable { vm.toggleHold(call) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${call.label} · ${if (call.isOnHold) "on hold" else "waiting"}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Tap to swap", color = Brand.BlueLight, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CallerHeader(call: CallUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                call.label.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            call.label,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (call.displayName != null && call.number.isNotBlank()) {
            Text(
                call.number,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        StatusLine(call)
        VerdictChip(call)
    }
}

@Composable
private fun StatusLine(call: CallUi) {
    // Map to the iQOO/Google-dialer wording. Outgoing progresses
    // CONNECTING ("Calling…") → DIALING ("Ringing…") → ACTIVE (timer).
    val text = when (call.state) {
        CallState.RINGING -> if (call.isIncoming) "Incoming call" else "Ringing…"
        CallState.CONNECTING -> "Calling…"
        CallState.DIALING -> "Ringing…"
        CallState.HOLDING -> "On hold"
        CallState.ACTIVE -> ""
        CallState.DISCONNECTING, CallState.DISCONNECTED -> "Call ended"
        else -> ""
    }
    if (call.isActive && call.connectedAtMs > 0) {
        CallTimer(call.connectedAtMs)
    } else {
        Crossfade(targetState = text, label = "callStatus") { label ->
            if (label.isNotBlank()) {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun CallTimer(connectedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val secs = ((now - connectedAtMs) / 1000).coerceAtLeast(0)
    Text(
        "%02d:%02d".format(secs / 60, secs % 60),
        color = Brand.SafeGreenLight,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
}

/** Phase D: the AI screening verdict + reason, carried over from pre-ring screening. */
@Composable
private fun VerdictChip(call: CallUi) {
    val verdict = call.verdict ?: return
    val (bg, label) = when (verdict.uppercase()) {
        "SPAM" -> Brand.SpamRed to "🚫 Likely spam"
        "REJECT" -> Brand.SpamRedDeep to "⛔ Screened"
        "ALLOW" -> Brand.SafeGreen to "✅ Looks genuine"
        else -> Color.White.copy(alpha = 0.15f) to verdict
    }
    Spacer(Modifier.height(12.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(bg.copy(alpha = 0.9f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        call.verdictReason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Incoming: slide to answer / decline, plus reply & voicemail ─────────────
@Composable
private fun IncomingControls(call: CallUi, vm: InCallViewModel) {
    var showReplies by remember { mutableStateOf(false) }

    // Lift the slider ~28dp off the bottom for easier one-handed reach (#2), and
    // add any gesture-nav inset that exceeds the nav-bar inset already applied by
    // the parent's systemBarsPadding — so on OriginOS the handle clears the
    // bottom gesture strip instead of colliding with it.
    val density = LocalDensity.current
    val gestureBottom = WindowInsets.systemGestures.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val gestureClearance = with(density) { (gestureBottom - navBottom).coerceAtLeast(0).toDp() }
    val sliderLift = 28.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = sliderLift + gestureClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SecondaryIncoming(Icons.AutoMirrored.Filled.Message, "Message") { showReplies = true }
            SecondaryIncoming(Icons.Filled.Voicemail, "Voicemail") { vm.sendToVoicemail(call.id) }
        }
        Spacer(Modifier.height(24.dp))
        SlideToAnswer(
            onAnswer = { vm.answer(call.id) },
            onDecline = { vm.reject(call.id) },
        )
    }

    if (showReplies) {
        QuickReplySheet(
            onDismiss = { showReplies = false },
            onSend = { text -> showReplies = false; vm.replyWithMessage(call, text) },
        )
    }
}

@Composable
private fun SecondaryIncoming(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Stock-Android slide control: drag the centre phone right to answer (green) or
 * left to decline (red). The end icons are also tappable as an accessible
 * fallback. A gentle pulse guides the user toward the gesture.
 */
@Composable
private fun SlideToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val density = LocalDensity.current
    val handle = 66.dp
    val trackHeight = 74.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val handlePx = with(density) { handle.toPx() }
        val marginPx = with(density) { 6.dp.toPx() }
        val maxOffset = (trackWidthPx - handlePx) / 2f - marginPx
        val threshold = maxOffset * 0.6f

        val offsetX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseScale",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            // End hints, also tappable for accessibility.
            Icon(
                Icons.Filled.CallEnd,
                contentDescription = "Decline",
                tint = Brand.SpamRedLight,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(28.dp)
                    .clickable(onClick = onDecline),
            )
            Icon(
                Icons.Filled.Call,
                contentDescription = "Answer",
                tint = Brand.SafeGreenLight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .size(28.dp)
                    .clickable(onClick = onAnswer),
            )
            // Draggable phone handle. Tint shifts toward the side being pulled.
            val progress = (offsetX.value / maxOffset).coerceIn(-1f, 1f)
            val handleColor = when {
                progress > 0.1f -> Brand.SafeGreen
                progress < -0.1f -> Brand.SpamRed
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(handle)
                    .scale(if (abs(progress) < 0.1f) pulse else 1f)
                    .clip(CircleShape)
                    .background(handleColor)
                    .slideHandle(
                        onDrag = { delta ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(-maxOffset, maxOffset))
                            }
                        },
                        onEnd = {
                            when {
                                offsetX.value >= threshold -> onAnswer()
                                offsetX.value <= -threshold -> onDecline()
                                else -> scope.launch { offsetX.animateTo(0f) }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = "Slide to answer or decline",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Horizontal drag wiring for the slide-to-answer handle. */
private fun Modifier.slideHandle(onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = onEnd,
            onDragCancel = onEnd,
        ) { _, dragAmount -> onDrag(dragAmount) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val presets = listOf(
        "Can't talk. Busy now.",
        "Can I call you later?",
        "In a meeting.",
        "On my way.",
    )
    var custom by remember { mutableStateOf("") }
    var composing by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Reply with a message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            presets.forEach { reply ->
                Text(
                    reply,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSend(reply) }
                        .padding(vertical = 14.dp),
                )
            }
            if (!composing) {
                Text(
                    "Custom…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { composing = true }
                        .padding(vertical = 14.dp),
                )
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    placeholder = { Text("Type a message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = custom.isNotBlank(),
                        onClick = { onSend(custom.trim()) },
                    ) { Text("Send") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Ongoing: mute / dialpad / audio route / hold + hang up ──────────────────
@Composable
private fun OngoingControls(call: CallUi, audio: AudioUi, vm: InCallViewModel) {
    var showDialpad by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = showDialpad) {
        DtmfPad(onDigit = { vm.playDtmf(call.id, it) }, onClose = { showDialpad = false })
    }

    if (!showDialpad) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToggleAction(Icons.Filled.MicOff, Icons.Filled.Mic, audio.muted, "Mute") {
                vm.setMuted(!audio.muted)
            }
            SmallAction(Icons.Filled.Dialpad, "Keypad") { showDialpad = true }
            AudioRouteAction(audio) { vm.cycleAudioRoute() }
            val holdIcon = if (call.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause
            SmallAction(holdIcon, if (call.isOnHold) "Resume" else "Hold") { vm.toggleHold(call) }
        }
        if (audio.route == AudioRoute.BLUETOOTH) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Bluetooth: ${audio.bluetoothName ?: "connected device"}",
                color = Brand.BlueLight,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    RoundAction(icon = Icons.Filled.CallEnd, bg = Brand.SpamRed, label = "End") { vm.hangup(call.id) }
}

/** Audio-route cycle button: reflects the live route and updates immediately. */
@Composable
private fun AudioRouteAction(audio: AudioUi, onClick: () -> Unit) {
    val icon = when (audio.route) {
        AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
        AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
        AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    }
    val active = audio.route != AudioRoute.EARPIECE
    val bg by animateFloatAsState(if (active) 1f else 0f, label = "routeBg")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f + 0.9f * bg))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = audio.routeLabel,
                tint = if (active) Color(0xFF0B1A2B) else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(audio.routeLabel, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

// ── DTMF keypad ────────────────────────────────────────────────────────────
@Composable
private fun DtmfPad(onDigit: (Char) -> Unit, onClose: () -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { d -> DialKey(d) { onDigit(d) } }
            }
        }
        Text(
            "Hide keypad",
            color = Brand.BlueLight,
            modifier = Modifier.clickable { onClose() }.padding(8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DialKey(digit: Char, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(digit.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Reusable buttons ───────────────────────────────────────────────────────
@Composable
private fun RoundAction(icon: ImageVector, bg: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ToggleAction(
    onIcon: ImageVector,
    offIcon: ImageVector,
    active: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val activeAmt by animateFloatAsState(if (active) 1f else 0f, label = "toggleBg")
    val pressScale by animateFloatAsState(if (active) 1.06f else 1f, label = "toggleScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f + 0.9f * activeAmt))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) onIcon else offIcon,
                contentDescription = label,
                tint = if (active) Color(0xFF0B1A2B) else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}
