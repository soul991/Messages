package com.personal.detectivedialer.ui.dialer

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.ui.theme.Brand

/**
 * Dialpad screen — satisfies the ROLE_DIALER ACTION_DIAL contract and places
 * outgoing calls. Reached from the Calls tab FAB or an external DIAL intent
 * (which pre-fills [prefill]).
 */
@Composable
fun DialerScreen(
    prefill: String = "",
    onCallPlaced: () -> Unit = {},
    vm: DialerViewModel = hiltViewModel(),
) {
    val input by vm.input.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(prefill) {
        if (prefill.isNotBlank()) vm.setNumber(prefill)
    }

    // Clipboard paste-to-dial (9c): offer a phone-shaped clipboard string as a
    // dismissible chip — never silently paste. Re-checked on ON_RESUME (not just
    // first composition) so returning from another app after copying a number
    // (e.g. from Messages) surfaces it. Dismiss is tracked per-number, so copying
    // a *different* number and coming back shows the new one.
    var clipSuggestion by remember { mutableStateOf<String?>(null) }
    var dismissedNumber by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                clipSuggestion = readClipboardPhoneNumber(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted && vm.placeCall()) onCallPlaced() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.3f))
        Text(
            input.ifBlank { "Enter a number" },
            color = if (input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.weight(0.3f))

        // Clipboard suggestion chip: shown until dismissed or acted on. Tapping it
        // fills the input and dials; the ✕ dismisses without touching the input.
        val suggestion = clipSuggestion
        if (suggestion != null && suggestion != dismissedNumber && suggestion != input) {
            ClipboardSuggestionChip(
                number = suggestion,
                onDial = {
                    vm.setNumber(suggestion)
                    if (vm.placeCall(suggestion)) {
                        onCallPlaced()
                    } else {
                        // No CALL_PHONE yet — number is now in the input; ask + place.
                        callPermLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                    dismissedNumber = suggestion
                },
                onDismiss = { dismissedNumber = suggestion },
            )
            Spacer(Modifier.height(12.dp))
        }

        val rows = listOf(
            listOf('1' to "", '2' to "ABC", '3' to "DEF"),
            listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
            listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
            listOf('*' to "", '0' to "+", '#' to ""),
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { (digit, letters) ->
                    PadKey(digit, letters) { vm.append(digit) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Call button (centered); backspace to its right when there's input.
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brand.SafeGreen)
                    .clickable {
                        if (input.isBlank()) return@clickable
                        // Ask for CALL_PHONE on first use, then place.
                        callPermLauncher.launch(Manifest.permission.CALL_PHONE)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (input.isNotBlank()) {
                Spacer(Modifier.size(24.dp))
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { vm.backspace() },
                )
            }
        }
        Spacer(Modifier.weight(0.2f))
    }
}

@Composable
private fun PadKey(digit: Char, letters: String, onClick: () -> Unit) {
    // 72dp meets the Material minimum comfortable touch target for a dial key.
    // A faint circular surface makes the target discoverable and gives press
    // feedback bounded to the circle instead of a square ripple.
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit.toString(), fontSize = 30.sp, fontWeight = FontWeight.Normal)
            if (letters.isNotBlank()) {
                Text(
                    letters,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Dismissible "Dial <number>" chip shown above the dialpad when the clipboard
 * holds a phone-number-shaped string (9c). The whole chip dials; the trailing ✕
 * dismisses it. Mirrors the Google Dialer clipboard suggestion.
 */
@Composable
private fun ClipboardSuggestionChip(number: String, onDial: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(50))
            .background(Brand.Blue.copy(alpha = 0.12f))
            .clickable(onClick = onDial)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.ContentPaste,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Dial $number",
            color = Brand.Blue,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss clipboard suggestion",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(4.dp),
        )
    }
}

// A phone-number-shaped string: an optional leading '+', then only digits and the
// usual formatting characters (spaces, dashes, dots, parens). Deliberately loose
// (this is a suggestion, not validation) but tight enough to ignore ordinary text.
private val PHONE_CLIP_REGEX =
    Regex("^\\+?[0-9()\\s.\\-]+$")

/**
 * The current clipboard text if it looks like a phone number, else null. Reads at
 * most the first clip item; total digit count must be 7–15 (E.164 upper bound) so
 * ordinary text, URLs, and long numeric blobs (order IDs, OTP payloads) don't
 * masquerade as numbers. Never throws — clipboard access can fail on some OEMs /
 * when another app owns the focus.
 */
private fun readClipboardPhoneNumber(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    if (!cm.hasPrimaryClip()) return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
    if (text.isEmpty() || !PHONE_CLIP_REGEX.matches(text)) return null
    // A stray '+' is only meaningful as the leading char; reject "1+2" style noise.
    if (text.indexOf('+').let { it > 0 || text.count { c -> c == '+' } > 1 }) return null
    val digitCount = text.count { it.isDigit() }
    if (digitCount !in 7..15) return null
    text
}.getOrNull()
