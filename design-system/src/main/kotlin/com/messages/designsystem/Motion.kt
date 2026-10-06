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
