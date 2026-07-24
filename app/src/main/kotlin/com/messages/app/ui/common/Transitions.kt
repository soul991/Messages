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

/**
 * Marks this element as the shared avatar for [threadId] across screens.
 *
 * The sharedElement modifier is attached ONLY while the navigation transition
 * is actually running (Phase 6): a registered shared element costs real
 * composition time per instance, and the Home list pays it for every row on
 * every fling frame otherwise (measured ~half the frame budget on-device).
 * While settled the avatar renders normally in place, so dropping the modifier
 * has no visual effect; when a transition starts, the rows recompose (the
 * transition state is a snapshot read) and both ends register in time for the
 * match. This also covers the old "Uninitialized LayoutCoordinates" cold-start
 * crash: on the very first frame of a start destination no transition runs, so
 * no element ever joins the first lookahead pass.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedThreadAvatar(threadId: Long): Modifier {
    val sts = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedVisibilityScope.current ?: return this
    val transition = scope.transition
    if (transition.currentState == transition.targetState && !transition.isRunning) return this
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
