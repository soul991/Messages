package com.messages.app.ui.secret

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.messages.app.ThemePreferences
import com.messages.designsystem.AccentSeed
import com.messages.designsystem.LocalDarkTheme
import com.messages.designsystem.LocalSentBubble
import com.messages.designsystem.Motion
import com.messages.designsystem.SentBubbleColors
import com.messages.designsystem.schemeForSeed

/**
 * The vault treatment (Phase 7): every secret-space screen renders inside
 * this wrapper — a DARK, AMOLED-black scheme derived from the user's active
 * accent seed via the SAME generators the app theme uses (`schemeForSeed` /
 * dynamic dark). Deliberately dark even in light theme: the space must read
 * as a different place from the normal inbox. This is a re-parameterization
 * of the existing design system, not a fork — no new color constants, no new
 * duration constants; the accent shows as thin glows and edges, never slabs.
 */
/**
 * True inside the secret space. Dialogs and bottom sheets create their OWN
 * windows — the activity's FLAG_SECURE does not extend to them — so every
 * dialog/sheet that can render inside the space asks these helpers for its
 * window properties. SecureOn inside the vault, Inherit outside (normal-space
 * behavior unchanged).
 */
val LocalSecureSurfaces = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun secureDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(
        securePolicy = if (LocalSecureSurfaces.current) {
            androidx.compose.ui.window.SecureFlagPolicy.SecureOn
        } else {
            androidx.compose.ui.window.SecureFlagPolicy.Inherit
        },
    )

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun secureSheetProperties(): androidx.compose.material3.ModalBottomSheetProperties =
    androidx.compose.material3.ModalBottomSheetDefaults.properties(
        securePolicy = if (LocalSecureSurfaces.current) {
            androidx.compose.ui.window.SecureFlagPolicy.SecureOn
        } else {
            androidx.compose.ui.window.SecureFlagPolicy.Inherit
        },
    )

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accent = remember { ThemePreferences.currentAccent(context) }
    val base = when {
        accent == AccentSeed.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)
        accent == AccentSeed.DYNAMIC -> schemeForSeed(AccentSeed.BLUE, dark = true)
        else -> schemeForSeed(accent, dark = true)
    }
    // AMOLED-black canvas — same overlay rule as the app's AMOLED tier.
    val scheme = base.copy(surface = Color.Black, background = Color.Black)
    CompositionLocalProvider(
        LocalDarkTheme provides true,
        LocalSentBubble provides SentBubbleColors(scheme.primary, scheme.onPrimary),
        LocalSecureSurfaces provides true,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography, // inherit the app's hierarchy
            content = content,
        )
    }
}

/**
 * The vault's lock mark — Compose-drawn (no stock 24dp icon): shackle arc +
 * rounded body + keyhole, stroked with a subtle vertical accent gradient over
 * a soft radial glow. Springs from 90% scale on entry (spatial — position/
 * size), glow fades on effects. ~80dp by default.
 */
@Composable
fun VaultLockMark(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.9f,
        animationSpec = Motion.spatialDefault(),
        label = "vault-lock-scale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = Motion.effectsSlow(),
        label = "vault-lock-glow",
    )
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        val w = this.size.width
        val stroke = w * 0.075f
        // Soft radial glow behind the mark — accent as light, never a slab.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.28f * glowAlpha), Color.Transparent),
                center = Offset(w / 2f, w * 0.55f),
                radius = w * 0.62f,
            ),
            radius = w * 0.62f,
            center = Offset(w / 2f, w * 0.55f),
        )
        val accentBrush = Brush.verticalGradient(
            colors = listOf(container, primary),
            startY = 0f, endY = w,
        )
        // Shackle: arc from the body's shoulders.
        drawArc(
            brush = accentBrush,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, w * 0.10f),
            size = Size(w * 0.44f, w * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Body: rounded rect, stroked (outline language, not a filled slab).
        drawRoundRect(
            brush = accentBrush,
            topLeft = Offset(w * 0.18f, w * 0.42f),
            size = Size(w * 0.64f, w * 0.46f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
            style = Stroke(width = stroke),
        )
        // Keyhole: dot + stem.
        drawCircle(
            brush = accentBrush,
            radius = w * 0.055f,
            center = Offset(w / 2f, w * 0.60f),
        )
        drawLine(
            brush = accentBrush,
            start = Offset(w / 2f, w * 0.63f),
            end = Offset(w / 2f, w * 0.74f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Slim persistent header strip for the locked list: lock glyph + space name
 * over a hairline accent gradient edge. Leaves no doubt which space this is.
 */
@Composable
fun VaultHeaderStrip(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VaultLockMark(size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Locked chats",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Hairline edge: accent fading out to both sides — an edge, not a bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            primary.copy(alpha = 0.45f),
                            Color.Transparent,
                        )
                    )
                ),
        )
    }
}

/**
 * Empty-state art for the locked list: a Compose-drawn composition (offset
 * concentric halos + the lock mark) settling on the design system's gentle
 * spring — same language as Home's layered empty states, vault palette.
 */
@Composable
fun VaultEmptyArt(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val settle by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = Motion.gentle(),
        label = "vault-empty-settle",
    )
    Box(modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val c = center.copy(y = center.y + (1f - settle) * 14.dp.toPx())
            // Concentric halos, offset slightly for depth.
            listOf(0.95f to 0.06f, 0.72f to 0.10f, 0.5f to 0.16f).forEach { (r, a) ->
                drawCircle(
                    color = primary.copy(alpha = a * settle),
                    radius = this.size.width / 2f * r,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Box(Modifier.graphicsLayer {
            translationY = (1f - settle) * 14.dp.toPx()
        }) { VaultLockMark(size = 72.dp) }
    }
}
