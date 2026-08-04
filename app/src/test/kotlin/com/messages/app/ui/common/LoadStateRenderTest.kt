package com.messages.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-43: Home rendered a blank screen while its conversation query was in
 * flight, and — because the state was a nullable list — rendered the same blank
 * forever if the query threw. Two different situations, one indistinguishable
 * output, and neither of them said anything true to the user.
 *
 * [listRender] is where that decision now lives, so this is where it is pinned.
 * The table below is the finding restated as assertions: every state maps to a
 * distinct rendering, and the two that make claims about the data — EMPTY and
 * CONTENT — are reachable only from a query that actually finished.
 */
class LoadStateRenderTest {

    private fun render(state: LoadState<List<String>>, pastGrace: Boolean) =
        listRender(state, pastGrace)

    // ---- the decision table ----

    @Test
    fun `a fast load renders nothing rather than flashing a spinner`() {
        assertEquals(ListRender.NOTHING, render(LoadState.Loading, pastGrace = false))
    }

    @Test
    fun `a load that outlasts the grace admits it is loading`() {
        assertEquals(ListRender.LOADING, render(LoadState.Loading, pastGrace = true))
    }

    @Test
    fun `a failed query renders the failure, grace or not`() {
        // The grace exists to suppress a *spinner*, not an error. A failure that
        // arrives in 20 ms is still a failure and still needs its retry button;
        // withholding it for 150 ms would just reinstate the blank screen.
        val failed = LoadState.Failed("SQLiteException")
        assertEquals(ListRender.FAILED, render(failed, pastGrace = false))
        assertEquals(ListRender.FAILED, render(failed, pastGrace = true))
    }

    @Test
    fun `a completed empty query renders the empty state`() {
        assertEquals(ListRender.EMPTY, render(LoadState.Ready(emptyList()), pastGrace = false))
        assertEquals(ListRender.EMPTY, render(LoadState.Ready(emptyList()), pastGrace = true))
    }

    @Test
    fun `rows render as content`() {
        assertEquals(ListRender.CONTENT, render(LoadState.Ready(listOf("a")), pastGrace = false))
        assertEquals(ListRender.CONTENT, render(LoadState.Ready(listOf("a")), pastGrace = true))
    }

    // ---- the invariants the finding is actually about ----

    @Test
    fun `no unfinished state can claim the data is empty`() {
        // "No conversations yet" is a statement about the user's messages. The
        // bug was making it — or its silent cousin, the blank screen — before
        // anything had been read. Only Ready may produce EMPTY.
        val unfinished = listOf<LoadState<List<String>>>(
            LoadState.Loading,
            LoadState.Failed("boom"),
        )
        for (state in unfinished) {
            for (pastGrace in listOf(false, true)) {
                assertTrue(
                    "$state (pastGrace=$pastGrace) must not render as EMPTY or CONTENT",
                    render(state, pastGrace) !in setOf(ListRender.EMPTY, ListRender.CONTENT),
                )
            }
        }
    }

    @Test
    fun `every state renders something and only one thing`() {
        val states = listOf<LoadState<List<String>>>(
            LoadState.Loading,
            LoadState.Failed("boom"),
            LoadState.Ready(emptyList()),
            LoadState.Ready(listOf("a")),
        )
        val rendered = states.flatMap { s -> listOf(false, true).map { render(s, it) } }
        // All five outcomes are reachable: an unreachable one is a branch that
        // exists in the enum and not in the UI, which is how the empty and the
        // blank got conflated in the first place.
        assertEquals(ListRender.entries.toSet(), rendered.toSet())
    }

    @Test
    fun `valueOrNull yields rows only for a finished query`() {
        assertNull(LoadState.Loading.valueOrNull)
        assertNull(LoadState.Failed("boom").valueOrNull)
        assertEquals(listOf("a"), LoadState.Ready(listOf("a")).valueOrNull)
    }

    @Test
    fun `the grace is short enough to be invisible and long enough to cover a warm query`() {
        // Below ~100 ms the spinner flickers; above ~250 ms the screen reads as
        // frozen. Room answers a warm query well inside this window.
        assertTrue("grace was $LOADING_GRACE_MILLIS ms", LOADING_GRACE_MILLIS in 100L..250L)
    }

    // ---- anti-decay guard ----

    @Test
    fun `Home does not go back to a nullable list for load state`() {
        // The nullable list is what made the failure unrepresentable. Reading
        // the source is the only cheap way to catch a revert: a `null` meaning
        // "not loaded" type-checks fine and fails only in front of a user whose
        // database read threw.
        val vm = File("src/main/kotlin/com/messages/app/ui/home/HomeViewModel.kt")
        assertTrue("expected ${vm.absolutePath}", vm.isFile)
        val text = vm.readText()
        assertTrue(
            "HomeViewModel must expose conversations as LoadState, not a nullable list",
            text.contains("LoadState<List<ConversationEntity>>"),
        )
        assertTrue(
            "HomeViewModel must keep a retry path — a Failed flow is terminated and " +
                "cannot resume on its own",
            text.contains("fun retry()"),
        )
        val nullable = Regex("""StateFlow<List<ConversationEntity>\?>""")
        assertTrue(
            "nullable conversation list is back in HomeViewModel",
            !nullable.containsMatchIn(text),
        )
    }
}
