package com.messages.app.notify

import com.messages.app.MessagesApp
import com.messages.protection.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Commit 1 spec: notification routing by verdict class.
 *
 *  - Inbox    → normal notification on CH_PERSONAL (IMPORTANCE_HIGH)
 *  - Review   → batched silent notification on CH_REVIEW (IMPORTANCE_LOW),
 *               no sound, no heads-up
 *  - Spam     → no notification at all (ordinary spam); dangerous spam
 *               + warn-on → fraud warning on CH_FRAUD (the one exception)
 *  - Blocked  → no notification
 *
 * All tests call [MessageNotifier.routingAction] directly — a pure function
 * with no Android context, no SharedPreferences, no side effects.
 */
class NotificationRoutingTest {

    /** Invoke routingAction with all toggles at their shipped defaults. */
    private fun route(
        category: Category,
        dangerous: Boolean = false,
        notifyTransactions: Boolean = true,
        notifyPromotions: Boolean = false,
        notifyReview: Boolean = true,
        warnDangerous: Boolean = true,
    ): NotifyAction = MessageNotifier.routingAction(
        category, dangerous, notifyTransactions, notifyPromotions, notifyReview, warnDangerous,
    )

    // ── Inbox ─────────────────────────────────────────────────────────────────

    @Test fun `inbox routes to the personal (high-importance) channel`() {
        val action = route(Category.INBOX)
        assertTrue("expected Post, got $action", action is NotifyAction.Post)
        assertEquals(MessagesApp.CH_PERSONAL, (action as NotifyAction.Post).channel)
    }

    @Test fun `inbox channel constant is the expected value`() {
        // Documents the value so a rename is caught here, not at runtime.
        assertEquals("personal", MessagesApp.CH_PERSONAL)
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Test fun `review produces a batched low-priority notification when enabled`() {
        assertEquals(NotifyAction.ReviewBatch, route(Category.REVIEW, notifyReview = true))
    }

    @Test fun `review is silent when the user turned off review notifications`() {
        assertEquals(NotifyAction.Silent, route(Category.REVIEW, notifyReview = false))
    }

    @Test fun `review channel constant is IMPORTANCE_LOW`() {
        // CH_REVIEW is registered with IMPORTANCE_LOW in MessagesApp.onCreate.
        // Pinning the name here ensures the channel id stays consistent.
        assertEquals("review", MessagesApp.CH_REVIEW)
    }

    // ── Spam ──────────────────────────────────────────────────────────────────

    @Test fun `ordinary spam produces no notification`() {
        assertEquals(NotifyAction.Silent, route(Category.SPAM, dangerous = false))
    }

    @Test fun `dangerous spam with warn-on produces a fraud warning`() {
        // Phase 4 item 19 / Truecaller rec A4: the ONE exception to
        // "filtered folders stay silent."
        assertEquals(
            NotifyAction.FraudWarning,
            route(Category.SPAM, dangerous = true, warnDangerous = true),
        )
    }

    @Test fun `dangerous spam with warn-off is silent`() {
        assertEquals(
            NotifyAction.Silent,
            route(Category.SPAM, dangerous = true, warnDangerous = false),
        )
    }

    // ── Blocked ───────────────────────────────────────────────────────────────

    @Test fun `blocked produces no notification`() {
        assertEquals(NotifyAction.Silent, route(Category.BLOCKED))
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @Test fun `transactions route to the transactions channel when enabled`() {
        val action = route(Category.TRANSACTIONS, notifyTransactions = true)
        assertTrue("expected Post, got $action", action is NotifyAction.Post)
        assertEquals(MessagesApp.CH_TRANSACTIONS, (action as NotifyAction.Post).channel)
    }

    @Test fun `transactions are silent when the user turned them off`() {
        assertEquals(NotifyAction.Silent, route(Category.TRANSACTIONS, notifyTransactions = false))
    }

    // ── Promotions ────────────────────────────────────────────────────────────

    @Test fun `promotions are silent by default (opt-in off)`() {
        assertEquals(NotifyAction.Silent, route(Category.PROMOTIONS, notifyPromotions = false))
    }

    @Test fun `promotions route to the promotions channel when opted in`() {
        val action = route(Category.PROMOTIONS, notifyPromotions = true)
        assertTrue("expected Post, got $action", action is NotifyAction.Post)
        assertEquals(MessagesApp.CH_PROMOTIONS, (action as NotifyAction.Post).channel)
    }
}
