package com.messages.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-36 guard.
 *
 * User-visible copy was hardcoded in Kotlin across the app — screen titles,
 * notification bodies, accessibility labels, error text. Moving it into
 * `strings.xml` was a sweep, and a sweep decays: `Text("Save")` is shorter to
 * type than `Text(stringResource(R.string.action_save))`, and the file next to
 * yours is what you copy from.
 *
 * So this reads the sources for the shapes that put a sentence on screen and
 * insists the argument is not a literal. It is a lexical guard, not a semantic
 * one — it cannot tell that a resource says the right thing, only that the copy
 * lives somewhere a translator can reach.
 */
class HardcodedStringTest {

    private val sources = File("src/main/kotlin")
    private val strings = File("src/main/res/values/strings.xml")

    @Test
    fun `the sources are where this test thinks they are`() {
        // Otherwise the guards below pass by finding nothing at all.
        assertTrue(
            "expected app sources at ${sources.absolutePath}",
            sources.isDirectory && sources.walkTopDown().any { it.extension == "kt" },
        )
        assertTrue("expected ${strings.absolutePath}", strings.isFile)
    }

    @Test
    fun `no composable renders a literal string`() {
        assertEquals(
            "put the copy in strings.xml and read it with stringResource — a " +
                "literal here ships English to every locale",
            emptyList<String>(),
            offenders(RENDERED_LITERAL),
        )
    }

    @Test
    fun `no notification is built from a literal string`() {
        // Notifications are the copy a user reads without opening the app, and
        // they are built off the composition, where `stringResource` is not
        // available — which is exactly why they were the last holdouts.
        assertEquals(emptyList<String>(), offenders(NOTIFICATION_LITERAL))
    }

    @Test
    fun `no accessibility label is a literal string`() {
        // A screen reader is the only way some users get this text at all, so
        // an untranslated label is not a cosmetic problem for them.
        assertEquals(emptyList<String>(), offenders(SEMANTICS_LITERAL))
    }

    /**
     * Counted nouns go through `<plurals>`, not concatenation.
     *
     * English needs two forms; Polish needs three, Arabic six. `"$n messages"`
     * cannot express that no matter how it is translated, so the shape has to
     * be gone, not merely translated.
     */
    @Test
    fun `no count is glued to a noun with string interpolation`() {
        assertEquals(emptyList<String>(), offenders(COUNTED_NOUN))
    }

    /**
     * The guards above pass by finding nothing, which is also what a broken
     * guard does. [withoutDiagnostics] in particular blanks whole spans of
     * source, so a scan that went wrong would erase the evidence and report
     * success. This runs the patterns over copy written to offend.
     */
    @Test
    fun `the guards still catch what they are for`() {
        val sample = """
            Text("Save")
            Text("${'$'}count messages")
            contentDescription = "Close"
            .setContentTitle("New message")
            Log.w(TAG, "released ${'$'}n stale claim(s)")
            throw IllegalStateException("expected ${'$'}n rows")
            Text(stringResource(R.string.action_save))
        """.trimIndent()

        val clean = withoutDiagnostics(sample)
        fun hits(p: Regex) = clean.lines().count { p.containsMatchIn(it) }

        assertEquals("Text(\"Save\")", 1, hits(RENDERED_LITERAL))
        assertEquals("contentDescription", 1, hits(SEMANTICS_LITERAL))
        assertEquals("setContentTitle", 1, hits(NOTIFICATION_LITERAL))
        // The counted noun is caught; the log and the exception carrying the
        // same shape are not.
        assertEquals("\"\$count messages\" only", 1, hits(COUNTED_NOUN))
        // Blanking must not eat the lines around it.
        assertEquals(sample.lines().size, clean.lines().size)
        assertTrue("stringResource call survived", clean.contains("R.string.action_save"))
    }

