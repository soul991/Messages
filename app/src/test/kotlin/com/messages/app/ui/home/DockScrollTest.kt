package com.messages.app.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test

class DockScrollTest {

    @Test
    fun `scrolling down preserves floating dock visibility`() {
        val visible = calculateDockVisibility(currentlyVisible = true, deltaY = -25f)
        assertTrue("Scrolling down must keep dock visible in B1 Telegram-style dock", visible)
    }

    @Test
    fun `scrolling up preserves floating dock visibility`() {
        val visible = calculateDockVisibility(currentlyVisible = false, deltaY = 25f)
        assertTrue("Scrolling up must keep dock visible", visible)
    }

    @Test
    fun `micro jitter preserves dock visibility`() {
        assertTrue(calculateDockVisibility(currentlyVisible = true, deltaY = -2f))
        assertTrue(calculateDockVisibility(currentlyVisible = false, deltaY = 3f))
    }

    @Test
    fun `reaching top of conversation list ensures dock is visible`() {
        val visible = calculateDockVisibility(currentlyVisible = false, deltaY = -50f, isTop = true)
        assertTrue("Reaching top of list must keep dock visible", visible)
    }
}
