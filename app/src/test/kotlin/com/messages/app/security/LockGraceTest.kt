package com.messages.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockGraceTest {

    @Test
    fun `immediately grace always relocks`() {
        assertTrue(LockGrace.shouldRelock(LockGrace.IMMEDIATELY, backgroundedAtMs = 1_000, nowMs = 1_001))
        assertTrue(LockGrace.shouldRelock(LockGrace.IMMEDIATELY, backgroundedAtMs = 0, nowMs = 0))
    }

    @Test
    fun `short trip within grace does not relock`() {
        // Camera round trip: backgrounded 10s under a 1-minute grace.
        assertFalse(
            LockGrace.shouldRelock(LockGrace.ONE_MINUTE, backgroundedAtMs = 100_000, nowMs = 110_000)
        )
    }

    @Test
    fun `trip exceeding grace relocks`() {
        assertTrue(
            LockGrace.shouldRelock(LockGrace.ONE_MINUTE, backgroundedAtMs = 100_000, nowMs = 160_000)
        )
        assertTrue(
            LockGrace.shouldRelock(LockGrace.FIVE_MINUTES, backgroundedAtMs = 500, nowMs = 300_500)
        )
    }

    @Test
    fun `trip exactly at grace boundary relocks`() {
        assertTrue(
            LockGrace.shouldRelock(LockGrace.ONE_MINUTE, backgroundedAtMs = 100_000, nowMs = 160_000)
        )
    }

    @Test
    fun `unknown background stamp fails safe to relock`() {
        assertTrue(LockGrace.shouldRelock(LockGrace.FIVE_MINUTES, backgroundedAtMs = 0, nowMs = 999_999))
        assertTrue(LockGrace.shouldRelock(LockGrace.FIVE_MINUTES, backgroundedAtMs = -1, nowMs = 999_999))
    }

    @Test
    fun `just under five minute grace does not relock`() {
        assertFalse(
            LockGrace.shouldRelock(LockGrace.FIVE_MINUTES, backgroundedAtMs = 1_000, nowMs = 300_999)
        )
    }

    @Test
    fun `external trip suppresses relock while fresh`() {
        // Camera round trip 30s after launch — no relock, even at "Immediately".
        assertTrue(LockGrace.externalTripActive(armedAtMs = 1_000, nowMs = 31_000))
    }

    @Test
    fun `external trip expires after timeout`() {
        val armed = 1_000L
        assertFalse(
            LockGrace.externalTripActive(armed, armed + LockGrace.EXTERNAL_RESULT_TIMEOUT_MS)
        )
        assertFalse(LockGrace.externalTripActive(armed, armed + 3_600_000))
    }

    @Test
    fun `no armed launch means no suppression`() {
        assertFalse(LockGrace.externalTripActive(armedAtMs = 0, nowMs = 5_000))
        assertFalse(LockGrace.externalTripActive(armedAtMs = -1, nowMs = 5_000))
    }

    @Test
    fun `labels resolve and unknown value falls back to immediately`() {
        assertEquals("Immediately", LockGrace.label(0))
        assertEquals("After 1 minute", LockGrace.label(60_000))
        assertEquals("After 5 minutes", LockGrace.label(300_000))
        assertEquals("Immediately", LockGrace.label(42))
    }
}
