package com.personal.detectivedialer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the bottom-tab slide direction for ALL SIX Calls/Contacts/Messages
 * transitions (bug batch #7). +1 = new screen enters from the right, -1 = from the
 * left. Every pair must mutually reverse — the original bug shipped because only
 * Calls↔Contacts happened to reverse.
 */
class TabSlideTest {

    // Real bottom-tab order: Calls, Contacts, Messages, Blocked.
    private val tabs = listOf("dashboard", "contacts", "messages", "blocklist")
    private fun dir(from: String, to: String) = TabSlide.direction(tabs, from, to)

    @Test fun `1 Calls to Contacts enters from right`() {
        assertEquals(1, dir("dashboard", "contacts"))
    }

    @Test fun `2 Contacts to Calls enters from left`() {
        assertEquals(-1, dir("contacts", "dashboard"))
    }

    @Test fun `3 Contacts to Messages enters from right`() {
        assertEquals(1, dir("contacts", "messages"))
    }

    @Test fun `4 Messages to Contacts enters from left`() {
        // The originally-broken case: must reverse #3, not repeat it.
        assertEquals(-1, dir("messages", "contacts"))
    }

    @Test fun `5 Calls to Messages enters from right`() {
        assertEquals(1, dir("dashboard", "messages"))
    }

    @Test fun `6 Messages to Calls enters from left`() {
        assertEquals(-1, dir("messages", "dashboard"))
    }

    @Test fun `every tab pair mutually reverses`() {
        for (from in tabs) {
            for (to in tabs) {
                if (from == to) {
                    assertEquals("same tab has no direction", 0, dir(from, to))
                } else {
                    assertEquals(
                        "$from<->$to must reverse",
                        -dir(from, to),
                        dir(to, from),
                    )
                    assertTrue("$from->$to must have a direction", dir(from, to) != 0)
                }
            }
        }
    }

    @Test fun `non-tab routes fall back to default (0)`() {
        // Detail/settings routes aren't tabs → caller keeps its push/pop feel.
        assertEquals(0, dir("dashboard", "call/{callId}"))
        assertEquals(0, dir("settings", "contacts"))
        assertEquals(0, dir("sms_thread/{sender}", "messages"))
    }
}
