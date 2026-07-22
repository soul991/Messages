package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun `extracts plain code`() {
        assertEquals("482913", OtpExtractor.extract("482913 is your OTP for login. Do not share."))
    }

    @Test
    fun `extracts code after label`() {
        assertEquals("7301", OtpExtractor.extract("Your verification code is 7301"))
    }

    @Test
    fun `skips digits glued to letters like account fragments`() {
        assertEquals(
            "567890",
            OtpExtractor.extract("A/c XX1234: OTP 567890 valid for 10 minutes"),
        )
    }

    @Test
    fun `ignores runs shorter than 4 or longer than 8`() {
        assertNull(OtpExtractor.extract("Call 191 now"))
        assertNull(OtpExtractor.extract("Ref 1234567890123 received"))
    }

    @Test
    fun `no digits means no code`() {
        assertNull(OtpExtractor.extract("Do not share your OTP with anyone"))
    }
}
