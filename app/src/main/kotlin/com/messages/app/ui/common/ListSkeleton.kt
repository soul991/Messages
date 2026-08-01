package com.messages.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * V2-43. True once [loading] has lasted longer than [graceMillis], false the
 * moment it stops.
 *
 * The grace is the whole reason the original code rendered a blank: a spinner
 * that appears for one frame is noise. This keeps that property for fast loads
 * and drops it for slow ones, instead of choosing once for both.
 */
@Composable
fun rememberLoadingGrace(loading: Boolean, graceMillis: Long = LOADING_GRACE_MILLIS): Boolean {
    var pastGrace by remember { mutableStateOf(false) }
    LaunchedEffect(loading, graceMillis) {
        if (!loading) {
            pastGrace = false
            return@LaunchedEffect
        }
        delay(graceMillis)
        pastGrace = true
    }
    return pastGrace
}

/**
 * Placeholder rows shaped like the list that is coming.
 *
 * A skeleton rather than a centered spinner because it says something a spinner
 * cannot: that what arrives will be a list, and roughly how much of it. It also
 * leaves the scroll position where the real rows will be, so the list does not
 * jump when they land.
 *
 * The whole block reads out as one "Loading" node — a screen reader has no use
 * for eight nameless grey bars.
 */
@Composable
fun ListSkeleton(
    label: String,
    modifier: Modifier = Modifier,
    rows: Int = 8,
    avatar: Dp = 48.dp,
) {
    // One transition for the block: every row pulsing on its own phase would
    // read as activity in the list rather than as a placeholder for it.
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        repeat(rows) { index ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bar(Modifier.size(avatar), CircleShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    // Alternating widths so the block does not read as a table.
                    Bar(Modifier.width(if (index % 2 == 0) 140.dp else 108.dp).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Bar(Modifier.fillMaxWidth(if (index % 3 == 0) 0.75f else 0.55f).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(modifier: Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Spacer(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
