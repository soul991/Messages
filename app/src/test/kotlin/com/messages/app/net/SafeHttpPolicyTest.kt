package com.messages.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * V2-08/09: the two pure decisions that stand between a message-supplied link
 * and a socket — which URLs are eligible at all, and which resolved addresses
 * the DNS override is allowed to hand back. Both are exercised directly; the
 * transport around them is OkHttp's.
 */
class SafeHttpPolicyTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(
            byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())
        )

    private fun v6(vararg head: Int): InetAddress {
        val bytes = ByteArray(16)
        head.forEachIndexed { i, v -> bytes[i] = v.toByte() }
        bytes[15] = 1
        return InetAddress.getByAddress(bytes)
    }

    // --- URL policy ----------------------------------------------------------

    @Test
    fun `plain https urls are eligible`() {
        assertNotNull(SafeHttp.validateUrl("https://example.com/a/b?c=d"))
        assertNotNull(SafeHttp.validateUrl("https://example.com:443/"))
    }

    @Test
    fun `non-https schemes are refused`() {
        assertNull(SafeHttp.validateUrl("http://example.com/"))
        assertNull(SafeHttp.validateUrl("file:///etc/passwd"))
        assertNull(SafeHttp.validateUrl("ftp://example.com/"))
        assertNull(SafeHttp.validateUrl("not a url"))
    }

    @Test
    fun `embedded credentials are refused`() {
        // Credentials in a link are a phishing shape as much as an SSRF one.
        assertNull(SafeHttp.validateUrl("https://user@example.com/"))
        assertNull(SafeHttp.validateUrl("https://user:pass@example.com/"))
    }

    @Test
    fun `non-443 ports are refused`() {
        // Port scanning via preview timing is the reason this is pinned.
        assertNull(SafeHttp.validateUrl("https://example.com:8443/"))
        assertNull(SafeHttp.validateUrl("https://example.com:22/"))
    }

    // --- address policy ------------------------------------------------------

    @Test
    fun `ordinary public addresses are allowed`() {
        assertTrue(SafeHttp.isPublicAddress(v4(93, 184, 216, 34)))
        assertTrue(SafeHttp.isPublicAddress(v4(8, 8, 8, 8)))
        assertTrue(SafeHttp.isPublicAddress(v6(0x20, 0x01, 0x4a, 0x60)))
    }

    @Test
    fun `loopback and private ranges are refused`() {
        assertFalse(SafeHttp.isPublicAddress(v4(127, 0, 0, 1)))
        assertFalse(SafeHttp.isPublicAddress(v4(10, 0, 0, 5)))
        assertFalse(SafeHttp.isPublicAddress(v4(172, 16, 3, 4)))
        assertFalse(SafeHttp.isPublicAddress(v4(192, 168, 1, 1)))
        assertFalse(SafeHttp.isPublicAddress(v4(0, 0, 0, 0)))
    }

    @Test
    fun `link-local including the metadata endpoint is refused`() {
        // 169.254.169.254 is the cloud metadata address every SSRF probe tries.
        assertFalse(SafeHttp.isPublicAddress(v4(169, 254, 169, 254)))
        assertFalse(SafeHttp.isPublicAddress(v6(0xfe, 0x80)))
    }

    @Test
    fun `carrier-grade nat and reserved ranges are refused`() {
        assertFalse(SafeHttp.isPublicAddress(v4(100, 64, 0, 1)))   // CGNAT low
        assertFalse(SafeHttp.isPublicAddress(v4(100, 127, 255, 1)))// CGNAT high
        assertFalse(SafeHttp.isPublicAddress(v4(192, 0, 0, 8)))    // IETF protocol
        assertFalse(SafeHttp.isPublicAddress(v4(198, 18, 0, 1)))   // benchmark
        assertFalse(SafeHttp.isPublicAddress(v4(198, 19, 0, 1)))   // benchmark
        assertFalse(SafeHttp.isPublicAddress(v4(240, 0, 0, 1)))    // reserved
        assertFalse(SafeHttp.isPublicAddress(v4(255, 255, 255, 255)))
    }

    @Test
    fun `addresses adjacent to blocked ranges stay allowed`() {
        // The boundaries are where an over-broad rule would quietly break real hosts.
        assertTrue(SafeHttp.isPublicAddress(v4(100, 63, 255, 255)))
        assertTrue(SafeHttp.isPublicAddress(v4(100, 128, 0, 1)))
        assertTrue(SafeHttp.isPublicAddress(v4(192, 1, 0, 1)))
        assertTrue(SafeHttp.isPublicAddress(v4(198, 17, 0, 1)))
        assertTrue(SafeHttp.isPublicAddress(v4(198, 20, 0, 1)))
        // 223.255.255.254 is the last unicast address before multicast begins.
        assertTrue(SafeHttp.isPublicAddress(v4(223, 255, 255, 254)))
    }

    @Test
    fun `ipv6 multicast and unique-local are refused`() {
        assertFalse(SafeHttp.isPublicAddress(v6(0xfc, 0x00)))
        assertFalse(SafeHttp.isPublicAddress(v6(0xfd, 0x00)))
        assertFalse(SafeHttp.isPublicAddress(v6(0xff, 0x02)))
    }
}
