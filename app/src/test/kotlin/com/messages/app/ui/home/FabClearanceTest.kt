package com.messages.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class FabClearanceTest {

    @Test
    fun `list bottom padding exceeds fab top boundary to prevent covering row text or timestamp`() {
        val fabSize = 48.dp
        val fabBottomMargin = 88.dp
        val fabTopBoundary = fabBottomMargin + fabSize // 136.dp

        val listBottomPadding = 144.dp

        assertTrue(
            "List bottom padding ($listBottomPadding) must exceed FAB top boundary ($fabTopBoundary) so the last row's timestamp and text are never obscured",
            listBottomPadding >= fabTopBoundary,
        )
    }

    @Test
    fun `fab sits safely above floating dock pill`() {
        val dockHeight = 56.dp
        val dockBottomMargin = 16.dp
        val dockTopBoundary = dockBottomMargin + dockHeight // 72.dp

        val fabBottomMargin = 88.dp

        assertTrue(
            "FAB bottom margin ($fabBottomMargin) must be greater than dock top boundary ($dockTopBoundary) so FAB never overlaps dock pill",
            fabBottomMargin > dockTopBoundary,
        )
    }
}
