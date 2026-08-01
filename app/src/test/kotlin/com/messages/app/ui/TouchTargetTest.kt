package com.messages.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-40 / V2-41 guard.
 *
 * Three controls shipped below the 48 dp minimum: the compact "Not spam" /
 * "Why?" / "Report" actions under filtered bubbles (hard-capped at 32 dp by a
 * `heightIn(max = …)`), the sender badge (a bare 16 dp glyph carrying the
 * click), and the composer's quick-replies button (an `IconButton` shrunk from
 * its 48 dp default to 36 dp to keep the pill short).
 *
 * All three are the same mistake: a visual size decision written onto the
 * element that also owns the touch handling. Nothing in the build catches that
 * — lint's touch-target checks do not run on Compose — so these assertions read
 * the sources for the two shapes that caused it.
 */
class TouchTargetTest {

    private val sources = File("src/main/kotlin")

    @Test
    fun `the sources are where this test thinks they are`() {
        // Otherwise the guards below pass by finding nothing at all.
        assertTrue(
            "expected app sources at ${sources.absolutePath}",
            sources.isDirectory && sources.walkTopDown().any { it.extension == "kt" },
        )
        assertTrue(File(sources, "com/messages/app/ui/common/TouchTargets.kt").isFile)
    }

    @Test
    fun `nothing hard-caps its height below the minimum touch target`() {
        // `heightIn(min = 32.dp, max = 32.dp)` was the exact shape of V2-40: a
        // ceiling, not a floor, so no enforcement downstream could undo it.
        assertEquals(
            "a max height below 48.dp caps the touch target, not just the visual",
            emptyList<String>(),
            offenders(CAPPED_HEIGHT),
        )
    }

    @Test
    fun `no icon button is shrunk below the minimum touch target`() {
        // IconButton is already 48 dp; `.size(36.dp)` on one is a deliberate
        // override of exactly the thing that made it accessible.
        assertEquals(emptyList<String>(), offenders(SHRUNK_ICON_BUTTON))
    }

    @Test
    fun `the two controls the finding named still carry their wrappers`() {
        val badge = File(sources, "com/messages/app/ui/common/SenderBadge.kt").readText()
        val chat = File(sources, "com/messages/app/ui/chat/ChatScreen.kt").readText()

        // The badge: reserved space, a button role, and an action-oriented
        // label — the description alone told a scanner what it was, not what
        // tapping it would do.
        assertTrue("badge lost its reserved touch area", "minTouchTarget()" in badge)
        assertTrue("badge lost its button role", "role = Role.Button" in badge)
        assertTrue("badge lost its action label", "onClickLabel" in badge)

        // The compact actions: the click must sit on the sized box, so a tap
        // anywhere in the 48 dp lands, not only on the 32 dp of text.
        assertTrue("compact actions lost their reserved touch area", "minTouchTarget()" in chat)
    }

    @Test
    fun `adjacent compact actions keep visible separation`() {
        // 2 dp between two 48 dp targets reads as one strip: a tap near the
        // boundary was a coin flip between "Not spam" and "Report", and those
        // two do opposite things.
        val chat = File(sources, "com/messages/app/ui/chat/ChatScreen.kt").readText()
        val row = Regex("""Arrangement\.spacedBy\((\d+)\.dp\)\s*\)\s*\{\s*\n\s*CompactAction""")
        val gap = row.find(chat)?.groupValues?.get(1)?.toInt()
        assertTrue("could not find the compact-action row", gap != null)
        assertTrue("compact actions are $gap dp apart; want at least 8", gap!! >= 8)
    }

    private fun offenders(pattern: Regex): List<String> =
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filterNot { (_, line) -> line.trimStart().let { it.startsWith("//") || it.startsWith("*") } }
                    .filter { (_, line) -> pattern.containsMatchIn(line) }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
            .toList()

    private companion object {
        /**
         * A dp literal below 48. The leading look-behind matters: without it the
         * engine happily matches the "0.dp" tail of "480.dp".
         */
        private const val UNDER_48 = """(?<![\d.])(?:[0-9]|[0-3][0-9]|4[0-7])\.dp"""

        /** `heightIn(… max = N.dp)` / `sizeIn(… maxHeight = N.dp)` with N < 48. */
        val CAPPED_HEIGHT = Regex("""max(?:Height)?\s*=\s*$UNDER_48""")

        /** An `IconButton` family component given an explicit size under 48 dp. */
        val SHRUNK_ICON_BUTTON = Regex("""IconButton\(.*\.size\($UNDER_48\)""")
    }
}
