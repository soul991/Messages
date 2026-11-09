package com.messages.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGesturePolicyTest {

    @Test
    fun `horizontal dominant angle within 20 degrees returns true`() {
        // Pure horizontal
        assertTrue(SwipeGesturePolicy.isHorizontalDominant(100f, 0f))
        assertTrue(SwipeGesturePolicy.isHorizontalDominant(-100f, 0f))

        // 10 degrees: tan(10 deg) ~ 0.176 < 0.364
        assertTrue(SwipeGesturePolicy.isHorizontalDominant(100f, 17.6f))
        assertTrue(SwipeGesturePolicy.isHorizontalDominant(-100f, -17.6f))

        // 19 degrees: tan(19 deg) ~ 0.344 < 0.364
        assertTrue(SwipeGesturePolicy.isHorizontalDominant(100f, 34.4f))
    }

    @Test
    fun `steep diagonal or vertical movement returns false`() {
        // Pure vertical
        assertFalse(SwipeGesturePolicy.isHorizontalDominant(0f, 50f))
        assertFalse(SwipeGesturePolicy.isHorizontalDominant(0f, -50f))

        // 45 degrees: ratio 1.0 >= 0.364
        assertFalse(SwipeGesturePolicy.isHorizontalDominant(100f, 100f))

        // 25 degrees: tan(25 deg) ~ 0.466 >= 0.364
        assertFalse(SwipeGesturePolicy.isHorizontalDominant(100f, 46.6f))

        // 30 degrees: tan(30 deg) ~ 0.577 >= 0.364
        assertFalse(SwipeGesturePolicy.isHorizontalDominant(-100f, 57.7f))
    }

    @Test
    fun `shouldCommit requires at least 40 percent of row width`() {
        val rowWidth = 1000f

        // Under 40%: false
        assertFalse(SwipeGesturePolicy.shouldCommit(399f, rowWidth))
        assertFalse(SwipeGesturePolicy.shouldCommit(-399f, rowWidth))
        assertFalse(SwipeGesturePolicy.shouldCommit(0f, rowWidth))

        // 40% and above: true
        assertTrue(SwipeGesturePolicy.shouldCommit(400f, rowWidth))
        assertTrue(SwipeGesturePolicy.shouldCommit(-400f, rowWidth))
        assertTrue(SwipeGesturePolicy.shouldCommit(500f, rowWidth))
        assertTrue(SwipeGesturePolicy.shouldCommit(-750f, rowWidth))
    }

    @Test
    fun `shouldCommit returns false for zero or negative row width`() {
        assertFalse(SwipeGesturePolicy.shouldCommit(100f, 0f))
        assertFalse(SwipeGesturePolicy.shouldCommit(100f, -100f))
    }
}
