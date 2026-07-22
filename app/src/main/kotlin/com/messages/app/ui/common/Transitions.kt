package com.messages.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.messages.designsystem.Motion

/**
 * Shared-element plumbing (§9: list→chat transition). MainActivity provides
 * both scopes; row/avatar call sites opt in via [sharedThreadAvatar] without
 * threading scopes through every parameter list.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks this element as the shared avatar for [threadId] across screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedThreadAvatar(threadId: Long): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedVisibilityScope.current ?: return this
    // A sharedElement taking part in the very first lookahead pass — before the
    // SharedTransitionLayout root has been placed — crashes with "Uninitialized
    // LayoutCoordinates" (hit when a notification tap cold-starts straight into
    // a chat, making it the start destination). Sit out the first frame; the
    // list→chat spring runs ~380ms, so joining at frame 2 still animates.
    var pastFirstFrame by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pastFirstFrame = true }
    if (!pastFirstFrame) return this
    return with(sts) {
        this@sharedThreadAvatar.sharedElement(
            rememberSharedContentState(key = "avatar-$threadId"),
            animatedVisibilityScope = scope,
            boundsTransform = { _, _ -> Motion.spatialDefault() },
        )
    }
}

// Shared-axis-X pair for sibling screens (settings, dashboard, …) — springs on
// the slide (spatial), critically damped on the fade (effects).
private const val AXIS_FRACTION = 5

fun sharedAxisEnter(forward: Boolean): EnterTransition =
    slideInHorizontally(Motion.spatialDefault()) { full ->
        if (forward) full / AXIS_FRACTION else -full / AXIS_FRACTION
    } + fadeIn(Motion.effectsDefault())

fun sharedAxisExit(forward: Boolean): ExitTransition =
    slideOutHorizontally(Motion.spatialDefault()) { full ->
        if (forward) -full / AXIS_FRACTION else full / AXIS_FRACTION
    } + fadeOut(Motion.effectsFast())

/** Fade-through for the list↔chat pair, so the shared avatar carries the motion. */
fun fadeThroughEnter(): EnterTransition = fadeIn(Motion.effectsSlow())

fun fadeThroughExit(): ExitTransition = fadeOut(Motion.effectsFast())
