package com.messages.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-25: `mediaUri` carries two different kinds of reference. Getting this
 * wrong is silent — `File("content://mms/part/7")` is a valid relative path
 * object that simply never exists, so a provider-backed attachment would be
 * quietly dropped from backups and skipped on resend instead of erroring.
 */
class MediaRefTest {

    @Test
    fun `local paths resolve to a file`() {
        val file = MediaRef.asFile("/data/user/0/com.messages.app/files/mms/42.jpg")
        assertEquals("/data/user/0/com.messages.app/files/mms/42.jpg", file?.path)
    }

    @Test
    fun `provider uris never resolve to a file`() {
        assertNull(MediaRef.asFile("content://mms/part/7"))
        assertNull(MediaRef.asFile("file:///storage/emulated/0/pic.png"))
    }

    @Test
    fun `absent and blank references resolve to nothing`() {
        assertNull(MediaRef.asFile(null))
        assertNull(MediaRef.asFile(""))
        assertNull(MediaRef.asFile("   "))
    }

    @Test
    fun `provider detection covers both uri schemes we store`() {
        assertTrue(MediaRef.isProviderUri("content://mms/part/7"))
        assertTrue(MediaRef.isProviderUri("file:///tmp/x"))
        assertFalse(MediaRef.isProviderUri("/data/files/mms/42.jpg"))
        // A path that merely mentions a scheme mid-string is still a path.
        assertFalse(MediaRef.isProviderUri("/data/files/content://not-a-uri"))
    }
}
