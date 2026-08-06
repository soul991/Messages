package com.messages.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-25: `mediaUri` carries two different kinds of reference. Getting this
 * wrong is silent — `File("content://mms/part/7")` is a valid relative path
 * object that simply never exists, so a provider-backed attachment would be
 * quietly dropped from backups and skipped on resend instead of erroring.
 */
class MediaRefTest {

    @Test
    fun `local paths resolve to a file`() {
        val path = "/data/user/0/com.messages.app/files/mms/42.jpg"
        val file = MediaRef.asFile(path)
        // Compared against File(path).path rather than the literal, because
        // `File.path` renders with the HOST's separator: on a Windows JVM the
        // same input comes back as `\data\user\0\...`, so a literal comparison
        // fails for a reason that has nothing to do with the behaviour under
        // test. On the device — and on CI, which is Linux — the two sides are
        // byte-identical, so this is not a weakened assertion. It still runs
        // everywhere (no assume/skip) and still fails if asFile mangles the
        // path, stops round-tripping it, or returns null for a local reference.
        assertNotNull(file)
        assertEquals(File(path).path, file?.path)
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
