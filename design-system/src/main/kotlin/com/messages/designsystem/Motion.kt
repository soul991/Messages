package com.messages.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

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
    /** Position/size/layout changes — the visible, bouncy character. */
    fun <T> spatialDefault(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Quick spatial transitions (chips, small components). */
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Deliberate spatial transitions (screen-level movement). */
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Opacity/color — critically damped, no overshoot. */
    fun <T> effectsDefault(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> effectsSlow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    /** Low-stiffness spring for hero/ambient elements (empty-state art). */
    fun <T> gentle(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
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
