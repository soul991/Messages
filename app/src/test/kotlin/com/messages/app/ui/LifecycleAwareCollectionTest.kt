package com.messages.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-35: every screen collected its ViewModel state with `collectAsState()`,
 * which keeps collecting while the UI is stopped — the app in the background
 * still recomposes, still holds `WhileSubscribed` upstreams open, and still
 * keeps database and provider queries alive behind them.
 *
 * The fix was a sweep across 16 screens, and a sweep is exactly the kind of fix
 * that decays: the next screen someone writes will reach for the shorter name.
 * This guard is the cheapest way to keep it from coming back — it reads the
 * sources rather than the runtime, so it costs nothing and cannot be satisfied
 * by a passing UI test that never went to the background.
 */
class LifecycleAwareCollectionTest {

    private val sources = File("src/main/kotlin")

    @Test
    fun `the sources are where this test thinks they are`() {
        // Otherwise the guard below passes by finding nothing at all.
        assertTrue(
            "expected app sources at ${sources.absolutePath}",
            sources.isDirectory && sources.walkTopDown().any { it.extension == "kt" },
        )
    }

    @Test
    fun `no screen collects flows without lifecycle awareness`() {
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> LIFECYCLE_UNAWARE.containsMatchIn(line) }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
            .toList()
        assertTrue(
            "Use collectAsStateWithLifecycle() instead — collection must stop with the UI:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        /** `.collectAsState(` but not `.collectAsStateWithLifecycle(`. */
        val LIFECYCLE_UNAWARE = Regex("""\.collectAsState\s*\(""")
    }
}
