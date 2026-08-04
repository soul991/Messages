package com.messages.app.drive

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-36. Asserts on [DriveSignInError.reason] — the technical code that goes
 * into the message — rather than the sentence around it. The sentence now
 * comes from the resource table, so an assertion on it would only be checking
 * the English copy.
 */
class DriveSignInErrorTest {

    @Test
    fun `user cancelling the picker is reported as cancelled, not silently dropped`() {
        val e = ApiException(Status(GoogleSignInStatusCodes.SIGN_IN_CANCELLED))
        assertTrue(DriveSignInError.reason(e).contains("cancel", ignoreCase = true))
    }

    @Test
    fun `developer error names the OAuth client misconfiguration`() {
        val e = ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR))
        assertTrue(DriveSignInError.reason(e).contains("DEVELOPER_ERROR"))
    }

    @Test
    fun `non-api exceptions fall back to their own message`() {
        assertEquals("boom", DriveSignInError.reason(RuntimeException("boom")))
    }

    @Test
    fun `exceptions without a message fall back to the class name`() {
        assertEquals("NumberFormatException", DriveSignInError.reason(NumberFormatException()))
    }
}