    @Test
    fun `every string resource this test can see is reachable by a translator`() {
        // A sanity check on the table itself: an empty or comment-only
        // strings.xml would make every guard above vacuous.
        val text = strings.readText()
        assertTrue("no <string> entries found", text.contains("<string name="))
        assertTrue("no <plurals> entries found", text.contains("<plurals name="))
    }

    private fun offenders(pattern: Regex): List<String> =
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                withoutDiagnostics(file.readText()).lines().withIndex()
                    .filter { (_, line) -> pattern.containsMatchIn(line) }
                    .filterNot { (_, line) -> line.trimStart().startsWith("//") }
                    .filterNot { (_, line) -> line.trimStart().startsWith("*") }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
            .toList()

    /**
     * The source with developer-facing calls blanked out.
     *
     * Log lines and exception messages are deliberately English. They are read
     * in logcat and in crash reports, and a translated log is one you cannot
     * grep for — so `"worker attempt $n failed"` is correct as written and must
     * not be reported. Blanking them is what lets the guards stay broad enough
     * to also cover copy assembled outside a composable, in a ViewModel or a
     * helper, which is where a counted noun usually gets built.
     *
     * Newlines survive so the reported line numbers still point at the source.
     */
    private fun withoutDiagnostics(source: String): String {
        val out = StringBuilder(source)
        for (call in DIAGNOSTIC_CALL.findAll(source)) {
            val open = call.range.last
            val close = balancedClose(source, open) ?: continue
            // A runaway span means the scan lost track; blanking it would
            // quietly switch the guard off for the rest of the file.
            if (close - open > MAX_DIAGNOSTIC_SPAN) continue
            for (i in open..close) {
                if (out[i] != '\n') out[i] = ' '
            }
        }
        return out.toString()
    }

    /**
     * Index of the `)` closing the `(` at [open], skipping over string literals
     * — `"released $n stale claim(s)"` has an unbalanced paren inside quotes.
     */
    private fun balancedClose(text: CharSequence, open: Int): Int? {
        var depth = 0
        var quoted = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '\\' -> i++
                quoted && c == '"' -> quoted = false
                quoted -> Unit
                c == '"' -> quoted = true
                c == '(' -> depth++
                c == ')' -> if (--depth == 0) return i
            }
            i++
        }
        return null
    }

    private companion object {
        /**
         * A literal handed to something that draws it. `Text("…")` and the
         * Material slots that take a string directly.
         */
        val RENDERED_LITERAL = Regex(
            """(\bText\(|placeholder\s*=\s*\{\s*Text\(|headlineContent\s*=\s*\{\s*Text\(|""" +
                """supportingContent\s*=\s*\{\s*Text\()\s*"[A-Za-z]""",
        )

        /** The NotificationCompat.Builder setters that carry visible copy. */
        val NOTIFICATION_LITERAL = Regex(
            """\.(setContentTitle|setContentText|setSubText|setTicker|setLabel)\(\s*"[A-Za-z]""",
        )

        /** Labels that exist only for assistive technology. */
        val SEMANTICS_LITERAL = Regex(
            """(contentDescription|stateDescription|onClickLabel)\s*=\s*"[A-Za-z]""",
        )

        /**
         * A number interpolated next to a word — `"$n messages"`, `"${x.size}
         * results"`. Deliberately narrow: it looks for an interpolation
         * immediately followed by a space and a letter, inside a literal that
         * is not a log tag or a URL.
         */
        val COUNTED_NOUN = Regex(
            """"[^"]*\$\{?[\w.()]+}?\s+[a-z]{3,}[^"]*"\s*[,)]""",
        )

        /**
         * Calls whose string arguments are for developers, not users. Extend
         * this when a new diagnostic shape starts showing up as a false report
         * — but only for text a user genuinely cannot reach.
         */
        val DIAGNOSTIC_CALL = Regex("""(\b(?:android\.util\.)?Log\.\w+|\bDiag\.\w+|\bthrow\s+[\w.]+)\(""")

        /** A blanked span longer than this means the paren scan went wrong. */
        const val MAX_DIAGNOSTIC_SPAN = 2_000
    }
}
