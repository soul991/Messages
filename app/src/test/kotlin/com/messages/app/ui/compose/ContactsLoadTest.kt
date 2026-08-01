package com.messages.app.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-42: the recipient picker caught every failure into `emptyList()` — a
 * denied permission, a null cursor, a thrown query and a phone with no contacts
 * saved all produced the identical "No contacts to show". The three failures
 * are the app being unable to read; only the fourth is a fact about the user's
 * data, and it was the only one the screen ever stated.
 *
 * The null-cursor case is the one worth naming: the manifest documents that the
 * contacts provider returns null *silently* without a `<queries>` entry, with
 * READ_CONTACTS granted. It throws nothing, so a `catch` never sees it, and it
 * reached the user as a claim that their address book was empty.
 */
class ContactsLoadTest {

    private fun notice(
        state: ContactsLoad,
        query: String = "",
        rowCount: Int = 0,
        pastGrace: Boolean = true,
        dialable: Boolean = false,
    ) = contactsNotice(state, query, rowCount, pastGrace, dialable)

    // ---- the four states are four different sentences ----

    @Test
    fun `a denied permission is not an empty address book`() {
        assertEquals(ContactsNotice.PERMISSION, notice(ContactsLoad.PermissionDenied))
    }

    @Test
    fun `a failed provider read is not an empty address book`() {
        assertEquals(ContactsNotice.FAILED, notice(ContactsLoad.Failed("no cursor")))
    }

    @Test
    fun `an address book that really is empty says so`() {
        assertEquals(ContactsNotice.NO_CONTACTS, notice(ContactsLoad.Ready(emptyList())))
    }

    @Test
    fun `a query that matches nothing is a search result, not a broken picker`() {
        assertEquals(
            ContactsNotice.NO_MATCHES,
            notice(ContactsLoad.Ready(emptyList()), query = "zzz"),
        )
    }

    @Test
    fun `every state maps to a distinct notice`() {
        val notices = listOf(
            notice(ContactsLoad.PermissionDenied),
            notice(ContactsLoad.Failed("boom")),
            notice(ContactsLoad.Loading),
            notice(ContactsLoad.Ready(emptyList())),
            notice(ContactsLoad.Ready(emptyList()), query = "zzz"),
        )
        assertEquals("states collapsed onto the same notice", notices.size, notices.toSet().size)
    }

    // ---- the empty claim is only ever made about a finished read ----

    @Test
    fun `no unfinished state can claim there are no contacts`() {
        val unfinished = listOf(
            ContactsLoad.Loading,
            ContactsLoad.PermissionDenied,
            ContactsLoad.Failed("boom"),
        )
        for (state in unfinished) {
            for (query in listOf("", "ann")) {
                for (pastGrace in listOf(false, true)) {
                    val result = notice(state, query = query, pastGrace = pastGrace)
                    assertTrue(
                        "$state (query='$query', pastGrace=$pastGrace) rendered as $result",
                        result !in setOf(ContactsNotice.NO_CONTACTS, ContactsNotice.NO_MATCHES),
                    )
                }
            }
        }
    }

    @Test
    fun `a fast load stays quiet and a slow one says it is loading`() {
        assertEquals(ContactsNotice.NONE, notice(ContactsLoad.Loading, pastGrace = false))
        assertEquals(ContactsNotice.LOADING, notice(ContactsLoad.Loading, pastGrace = true))
    }

    @Test
    fun `rows on screen suppress every notice`() {
        // Including during a reload: stale rows beat a placeholder that replaces
        // a working list with a skeleton on every retry.
        val states = listOf(
            ContactsLoad.Loading,
            ContactsLoad.PermissionDenied,
            ContactsLoad.Failed("boom"),
            ContactsLoad.Ready(listOf(PickerContact("Ann", "+15551234", "Mobile"))),
        )
        for (state in states) {
            assertEquals(ContactsNotice.NONE, notice(state, rowCount = 1))
        }
    }

    @Test
    fun `a dialable query silences the no-match hint but not the failures`() {
        // The "Send to <number>" row is already on screen; repeating "type a
        // number to message it directly" underneath it is noise.
        assertEquals(
            ContactsNotice.NONE,
            notice(ContactsLoad.Ready(emptyList()), query = "5550199", dialable = true),
        )
        assertEquals(
            ContactsNotice.PERMISSION,
            notice(ContactsLoad.PermissionDenied, query = "5550199", dialable = true),
        )
        assertEquals(
            ContactsNotice.FAILED,
            notice(ContactsLoad.Failed("boom"), query = "5550199", dialable = true),
        )
    }

    @Test
    fun `rows are readable only from a finished read`() {
        assertEquals(emptyList<PickerContact>(), ContactsLoad.Loading.rows())
        assertEquals(emptyList<PickerContact>(), ContactsLoad.PermissionDenied.rows())
        assertEquals(emptyList<PickerContact>(), ContactsLoad.Failed("boom").rows())
        val ann = PickerContact("Ann", "+15551234", "Mobile")
        assertEquals(listOf(ann), ContactsLoad.Ready(listOf(ann)).rows())
    }

    // ---- filtering, which the state change had to preserve ----

    private val book = listOf(
        PickerContact("Ann Lee", "+1 555 0100", "Mobile"),
        PickerContact("Bob Ray", "(555) 0199", "Work"),
    )

    @Test
    fun `a blank query is the whole address book`() {
        assertEquals(book, filterContacts(book, "   "))
    }

    @Test
    fun `name matching ignores case`() {
        assertEquals(listOf(book[0]), filterContacts(book, "ann"))
    }

    @Test
    fun `number matching ignores punctuation and spacing`() {
        assertEquals(listOf(book[1]), filterContacts(book, "5550199"))
    }

    @Test
    fun `fewer than three digits does not match numbers`() {
        // Otherwise "5" returns everyone and the list reads as unfiltered.
        assertEquals(emptyList<PickerContact>(), filterContacts(book, "55"))
    }

    // ---- anti-decay guard ----

    @Test
    fun `the picker does not go back to swallowing failures into an empty list`() {
        val vm = File("src/main/kotlin/com/messages/app/ui/compose/NewMessageScreen.kt")
        assertTrue("expected ${vm.absolutePath}", vm.isFile)
        val text = vm.readText()
        assertTrue(
            "the contacts flow must carry ContactsLoad, not a bare list",
            text.contains("StateFlow<ContactsLoad>"),
        )
        assertTrue(
            "a null cursor must be reported as a failure, not as no contacts",
            text.contains("ContactsLoad.Failed(\"Contacts provider returned no cursor\")"),
        )
        assertTrue(
            "granting the permission must trigger a re-read — the process is not restarted",
            text.contains("fun refresh()"),
        )
        // `catch (_: Exception) { emptyList() }` is the original defect.
        val swallow = Regex("""catch\s*\([^)]*\)\s*\{\s*emptyList\(\)""")
        assertTrue(
            "contacts failures are being swallowed into an empty list again",
            !swallow.containsMatchIn(text),
        )
    }
}
