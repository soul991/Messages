package com.messages.core.send

import com.messages.core.db.AttemptState
import com.messages.core.db.SmsAttemptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-13: the message-level send status must be a pure function of the
 * per-(recipient, part) attempt set — identical no matter which order the
 * platform delivers the SENT/DELIVERED broadcasts in.
 *
 * Pure JVM (no Robolectric): [SendAggregate] deliberately has no Android deps.
 */
class SendAggregateTest {

    private fun matrix(
        messageId: Long = 42L,
        recipients: Int,
        parts: Int,
        wantDelivery: Boolean = true,
    ): List<SmsAttemptEntity> = buildList {
        for (r in 0 until recipients) for (p in 0 until parts) {
            add(
                SmsAttemptEntity(
                    attemptId = SendAggregate.attemptId(messageId, r, p),
                    messageId = messageId,
                    recipientIndex = r,
                    partIndex = p,
                    wantDelivery = wantDelivery,
                )
            )
        }
    }

    /** Applies one callback the way the DAO does: only PENDING may transition. */
    private fun List<SmsAttemptEntity>.settleSent(
        attemptId: String,
        ok: Boolean,
        code: Int? = null,
    ) = map {
        if (it.attemptId == attemptId && it.sentState == AttemptState.PENDING) {
            it.copy(
                sentState = if (ok) AttemptState.OK else AttemptState.FAILED,
                resultCode = code ?: it.resultCode,
            )
        } else it
    }

    private fun List<SmsAttemptEntity>.settleDelivered(attemptId: String, ok: Boolean) = map {
        if (it.attemptId == attemptId && it.deliveryState == AttemptState.PENDING) {
            it.copy(deliveryState = if (ok) AttemptState.OK else AttemptState.FAILED)
        } else it
    }

    @Test
    fun `no attempt rows leaves the status alone`() {
        assertNull(SendAggregate.of(emptyList()))
    }

    @Test
    fun `partially reported multipart group send stays SENDING`() {
        var m = matrix(recipients = 2, parts = 3)
        assertEquals(SendAggregate.SENDING, SendAggregate.of(m))
        // The first dispatch succeeding used to mark the whole message SENT.
        m = m.settleSent("42:0:0", ok = true)
        assertEquals(SendAggregate.SENDING, SendAggregate.of(m))
    }

    @Test
    fun `SENT only once every recipient and part reported success`() {
        var m = matrix(recipients = 2, parts = 2, wantDelivery = false)
        m.forEach { m = m.settleSent(it.attemptId, ok = true) }
        assertEquals(SendAggregate.SENT, SendAggregate.of(m))
    }

    @Test
    fun `one failed part fails the message even when the rest succeeded`() {
        var m = matrix(recipients = 2, parts = 2, wantDelivery = false)
        m.forEach { m = m.settleSent(it.attemptId, ok = true) }
        // The last part of the second recipient comes back with RESULT_ERROR_*.
        var withFailure = matrix(recipients = 2, parts = 2, wantDelivery = false)
        withFailure = withFailure.settleSent("42:0:0", ok = true)
        withFailure = withFailure.settleSent("42:0:1", ok = true)
        withFailure = withFailure.settleSent("42:1:0", ok = true)
        withFailure = withFailure.settleSent("42:1:1", ok = false, code = 4)
        assertEquals(SendAggregate.FAILED, SendAggregate.of(withFailure))
        assertEquals(4, SendAggregate.failureCode(withFailure))
    }

    @Test
    fun `DELIVERED needs every requested report and survives a late SENT`() {
        var m = matrix(recipients = 1, parts = 2)
        m = m.settleSent("42:0:0", ok = true)
        m = m.settleDelivered("42:0:0", ok = true)
        // Second part not sent yet: still SENDING, definitely not DELIVERED.
        assertEquals(SendAggregate.SENDING, SendAggregate.of(m))
        m = m.settleSent("42:0:1", ok = true)
        assertEquals(SendAggregate.SENT, SendAggregate.of(m))
        m = m.settleDelivered("42:0:1", ok = true)
        assertEquals(SendAggregate.DELIVERED, SendAggregate.of(m))
        // A duplicate/late SENT broadcast cannot downgrade the aggregate,
        // because the attempt is no longer PENDING.
        m = m.settleSent("42:0:0", ok = true)
        assertEquals(SendAggregate.DELIVERED, SendAggregate.of(m))
        assertTrue(SendAggregate.supersedes(SendAggregate.DELIVERED, SendAggregate.SENT))
        assertTrue(!SendAggregate.supersedes(SendAggregate.SENT, SendAggregate.DELIVERED))
    }

