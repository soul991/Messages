package com.messages.app.ui.compose

/**
 * V2-42. What the recipient picker knows about the address book.
 *
 * The picker collapsed a denied permission, a null cursor and a thrown query
 * into `emptyList()`, which renders identically to a phone with no contacts
 * saved. Three of those four are the app failing to read; only one of them is a
 * statement about the user's data, and it was the only one the UI ever made.
 *
 * [PermissionDenied] is kept separate from [Failed] because it is the one case
 * with a fix the user can perform, and the fix is not "try again".
 */
sealed interface ContactsLoad {

    data object Loading : ContactsLoad

    data class Ready(val contacts: List<PickerContact>) : ContactsLoad

    /** READ_CONTACTS not granted, or revoked while the process was alive. */
    data object PermissionDenied : ContactsLoad

    /**
     * The provider was queryable and did not answer: a null cursor (the
     * package-visibility case the manifest warns about — it fails *silently*
     * even with the permission granted) or a thrown query. [reason] is small
     * print for a bug report, not instructions.
     */
    data class Failed(val reason: String) : ContactsLoad
}

/** The contacts to list right now — empty for every state but [ContactsLoad.Ready]. */
fun ContactsLoad.rows(): List<PickerContact> =
    (this as? ContactsLoad.Ready)?.contacts.orEmpty()

/** What, if anything, to say under the list. */
enum class ContactsNotice {
    /** Say nothing: either there are rows, or the load is too young to mention. */
    NONE,
    LOADING,
    PERMISSION,
    FAILED,

    /** A completed read of an address book that really is empty. */
    NO_CONTACTS,

    /** A completed read that matched nothing for the current query. */
    NO_MATCHES,
}

/**
 * Maps picker state to the notice under the list.
 *
 * [rowCount] is the count *after* filtering, so a non-empty result never gets a
 * notice — including while a reload is in flight, where the stale rows are
 * still the best thing on screen.
 *
 * [pastGrace] carries the same 150 ms rule as the conversation list (V2-43): a
 * contacts query that answers in a frame should not flash a placeholder.
 *
 * [dialable] suppresses only the no-match hint, and only because the screen is
 * already offering a "Send to <number>" row directly above it. The failure
 * notices are never suppressed: a broken address book is worth saying even when
 * the typed number happens to be sendable.
 */
fun contactsNotice(
    state: ContactsLoad,
    query: String,
    rowCount: Int,
    pastGrace: Boolean,
    dialable: Boolean = false,
): ContactsNotice = when {
    rowCount > 0 -> ContactsNotice.NONE
    state is ContactsLoad.PermissionDenied -> ContactsNotice.PERMISSION
    state is ContactsLoad.Failed -> ContactsNotice.FAILED
    state is ContactsLoad.Loading && pastGrace -> ContactsNotice.LOADING
    state is ContactsLoad.Loading -> ContactsNotice.NONE
    dialable -> ContactsNotice.NONE
    query.isBlank() -> ContactsNotice.NO_CONTACTS
    else -> ContactsNotice.NO_MATCHES
}

/**
 * Name/number match for the picker query.
 *
 * The digit path needs three digits before it will match: fewer than that and
 * every contact whose number contains "1" comes back, which is not a search
 * result, it is the address book in a different order.
 */
fun filterContacts(contacts: List<PickerContact>, query: String): List<PickerContact> {
    if (query.isBlank()) return contacts
    val queryDigits = query.filter { it.isDigit() || it == '+' }
    return contacts.filter { c ->
        c.name.contains(query, ignoreCase = true) ||
            (queryDigits.length >= 3 &&
                c.number.filter { it.isDigit() || it == '+' }.contains(queryDigits))
    }
}
