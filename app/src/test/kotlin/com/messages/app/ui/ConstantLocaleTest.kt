package com.messages.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-45 guard.
 *
 * The fix replaced ten file-level `SimpleDateFormat` properties across nine
 * screens with [com.messages.app.ui.common.AppDateFormat]. That is a sweep, and
 * a sweep decays: the next screen that needs a timestamp will copy whatever the
 * file next to it does, and the old shape is shorter to type.
 *
 * This reads the sources rather than the runtime, so it costs nothing and — the
 * point — cannot be satisfied by a UI test that never changed language.
 * `ConstantLocale` is the lint rule for half of this; lint does not flag the
 * other half (a hardcoded `Locale.US` pattern), and lint warnings do not fail
 * this build, so the guard lives here.
 */
class ConstantLocaleTest {

    private val sources = File("src/main/kotlin")

    /**
     * The two files allowed to construct a formatter directly.
     *
     * `LocalizedFormats` is the cache itself. `SettingsScreen` builds a
     * `yyyy-MM-dd` stamp for a backup *file name*, which must stay invariant —
     * a localized month or a non-Gregorian calendar would make backups sort
     * wrongly — and builds it per click, so it cannot go stale.
     */
    private val allowed = setOf("LocalizedFormats.kt", "SettingsScreen.kt")

    @Test
    fun `the sources are where this test thinks they are`() {
        // Otherwise the guards below pass by finding nothing at all.
        assertTrue(
            "expected app sources at ${sources.absolutePath}",
            sources.isDirectory && sources.walkTopDown().any { it.extension == "kt" },
        )
        assertTrue(File(sources, "com/messages/app/ui/common/AppDateFormat.kt").isFile)
    }

    @Test
    fun `no screen constructs its own date formatter`() {
        assertEquals(
            "use AppDateFormat — a formatter built here freezes the locale and " +
                "time zone that happened to be current when the class loaded",
            emptyList<String>(),
            offenders(FORMATTER),
        )
    }

    @Test
    fun `no formatter is held in a property that outlives a configuration change`() {
        // The specific shape the finding named: a top-level or companion `val`
        // initialised once per process.
        assertEquals(emptyList<String>(), offenders(HELD_FORMATTER))
    }

    private fun offenders(pattern: Regex): List<String> =
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> pattern.containsMatchIn(line) }
                    .filterNot { (_, line) -> line.trimStart().startsWith("//") }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
            .toList()

    private companion object {
        /** Any direct construction of a date/time formatter. */
        val FORMATTER = Regex("""(SimpleDateFormat\(|DateTimeFormatter\.ofPattern\()""")

        /** …specifically one assigned to a property rather than a local. */
        val HELD_FORMATTER = Regex(
            """va[lr]\s+\w+(\s*:\s*[\w.<>]+)?\s*=\s*(SimpleDateFormat\(|DateTimeFormatter\.)""",
        )
    }
}