    @Test
    fun `a negative delivery report leaves the message SENT not DELIVERED`() {
        var m = matrix(recipients = 1, parts = 1)
        m = m.settleSent("42:0:0", ok = true)
        m = m.settleDelivered("42:0:0", ok = false)
        assertEquals(SendAggregate.SENT, SendAggregate.of(m))
    }

    @Test
    fun `every callback permutation of a mixed group send yields one status`() {
        // 2 recipients x 2 parts; recipient 1 part 1 fails; deliveries for the
        // three successes come back OK. Order must not matter.
        data class Cb(val id: String, val kind: String, val ok: Boolean)

        val callbacks = listOf(
            Cb("42:0:0", "sent", true),
            Cb("42:0:1", "sent", true),
            Cb("42:1:0", "sent", true),
            Cb("42:1:1", "sent", false),
            Cb("42:0:0", "delivered", true),
            Cb("42:0:1", "delivered", true),
            Cb("42:1:0", "delivered", true),
        )

        var permutations = 0
        permute(callbacks) { order ->
            var m = matrix(recipients = 2, parts = 2)
            order.forEach { cb ->
                m = when (cb.kind) {
                    "sent" -> m.settleSent(cb.id, cb.ok, code = if (cb.ok) null else 4)
                    else -> m.settleDelivered(cb.id, cb.ok)
                }
            }
            permutations++
            assertEquals(
                "order ${order.map { it.id + ":" + it.kind }} disagreed",
                SendAggregate.FAILED,
                SendAggregate.of(m),
            )
            assertEquals(4, SendAggregate.failureCode(m))
        }
        assertEquals(5040, permutations) // 7! — exhaustive, not sampled
    }

    @Test
    fun `all-success permutations always end DELIVERED`() {
        data class Cb(val id: String, val kind: String)

        val callbacks = listOf(
            Cb("42:0:0", "sent"), Cb("42:0:1", "sent"),
            Cb("42:1:0", "sent"), Cb("42:1:1", "sent"),
            Cb("42:0:0", "delivered"), Cb("42:0:1", "delivered"),
            Cb("42:1:0", "delivered"), Cb("42:1:1", "delivered"),
        )
        permute(callbacks) { order ->
            var m = matrix(recipients = 2, parts = 2)
            order.forEach { cb ->
                m = if (cb.kind == "sent") m.settleSent(cb.id, true)
                else m.settleDelivered(cb.id, true)
            }
            assertEquals(SendAggregate.DELIVERED, SendAggregate.of(m))
        }
    }

    @Test
    fun `request codes are unique per recipient and part`() {
        val codes = buildList {
            for (r in 0 until 8) for (p in 0 until 8) {
                add(SendAggregate.requestCode(42L, r, p))
            }
        }
        assertEquals(64, codes.distinct().size)
        assertTrue(codes.all { it >= 0 })
        // Different messages must not share codes for the same slot.
        assertTrue(SendAggregate.requestCode(42L, 0, 0) != SendAggregate.requestCode(43L, 0, 0))
    }

    @Test
    fun `attempt ids are stable and unique`() {
        assertEquals("42:1:2", SendAggregate.attemptId(42L, 1, 2))
        val ids = buildList {
            for (r in 0 until 5) for (p in 0 until 5) add(SendAggregate.attemptId(42L, r, p))
        }
        assertEquals(25, ids.distinct().size)
    }

    /** Runs [block] for every permutation of [items] (Heap's algorithm). */
    private fun <T> permute(items: List<T>, block: (List<T>) -> Unit) {
        val work = items.toMutableList()
        fun go(k: Int) {
            if (k == 1) { block(work.toList()); return }
            for (i in 0 until k) {
                go(k - 1)
                if (k % 2 == 0) {
                    val t = work[i]; work[i] = work[k - 1]; work[k - 1] = t
                } else {
                    val t = work[0]; work[0] = work[k - 1]; work[k - 1] = t
                }
            }
        }
        go(work.size)
    }
}
