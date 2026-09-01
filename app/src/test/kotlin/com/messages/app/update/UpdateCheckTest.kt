package com.messages.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `isNewer correctly compares semantic versions`() {
        assertTrue(UpdateCheck.isNewer("1.2.2", "1.2.1"))
        assertTrue(UpdateCheck.isNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateCheck.isNewer("1.3", "1.2.9"))
        assertTrue(UpdateCheck.isNewer("v1.2.2", "1.2.1"))
        assertTrue(UpdateCheck.isNewer("1.2.10", "1.2.9"))

        assertFalse(UpdateCheck.isNewer("1.2.1", "1.2.1"))
        assertFalse(UpdateCheck.isNewer("1.2.0", "1.2.1"))
        assertFalse(UpdateCheck.isNewer("1.1.9", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("v1.2.1", "1.2.1"))
    }

    @Test
    fun `parseTag cleans tag names correctly`() {
        val json = """{"tag_name": "v1.2.2"}"""
        assertEquals("1.2.2", UpdateCheck.parseTag(json))

        val jsonNoV = """{"tag_name": "1.2.2"}"""
        assertEquals("1.2.2", UpdateCheck.parseTag(jsonNoV))

        val jsonEmpty = """{"tag_name": ""}"""
        assertNull(UpdateCheck.parseTag(jsonEmpty))

        val jsonInvalid = """{"invalid": true}"""
        assertNull(UpdateCheck.parseTag(jsonInvalid))
    }

    @Test
    fun `parseDownloadUrl extracts direct apk url from assets`() {
        val json = """
            {
                "tag_name": "v1.2.2",
                "html_url": "https://github.com/soul991/Messages/releases/tag/v1.2.2",
                "assets": [
                    {
                        "name": "Messages.apk",
                        "browser_download_url": "https://github.com/soul991/Messages/releases/download/v1.2.2/Messages.apk",
                        "size": 36900000
                    }
                ]
            }
        """.trimIndent()

        val downloadUrl = UpdateCheck.parseDownloadUrl(json)
        assertEquals("https://github.com/soul991/Messages/releases/download/v1.2.2/Messages.apk", downloadUrl)
    }

    @Test
    fun `parseReleaseInfo extracts full metadata`() {
        val json = """
            {
                "tag_name": "v1.2.2",
                "published_at": "2026-09-01T12:00:00Z",
                "body": "## What's New\n- In-app update engine with animations\n- Direct download and install",
                "html_url": "https://github.com/soul991/Messages/releases/tag/v1.2.2",
                "assets": [
                    {
                        "name": "Messages.apk",
                        "browser_download_url": "https://github.com/soul991/Messages/releases/download/v1.2.2/Messages.apk",
                        "size": 36900000
                    }
                ]
            }
        """.trimIndent()

        val apkUrl = "https://github.com/soul991/Messages/releases/download/v1.2.2/Messages.apk"
        val info = UpdateCheck.parseReleaseInfo(json, "1.2.2", apkUrl)

        assertNotNull(info)
        info!!
        assertEquals("1.2.2", info.version)
        assertEquals("v1.2.2", info.tagName)
        assertEquals("2026-09-01T12:00:00Z", info.publishedAt)
        assertTrue(info.changelog.contains("In-app update engine"))
        assertEquals(apkUrl, info.apkUrl)
        assertEquals(36900000L, info.apkSizeBytes)
        assertEquals("https://github.com/soul991/Messages/releases/tag/v1.2.2", info.htmlUrl)
    }

    @Test
    fun `formatBytes returns human readable sizes`() {
        assertEquals("500 B", AppUpdateManager.formatBytes(500L))
        assertEquals("1.5 KB", AppUpdateManager.formatBytes(1536L))
        assertEquals("35.2 MB", AppUpdateManager.formatBytes(36909875L))
    }
}
