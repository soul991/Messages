package com.messages.core.backfill

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V2-25: the MMS table stores seconds where the SMS table stores milliseconds.
 * Multiplying an already-millisecond value would file the message tens of
 * thousands of years in the future, where it sorts above everything else in
 * every conversation forever.
 */
class MmsDateTest {

    @Test
    fun `seconds are scaled to milliseconds`() {
        // 2024-01-01T00:00:00Z
        assertEquals(1_704_067_200_000L, BackfillWorker.mmsDateToMillis(1_704_067_200L))
    }

    @Test
    fun `values already in milliseconds are left alone`() {
        assertEquals(1_704_067_200_000L, BackfillWorker.mmsDateToMillis(1_704_067_200_000L))
    }

    @Test
    fun `epoch zero stays epoch zero`() {
        assertEquals(0L, BackfillWorker.mmsDateToMillis(0L))
    }
}
