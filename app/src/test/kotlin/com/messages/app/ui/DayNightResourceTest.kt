package com.messages.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-37 / V2-44 guard.
 *
 * The app theme and the home-screen widgets were both light-only: a hardcoded
 * `android:Theme.Material.Light.NoActionBar` window that flashed white before
 * the first composition in dark mode, and widget layouts with `#FFFBFE`
 * surfaces and `#1C1B1F` text baked in — a widget cannot read the Compose
 * theme, its RemoteViews resolve against the *launcher host's* configuration,
 * so day/night resources are the only mechanism available to it.
 *
 * Both fixes are resource-shaped, which means neither has a runtime assertion
 * that would catch a regression. This reads the resource files directly: it is
 * the cheapest thing that fails when someone adds a night-sensitive color and
 * forgets its dark counterpart, or reaches for a literal in a widget layout
 * because that is what the surrounding file used to look like.
 */
class DayNightResourceTest {

    private val res = File("src/main/res")

    /** Colors that are meaningless without a dark counterpart. */
    private val nightSensitive = setOf(
        "window_background",
        "widget_background",
        "widget_text_primary",
        "widget_text_secondary",
        "widget_text_tertiary",
    )

    @Test
    fun `the resources are where this test thinks they are`() {
        // Otherwise every assertion below passes by finding nothing at all.
        assertTrue("expected app resources at ${res.absolutePath}", res.isDirectory)
        assertTrue(colorNames("values").isNotEmpty())
    }

    @Test
    fun `every night-sensitive color is defined in both day and night`() {
        val day = colorNames("values")
        val night = colorNames("values-night")
        for (name in nightSensitive) {
            assertTrue("$name missing from values/colors.xml", name in day)
            assertTrue("$name missing from values-night/colors.xml", name in night)
        }
    }

    @Test
    fun `no night override exists without a default to fall back to`() {
        // A values-night-only color resolves to nothing in day mode and fails
        // the build on some AGP versions and silently at runtime on others.
        val day = colorNames("values")
        val orphans = colorNames("values-night") - day
        assertEquals("colors defined only in values-night", emptySet<String>(), orphans)
    }

    @Test
    fun `the v31 dynamic palette overrides come in day-night pairs`() {
        // V2-44: below API 31 the system_neutral attrs do not exist, so the
        // static values in values/ are the fallback. Above it, an override
        // present in only one of the two qualifies for one configuration and
        // leaves the other on a static color that no longer matches.
        assertEquals(colorNames("values-v31"), colorNames("values-night-v31"))
        assertTrue(colorNames("values-v31").isNotEmpty())
        assertTrue(colorNames("values-v31").all { it in nightSensitive })
    }

    @Test
    fun `the app theme has a night variant`() {
        val day = File(res, "values/themes.xml").readText()
        val night = File(res, "values-night/themes.xml").readText()
        assertTrue("Theme.Messages missing from values", THEME.containsMatchIn(day))
        assertTrue("Theme.Messages missing from values-night", THEME.containsMatchIn(night))
        // The whole point: the night window must not paint the light background.
        assertTrue(
            "values-night theme must not inherit the Light platform theme",
            "Theme.Material.Light" !in night,
        )
        assertTrue(
            "night theme must turn off light status bar icons",
            Regex("""windowLightStatusBar">false<""").containsMatchIn(night),
        )
    }

    @Test
    fun `widget layouts carry no hardcoded colors`() {
        val files = listOf(
            File(res, "layout/widget_unread.xml"),
            File(res, "layout/widget_protection.xml"),
            File(res, "drawable/widget_bg.xml"),
        )
        val offenders = files.flatMap { file ->
            assertTrue("missing ${file.path}", file.isFile)
            file.readLines().withIndex()
                .filter { (_, line) -> LITERAL_COLOR.containsMatchIn(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(
            "widget resources must reference @color/* so they follow the host's night mode",
            emptyList<String>(),
            offenders,
        )
    }

    private fun colorNames(qualifier: String): Set<String> {
        val file = File(res, "$qualifier/colors.xml")
        if (!file.isFile) return emptySet()
        return COLOR_NAME.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    private companion object {
        val COLOR_NAME = Regex("""<color\s+name="([^"]+)"""")
        val THEME = Regex("""<style\s+name="Theme\.Messages"""")

        /** `#RGB`, `#RRGGBB`, `#AARRGGBB` — anything but a resource reference. */
        val LITERAL_COLOR = Regex("""android:(?:\w*[Cc]olor|background)="#""")
    }
}
