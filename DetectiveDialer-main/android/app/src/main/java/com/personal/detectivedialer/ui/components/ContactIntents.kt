package com.personal.detectivedialer.ui.components

import android.content.Intent
import android.provider.ContactsContract

/**
 * Intent that hands off to the system Contacts UI to save/attach a phone number,
 * pre-filled. ACTION_INSERT_OR_EDIT lets the user either create a new contact or
 * add the number to an existing one — the OEM Contacts app owns the editor, so we
 * don't build our own (see bug batch #5).
 */
fun insertOrEditContactIntent(number: String): Intent =
    Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
    }
