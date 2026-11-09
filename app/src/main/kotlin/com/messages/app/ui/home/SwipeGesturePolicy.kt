package com.messages.app.ui.home

/**
 * Policy rules for swipe gestures (§8.2 / Phase C3):
 * Prevents accidental archives during vertical scrolling or diagonal thumb movements.
 */
object SwipeGesturePolicy {
    /** tan(20 degrees) = 0.36397... Horizontal dominance requires abs(dy) / abs(dx) < 0.364 */
    const val MAX_DY_DX_RATIO = 0.364f

    /** Reveal threshold: pulling at least 40% of the row width commits the archive. */
    const val COMMIT_THRESHOLD_FRACTION = 0.40f

    /** Touch slop in dp before committing to the swipe gesture. */
    const val TOUCH_SLOP_DP = 24

    /** Returns true if the gesture angle is within 20 degrees of horizontal. */
    fun isHorizontalDominant(dx: Float, dy: Float): Boolean {
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)
        if (absDx == 0f) return false
        return (absDy / absDx) < MAX_DY_DX_RATIO
    }

    /** Returns true if the revealed offset meets or exceeds the 40% commit threshold. */
    fun shouldCommit(offset: Float, rowWidth: Float): Boolean {
        if (rowWidth <= 0f) return false
        return kotlin.math.abs(offset) >= rowWidth * COMMIT_THRESHOLD_FRACTION
    }
}
