package com.messages.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-31. Notification and PendingIntent identity used to be `threadId.toInt()`,
 * which keeps 32 bits of a 64-bit id. These pin the two failures that caused:
 * one conversation's notification replacing another's, and — worse — an action
 * button carrying a different conversation's threadId.
 *
 * The ids that matter here are not hypothetical. Telephony thread ids grow past
 * 2^31 on a long-lived device, and this app mints *negative* synthetic ids of
 * its own (V2-21 aliases, and the old fraud ids at -1,000,000 and below), so
 * both halves of the range are in use.
 */
class NotificationIdsTest {

    /** Same low 32 bits, different threads — the exact truncation collision. */
    private val a = 1L
    private val b = 1L + (1L shl 32)

    @Test
    fun `threads sharing their low 32 bits get different tags`() {
        assertEquals(a.toInt(), b.toInt()) // the bug, stated
        assertNotEquals(NotificationIds.threadTag(a), NotificationIds.threadTag(b))
    }

    @Test
    fun `a negative thread id keeps its own tag`() {
        // Synthetic alias ids are negative; -1 and its 64-bit siblings must not
        // merge, and must not look like a positive thread either.
        val negative = -1L
        val sibling = -1L - (1L shl 32)
        assertEquals(negative.toInt(), sibling.toInt())
        assertNotEquals(
            NotificationIds.threadTag(negative),
            NotificationIds.threadTag(sibling),
        )
        assertNotEquals(NotificationIds.threadTag(negative), NotificationIds.threadTag(1L))
    }

    @Test
    fun `a thread's message and fraud notifications are distinct`() {
        // Same tag, different kind — the fraud warning must not replace the
        // message notification, nor the other way round.
        assertNotEquals(NotificationIds.ID_MESSAGE, NotificationIds.ID_FRAUD)
        assertNotEquals(NotificationIds.ID_MESSAGE, NotificationIds.ID_REMINDER)
        assertNotEquals(NotificationIds.ID_FRAUD, NotificationIds.ID_REMINDER)
    }

    @Test
    fun `thread notifications cannot collide with the fixed-id ones`() {
        // Review (-100) and the locked space (-200) are posted with no tag.
        // A tagged notification is a different identity whatever its id, so the
        // only thing to hold is that thread ids are always tagged — asserted by
        // the tag being non-empty for every id, including the ones that used to
        // truncate straight onto those constants.
        for (id in listOf(-100L, -200L, -1_000_000L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertTrue(NotificationIds.threadTag(id).isNotEmpty())
        }
    }

    @Test
    fun `reminders are keyed by message, not thread`() {
        // Two reminders on the same conversation are two notifications.
        assertNotEquals(NotificationIds.reminderTag(7L), NotificationIds.reminderTag(8L))
        // And a reminder tag can never be mistaken for a thread tag.
        assertNotEquals(NotificationIds.reminderTag(7L), NotificationIds.threadTag(7L))
    }

    @Test
    fun `each action on a thread gets its own intent data`() {
        val actions = listOf("open", "mark_read", "copy_otp", "reply", "bubble", "fraud")
        val uris = actions.map { NotificationIds.actionUri(a, it) }
        assertEquals(actions.size, uris.distinct().size)
    }

    @Test
    fun `two threads never share an action's intent data`() {
        // This is the one that mattered most: filterEquals ignores extras, so
        // identical data meant "mark as read" could act on the wrong thread.
        for (action in listOf("open", "mark_read", "reply")) {
            assertNotEquals(
                NotificationIds.actionUri(a, action),
                NotificationIds.actionUri(b, action),
            )
        }
    }

    @Test
    fun `request codes are non-negative and stable`() {
        for (id in listOf(0L, a, b, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val code = NotificationIds.requestCode(id, "reply")
            assertTrue("negative request code for $id", code >= 0)
            assertEquals(code, NotificationIds.requestCode(id, "reply"))
        }
    }

    @Test
    fun `request codes separate the truncation-colliding pair`() {
        // Not load-bearing — the data URI decides identity — but keeping these
        // apart avoids needless FLAG_UPDATE_CURRENT churn between two threads.
        assertNotEquals(
            NotificationIds.requestCode(a, "reply"),
            NotificationIds.requestCode(b, "reply"),
        )
        assertNotEquals(
            NotificationIds.requestCode(a, "reply"),
            NotificationIds.requestCode(a, "mark_read"),
        )
    }

    @Test
    fun `the legacy ids still match what the previous version posted`() {
        // cancelThread has to clear notifications left on screen across the
        // upgrade, so these must reproduce the old scheme exactly.
        assertEquals(1234.toInt(), NotificationIds.legacyMessageId(1234L))
        assertEquals((-1_000_000L - 1234L).toInt(), NotificationIds.legacyFraudId(1234L))
    }
}
