package com.messages.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewParserTest {

    @Test
    fun `extracts first https url and trims trailing punctuation`() {
        assertEquals(
            "https://example.com/a",
            LinkPreviewParser.firstUrl("see https://example.com/a. thanks"),
        )
        assertNull(LinkPreviewParser.firstUrl("http://insecure.example.com only"))
        assertNull(LinkPreviewParser.firstUrl("no links here"))
    }

    @Test
    fun `parses open graph tags`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Order #42 shipped &amp; on the way"/>
            <meta property="og:site_name" content="ShopCo">
            <meta property="og:image" content="https://cdn.shop.co/i.jpg">
            <title>fallback</title>
            </head></html>
        """.trimIndent()
        val p = LinkPreviewParser.parse("https://shop.co/x", html)!!
        assertEquals("Order #42 shipped & on the way", p.title)
        assertEquals("ShopCo", p.siteName)
        assertEquals("https://cdn.shop.co/i.jpg", p.imageUrl)
    }

    @Test
    fun `falls back to title tag and rejects non-https images`() {
        val html = """
            <html><head>
            <meta property="og:image" content="http://cdn.shop.co/i.jpg">
            <title>Plain Title</title>
            </head></html>
        """.trimIndent()
        val p = LinkPreviewParser.parse("https://shop.co/x", html)!!
        assertEquals("Plain Title", p.title)
        assertNull(p.siteName)
        assertNull(p.imageUrl)
    }

    @Test
    fun `no title means no preview`() {
        assertNull(LinkPreviewParser.parse("https://x.co", "<html><body>hi</body></html>"))
    }

    @Test
    fun `tracking numbers match india post format only`() {
        assertEquals(listOf(5..17), TrackingNumbers.ranges("AWB: RX123456789IN today"))
        assertEquals(emptyList<IntRange>(), TrackingNumbers.ranges("ref 1234567890"))
        assertEquals(emptyList<IntRange>(), TrackingNumbers.ranges("RX12345678IN short"))
    }
}
