package com.messages.app.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsReceiveFallbackTest {

    @Test
    fun `known sender is retained for a recoverable placeholder`() {
        assertEquals("+919876543210", MmsReceiveFallback.addressOrUnknown(" +919876543210 "))
    }

    @Test
    fun `missing sender remains visible under an explicit unknown identity`() {
        assertEquals(MmsReceiveFallback.UNKNOWN_SENDER, MmsReceiveFallback.addressOrUnknown(null))
        assertEquals(MmsReceiveFallback.UNKNOWN_SENDER, MmsReceiveFallback.addressOrUnknown("   "))
    }

    @Test
    fun `placeholder clearly explains why the MMS body is unavailable`() {
        assertEquals(
            "[MMS message — couldn't be downloaded]",
            MmsReceiveFallback.placeholderBody(MmsReceiveFallback.DOWNLOAD_FAILURE),
        )
    }
}
