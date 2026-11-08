package com.messages.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextSelectionTest {

    @Test
    fun `findWordBoundaries selects single word on double tap`() {
        val text = "Hello world this is a test"
        val range = findWordBoundaries(text, 7) // Inside "world"
        assertEquals(6, range.first)
        assertEquals(11, range.last)
        assertEquals("world", text.substring(range.first, range.last))
    }

    @Test
    fun `findWordBoundaries isolates words bounded by punctuation`() {
        val text = "Your package (AWB-123456) has arrived!"
        val range = findWordBoundaries(text, 19) // Inside "123456"
        assertEquals(18, range.first)
        assertEquals(24, range.last)
        assertEquals("123456", text.substring(range.first, range.last))
    }

    @Test
    fun `findWordBoundaries handles start and end of string safely`() {
        val text = "Start and End"
        val startRange = findWordBoundaries(text, 0)
        assertEquals(0, startRange.first)
        assertEquals(5, startRange.last)
        assertEquals("Start", text.substring(startRange.first, startRange.last))

        val endRange = findWordBoundaries(text, text.length - 1)
        assertEquals(10, endRange.first)
        assertEquals(13, endRange.last)
        assertEquals("End", text.substring(endRange.first, endRange.last))
    }

    @Test
    fun `clampLoupePosition keeps magnifier strictly within screen boundaries`() {
        val screenWidth = 1080f
        val loupeWidth = 400f
        val minMargin = 48f
        val minTop = 144f

        // Near left edge -> clamps to minMargin
        val (leftX, leftY) = clampLoupePosition(
            targetX = 10f,
            targetY = 200f,
            loupeWidthPx = loupeWidth,
            screenWidthPx = screenWidth,
            minMarginPx = minMargin,
            minTopPx = minTop,
        )
        assertEquals(minMargin, leftX, 0.01f)
        assertEquals(200f, leftY, 0.01f)

        // Near right edge -> clamps to screenWidth - loupeWidth - minMargin (632f)
        val (rightX, _) = clampLoupePosition(
            targetX = 1000f,
            targetY = 200f,
            loupeWidthPx = loupeWidth,
            screenWidthPx = screenWidth,
            minMarginPx = minMargin,
            minTopPx = minTop,
        )
        val maxAllowedX = screenWidth - loupeWidth - minMargin
        assertEquals(maxAllowedX, rightX, 0.01f)

        // Above top edge -> clamps to minTop
        val (_, topY) = clampLoupePosition(
            targetX = 500f,
            targetY = 20f,
            loupeWidthPx = loupeWidth,
            screenWidthPx = screenWidth,
            minMarginPx = minMargin,
            minTopPx = minTop,
        )
        assertEquals(minTop, topY, 0.01f)
    }
}
