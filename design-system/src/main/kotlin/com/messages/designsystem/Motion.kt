package com.messages.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.animation.ValueAnimator
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap

/**
 * M3 Expressive motion scheme (§9: spring-based animations throughout).
 *
 * These are the `MotionScheme.expressive()` spring values from material3 1.4;
 * defined locally because this app pins material3 1.3 (BOM 2024.09).
 * Spatial springs carry the signature bounce and are for position/size/layout
 * only. Effects springs are critically damped — opacity and color must never
 * wobble, so never use a spatial spec for a fade.
 */
object Motion {
    /** Follow Android's system animation scale for reduced-motion users. */
    fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    /** Chat message entrance spring. */
    fun <T> chatEnter(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.81f, stiffness = 340f) else snap()

    /** Screen and sub-menu transition spring. */
    fun <T> screenSpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f) else snap()

    /** Position/size/layout changes — the visible, bouncy character. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.88f, stiffness = 420f) else snap()

    /** Quick spatial transitions (chips, small components). */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 800f) else snap()

    /** Deliberate spatial transitions (screen-level movement). */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.88f, stiffness = 220f) else snap()

    /** Opacity/color — critically damped, no overshoot. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 1f, stiffness = 1600f) else snap()

    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 1f, stiffness = 3800f) else snap()

    fun <T> effectsSlow(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 1f, stiffness = 800f) else snap()

    /** Low-stiffness spring for hero/ambient elements (empty-state art). */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        else snap()

    /** Fluid spatial spring for expanding cards and container morphs. */
    fun <T> fluidSpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.86f, stiffness = 380f)
        else snap()

    /** Micro-bounce spring for popping badges, counter ticks, and tactile pills. */
    fun <T> bouncySpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.72f, stiffness = 520f)
        else snap()

    /** Ultra-smooth butter spring with low bounce and natural physical decay. */
    fun <T> butterSpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.85f, stiffness = 320f)
        else snap()

    /** Fluid composer spring for smooth multiline height expansion and keyboard sync. */
    fun <T> composerSpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.84f, stiffness = 420f)
        else snap()

    /** Tactile pop spring for emoji reaction pills and quick tap bubbles. */
    fun <T> reactionPopSpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.68f, stiffness = 550f)
        else snap()

    /** Natural bubble entry spring for incoming/outgoing message insertions. */
    fun <T> bubbleEntrySpring(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f)
        else snap()

    /** High-frequency icon morph spec for seamless mic to send button transformation. */
    fun <T> sendIconMorphSpec(): FiniteAnimationSpec<T> =
        if (animationsEnabled()) spring(dampingRatio = 0.80f, stiffness = 600f)
        else snap()
}

/** Haptics on key actions (§9) — thin wrappers over view feedback constants. */
object Haptics {
    /** Light tick for selection changes (folder chips, pickers). */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Positive confirmation (send, message moved). */
    fun confirm(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Long-press context menus. */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
