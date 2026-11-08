package com.messages.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockScrollTest {

    @Test
    fun `scrolling down hides floating dock`() {
        val visible = calculateDockVisibility(currentlyVisible = true, deltaY = -25f)
        assertFalse("Scrolling down should hide the dock", visible)
    }

    @Test
    fun `scrolling up reveals floating dock`() {
        val visible = calculateDockVisibility(currentlyVisible = false, deltaY = 25f)
        assertTrue("Scrolling up should reveal the dock", visible)
    }

    @Test
    fun `micro jitter preserves current dock visibility state`() {
        // Minor delta within [-6f, 6f]
        assertTrue(calculateDockVisibility(currentlyVisible = true, deltaY = -2f))
        assertFalse(calculateDockVisibility(currentlyVisible = false, deltaY = 3f))
    }

    @Test
    fun `reaching top of conversation list always ensures dock is visible`() {
        val visible = calculateDockVisibility(currentlyVisible = false, deltaY = -50f, isTop = true)
        assertTrue("Reaching top of list must always keep dock visible", visible)
    }
}
