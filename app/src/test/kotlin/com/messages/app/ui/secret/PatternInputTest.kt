package com.messages.app.ui.secret

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-38: the pattern grid was a single [androidx.compose.foundation.Canvas] with
 * a drag detector — no child nodes, no actions, no state. TalkBack, Switch
 * Access, a D-pad and a physical keyboard could not enter a pattern at all, and
 * since a pattern can be the credential for the locked space, that is not a
 * degraded experience but a door those users cannot open.
 *
 * The tap path is what makes it operable, so the tap rule is pinned here, and
 * the source is guarded for the two properties that are easy to break later:
 * the dots must carry semantics, and they must NOT carry a pointer-consuming
 * click that would silently kill the drag path for everyone else.
 */
class PatternInputTest {

    // ---- the tap rule ----

    @Test
    fun `tapping an unselected dot appends it`() {
        assertEquals(listOf(0), patternAfterTap(emptyList(), 0))
        assertEquals(listOf(0, 4), patternAfterTap(listOf(0), 4))
    }

    @Test
    fun `order is preserved — a pattern is a sequence, not a set`() {
        var pattern = emptyList<Int>()
        for (dot in listOf(6, 3, 0, 1)) pattern = patternAfterTap(pattern, dot)
        assertEquals(listOf(6, 3, 0, 1), pattern)
    }

    @Test
    fun `tapping the most recent dot undoes it`() {
        // With a drag, lifting the finger is the undo. There is no lift here, so
        // without this the only recovery from a mistap is Clear and start over.
        assertEquals(listOf(0, 4), patternAfterTap(listOf(0, 4, 8), 8))
    }

    @Test
    fun `tapping an earlier dot truncates back to just before it`() {
        assertEquals(listOf(0), patternAfterTap(listOf(0, 4, 8), 4))
        assertEquals(emptyList<Int>(), patternAfterTap(listOf(0, 4, 8), 0))
    }

    @Test
    fun `a dot never appears twice`() {
        var pattern = listOf(0, 1, 2)
        pattern = patternAfterTap(pattern, 1)
        pattern = patternAfterTap(pattern, 1)
        assertEquals(listOf(0, 1), pattern)
        assertEquals(pattern.size, pattern.toSet().size)
    }

    @Test
    fun `the tap path can build every pattern the drag path can`() {
        // Any ordering of any subset — the drag is limited by what the hand can
        // reach without crossing; taps are not, so this is a superset.
        val target = listOf(2, 4, 6, 8, 0)
        var pattern = emptyList<Int>()
        for (dot in target) pattern = patternAfterTap(pattern, dot)
        assertEquals(target, pattern)
        assertTrue(pattern.size >= PATTERN_MIN_DOTS)
    }

    // ---- what gets said out loud ----

    @Test
    fun `dot labels describe position the way a person would`() {
        // V2-36: the sentence moved to the resource table; the arithmetic that
        // has to hold in every language stayed here.
        assertEquals(1 to 1, patternDotRowCol(0))
        assertEquals(2 to 3, patternDotRowCol(5))
        assertEquals(3 to 3, patternDotRowCol(8))
    }

    @Test
    fun `every dot has a distinct label`() {
        val positions = (0..8).map(::patternDotRowCol)
        assertEquals(9, positions.toSet().size)
    }

    @Test
    fun `the status line reports progress toward the floor`() {
        assertEquals(PatternStatus.EMPTY, patternStatus(0))
        assertEquals(PatternStatus.BELOW_MINIMUM, patternStatus(2))
        assertEquals(PatternStatus.READY, patternStatus(PATTERN_MIN_DOTS))
        assertEquals(PatternStatus.READY, patternStatus(PATTERN_MIN_DOTS + 3))
    }

