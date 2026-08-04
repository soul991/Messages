package com.messages.app.receiver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-34. Every receiver used to build its own scope per broadcast: two had no
 * SupervisorJob and no catch (a repository throw crashed the process from a
 * delivery-report callback), none had a time budget, and `finish()` was on a
 * single path. These pin the shared policy that replaced them — the pending
 * result is closed on every exit, and the never-lose recovery runs for a
 * timeout exactly as it does for a failure.
 */
class ReceiverWorkTest {

    private val budget = 1_000L

    @Test
    fun `ordinary work finishes the broadcast exactly once`() = runTest {
        var finished = 0
        var ran = false
        ReceiverWork.guard("t", budget, onFailure = null, finish = { finished++ }, log = ::ignore) {
            ran = true
        }
        assertTrue(ran)
        assertEquals(1, finished)
    }

    @Test
    fun `a throw is contained, recovered from, and still finishes`() = runTest {
        var finished = 0
        var recovered: Throwable? = null
        ReceiverWork.guard(
            "t", budget,
            onFailure = { recovered = it },
            finish = { finished++ },
            log = ::ignore,
        ) {
            error("provider is unavailable")
        }
        // Not rethrown: this is what used to reach the default handler and take
        // the process down.
        assertEquals("provider is unavailable", recovered?.message)
        assertEquals(1, finished)
    }

    @Test
    fun `work that outlives its budget is cut off and recovered from`() = runTest {
        var finished = 0
        var recovered: Throwable? = null
        var completed = false
        ReceiverWork.guard(
            "t", budget,
            onFailure = { recovered = it },
            finish = { finished++ },
            log = ::ignore,
        ) {
            delay(budget * 10)
            completed = true
        }
        assertTrue(!completed)
        assertTrue(recovered is CancellationException)
        assertEquals(1, finished)
    }

    @Test
    fun `the recovery path runs even though the block was cancelled`() = runTest {
        // The point of NonCancellable: a timeout cancels the coroutine, and the
        // fallback write must still be allowed to suspend and complete.
        var wrote = false
        ReceiverWork.guard(
            "t", budget,
            onFailure = {
                delay(50)
                wrote = true
            },
            finish = {},
            log = ::ignore,
        ) {
            delay(budget * 10)
        }
        assertTrue(wrote)
    }

    @Test
    fun `a failing recovery cannot take the process with it`() = runTest {
        var finished = 0
        ReceiverWork.guard(
            "t", budget,
            onFailure = { error("recovery failed too") },
            finish = { finished++ },
            log = ::ignore,
        ) {
            error("original failure")
        }
        assertEquals(1, finished)
    }

    @Test
    fun `a finish that throws does not escape`() = runTest {
        // A broadcast already finished by the platform throws on a second
        // finish(); that must not become an uncaught exception.
        ReceiverWork.guard(
            "t", budget,
            onFailure = null,
            finish = { throw IllegalStateException("already finished") },
            log = ::ignore,
        ) {
            // nothing
        }
    }

    @Test
    fun `an outer cancellation still closes the broadcast`() = runTest {
        var finished = 0
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                ReceiverWork.guard(
                    "t", budget,
                    onFailure = null,
                    finish = { finished++ },
                    log = ::ignore,
                ) {
                    throw CancellationException("scope is going away")
                }
            }
        }
        assertEquals(1, finished)
    }

    private fun ignore(message: String, t: Throwable) = Unit
}
