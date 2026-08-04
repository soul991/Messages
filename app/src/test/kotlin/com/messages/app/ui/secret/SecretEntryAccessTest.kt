package com.messages.app.ui.secret

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-39. The locked space had exactly one entrance: a 1.5-second timed press on
 * the Home title, written as raw pointer input. A gesture like that does not
 * exist for TalkBack, Switch Access, a keyboard or a D-pad — those users could
 * not reach the feature at all — and it is out of reach for many motor
 * impairments besides.
 *
 * The fix has three moving parts that have to stay in agreement, so this test
 * pins all three:
 *
 * 1. An opt-in preference, off by default, because a permanently-labelled
 *    "Open locked space" action would announce the feature to every screen
 *    reader user and undo the concealment the space is built around.
 * 2. Semantics on the Home title that are *added* to the gesture, never
 *    substituted for it — the press has to keep working for everyone else.
 * 3. A Settings switch that lives in main Settings, not inside the locked
 *    space. Putting the toggle behind the gesture it replaces would leave the
 *    people it is for unable to turn it on.
 *
 * And one thing that must never become true: neither route may skip the
 * credential prompt. An accessible entrance is a door, not a missing lock.
 *
 * `enabled()`/`setEnabled()` read SharedPreferences and so need an Android
 * `Context` these JVM tests do not have; what is checkable here is the contract
 * around them and the shape of the call sites.
 */
class SecretEntryAccessTest {

    private val access = File("src/main/kotlin/com/messages/app/ui/secret/SecretEntryAccess.kt")
    private val home = File("src/main/kotlin/com/messages/app/ui/home/HomeScreen.kt")
    private val settings = File("src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt")
    private val activity = File("src/main/kotlin/com/messages/app/MainActivity.kt")
    private val strings = File("src/main/res/values/strings.xml")

    @Test
    fun `the sources are where this test thinks they are`() {
        for (source in listOf(access, home, settings, activity, strings)) {
            assertTrue("expected ${source.absolutePath}", source.isFile)
        }
    }

    // ---- the preference ----

    @Test
    fun `the action and the settings row say the same thing`() {
        // One resource, two surfaces. If they drift, a user who reads
        // "Open locked space" in Settings hears something else from TalkBack
        // and has no way to know they are the same door.
        //
        // V2-36 turned the label into a string resource, so the identity is now
        // "both sites resolve the same id" rather than "both read the same
        // constant". The id itself is checked by name — a JVM test has no
        // resource table to resolve it against.
        assertTrue(
            "ACTION_LABEL must point at the shared resource",
            access.readText().contains("ACTION_LABEL: Int = R.string.secret_entry_action"),
        )
        assertTrue(
            "the Settings row must use the shared label, not a copy of the text",
            settings.readText().contains("title = stringResource(SecretEntryAccess.ACTION_LABEL)"),
        )
        assertTrue(
            "the resource the two surfaces share must actually exist",
            strings.readText()
                .contains("<string name=\"secret_entry_action\">Open locked space</string>"),
        )
    }

    @Test
    fun `concealment is what you get until you ask for something else`() {
        assertTrue(
            "the accessible entry must default off — an unrequested labelled " +
                "action discloses the feature to anyone running a screen reader",
            access.readText().contains("getBoolean(KEY, false)"),
        )
    }

    // ---- the Home title ----

    @Test
    fun `the timed press survives`() {
        val text = home.readText()
        for (required in listOf("pointerInput(Unit)", "awaitFirstDown", "withTimeout(1_500)")) {
            assertTrue(
                "the original gesture lost $required — the accessible route is " +
                    "an addition, not a replacement",
                text.contains(required),
            )
        }
    }

    @Test
    fun `the title publishes both an action and a long-click, and only on opt-in`() {
        val text = home.readText()
        assertTrue(
            "the Home title must read the opt-in before publishing anything",
            text.contains("SecretEntryAccess.enabled(context)"),
        )
        assertTrue(
            "semantics must be conditional on the opt-in",
            text.contains("if (!accessibleEntry) Modifier else Modifier.semantics"),
        )
        // Switch Access and TalkBack surface these two differently, so both are
        // published rather than picking one and hoping.
        for (required in listOf("onLongClick(label = entryLabel)", "customActions = listOf(")) {
            assertTrue("the accessible entry lost: $required", text.contains(required))
        }
    }

    // ---- the Settings switch ----

    @Test
    fun `the switch lives in main settings and writes the preference`() {
        val text = settings.readText()
        assertTrue(
            "the toggle must be reachable without the gesture it replaces",
            text.contains("stringResource(R.string.settings_accessible_entry_title)"),
        )
        assertTrue(
            "the toggle's title resource must exist",
            strings.readText().contains(
                "<string name=\"settings_accessible_entry_title\">" +
                    "Accessible locked-space entry</string>",
            ),
        )
        assertTrue(
            "the switch must persist the choice",
            text.contains("SecretEntryAccess.setEnabled(entryCtx, it)"),
        )
    }

    // ---- no bypass ----

    @Test
    fun `every entry point lands on the credential gate`() {
        val text = activity.readText()
        val lambdas = text.split("onSecretEntry = {").drop(1)
        assertEquals(
            "expected the Home title and the Settings row to be the two entry points",
            2,
            lambdas.size,
        )
        // The lambda body ends at the first "}," — the inner if/else closes with
        // "} else {" and a bare "}", neither of which is followed by a comma.
        for (body in lambdas.map { it.substringBefore("},") }) {
            assertTrue(
                "an entry point must route to the prompt or to first-time setup",
                body.contains("secret_prompt") && body.contains("secret_setup"),
            )
            assertFalse(
                "an entry point must never navigate straight into the unlocked " +
                    "space — that would make accessibility a way past the code",
                body.contains("secret_space"),
            )
        }
    }
}