    @Test
    fun `the status line never names a dot`() {
        // It is a live region: it is spoken on every change. Counting is safe to
        // say out loud in a room; which dots were chosen is not. The states are
        // rendered from resources that take a count and nothing else, so the
        // guard is that no state carries a dot identity — checked at the source,
        // since the strings themselves live in XML.
        val xml = File("src/main/res/values/strings.xml").readText()
        // Matched by name prefix, and accepting either declaration, rather than
        // from a fixed list of <string> names: a counted state belongs in
        // <plurals>, and a guard that only knew about <string> would quietly
        // stop covering it the moment someone did the right thing.
        val states = Regex(
            """<(string|plurals) name="(secret_pattern_status_\w+)">(.*?)</\1>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(xml).toList()

        assertTrue("no secret_pattern_status_* resources found", states.size >= 3)
        for (state in states) {
            val (_, name, value) = state.destructured
            assertFalse(
                "status $name leaked a position: $value",
                value.contains("Row", ignoreCase = true) ||
                    value.contains("column", ignoreCase = true),
            )
        }
    }

    // ---- source guards ----

    private val source = File("src/main/kotlin/com/messages/app/ui/secret/SecretInputs.kt")

    @Test
    fun `the source is where this test thinks it is`() {
        assertTrue("expected ${source.absolutePath}", source.isFile)
    }

    @Test
    fun `the pattern dots expose semantics, state and an action`() {
        val text = source.readText()
        for (required in listOf(
            "contentDescription = dotLabel",
            "patternDotLabel(",
            "selected = chosen",
            "stateDescription =",
            "onClick(",
            "liveRegion = LiveRegionMode.Polite",
        )) {
            assertTrue("pattern dots lost: $required", text.contains(required))
        }
    }

    @Test
    fun `the dots are reachable by keyboard and D-pad`() {
        val text = source.readText()
        assertTrue("dots must be focusable for D-pad traversal", text.contains(".focusable(enabled)"))
        for (key in listOf("Key.Enter", "Key.Spacebar", "Key.DirectionCenter")) {
            assertTrue("no key handling for $key", text.contains(key))
        }
    }

    @Test
    fun `the accessible overlay does not consume pointer input`() {
        // `Modifier.clickable` on a dot consumes the pointer-down, which stops
        // the drag detector underneath from ever seeing the gesture: adding
        // accessibility this way would remove the primary input method and the
        // failure is silent — it compiles, and the grid simply stops drawing.
        val overlay = source.readText()
            .substringAfter("private fun PatternDotTarget")
            .substringBefore("\n}\n")
        assertFalse(
            "PatternDotTarget must not use clickable/toggleable — it would break the drag path",
            overlay.contains(".clickable") || overlay.contains(".toggleable") ||
                overlay.contains(".selectable") || overlay.contains("pointerInput"),
        )
    }

    @Test
    fun `a wrong code is announced, not only shaken`() {
        // The unlock screen's whole failure vocabulary was visual: a horizontal
        // shake and a line of neutral text. A screen-reader user submitted a
        // wrong pattern and heard nothing whatsoever.
        val prompt = File("src/main/kotlin/com/messages/app/ui/secret/SecretPromptScreen.kt")
        assertTrue("expected ${prompt.absolutePath}", prompt.isFile)
        val text = prompt.readText()
        assertTrue(
            "unlock failure text must be a live region",
            text.contains("liveRegion = LiveRegionMode.Assertive"),
        )
        // …and must stay free of the credential itself.
        assertFalse(
            "the announced failure must not echo the entry",
            text.contains("Text(\"Wrong code: \$entry") || text.contains("\$selected"),
        )
    }

    @Test
    fun `both input paths share one minimum-dot floor`() {
        val text = source.readText()
        assertEquals(4, PATTERN_MIN_DOTS)
        // Two hardcoded 4s would drift; the drag commit and the Done button must
        // agree, or a pattern accepted one way is rejected the other.
        assertFalse(
            "the drag path hardcodes its own minimum instead of PATTERN_MIN_DOTS",
            Regex("""selected\.size\s*>=\s*4""").containsMatchIn(text),
        )
        assertTrue(
            text.split("selected.size >= PATTERN_MIN_DOTS").size - 1 >= 2,
        )
    }
}
