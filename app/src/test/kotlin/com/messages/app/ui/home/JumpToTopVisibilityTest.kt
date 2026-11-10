package com.messages.app.ui.home

import com.messages.designsystem.JumpToTopDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpToTopVisibilityTest {

    @Test
    fun `threshold constant is strictly 6 rows`() {
        assertEquals(6, JumpToTopDefaults.VISIBILITY_THRESHOLD_INDEX)
    }

    @Test
    fun `button is hidden near top of the list`() {
        assertFalse("At index 0 (top of list), button must be hidden", JumpToTopDefaults.isVisible(0))
        assertFalse("At index 1, button must be hidden", JumpToTopDefaults.isVisible(1))
        assertFalse("At index 5, button must be hidden", JumpToTopDefaults.isVisible(5))
        assertFalse("At index 6 (exact threshold boundary), button must still be hidden", JumpToTopDefaults.isVisible(6))
    }

    @Test
    fun `button springs into view after scrolling past 6 rows`() {
        assertTrue("At index 7 (past 6 rows), button must be visible", JumpToTopDefaults.isVisible(7))
        assertTrue("At index 8, button must be visible", JumpToTopDefaults.isVisible(8))
        assertTrue("At index 15, button must be visible", JumpToTopDefaults.isVisible(15))
        assertTrue("At index 100 (deep scroll), button must be visible", JumpToTopDefaults.isVisible(100))
    }

    @Test
    fun `simulated scroll sequence correctly triggers and dismisses button`() {
        val scrollIndices = listOf(0, 2, 4, 6, 7, 10, 15, 10, 7, 6, 3, 0)
        val expectedVisibility = listOf(
            false, // 0
            false, // 2
            false, // 4
            false, // 6
            true,  // 7 (spring in)
            true,  // 10
            true,  // 15
            true,  // 10
            true,  // 7
            false, // 6 (hide near top)
            false, // 3
            false, // 0
        )

        for (i in scrollIndices.indices) {
            val index = scrollIndices[i]
            val expected = expectedVisibility[i]
            assertEquals("Visibility mismatch at scroll index $index", expected, JumpToTopDefaults.isVisible(index))
        }
    }
}
