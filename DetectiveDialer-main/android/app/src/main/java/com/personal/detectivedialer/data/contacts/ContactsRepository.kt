package com.personal.detectivedialer.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A phone number belonging to a contact, with its type label ("Mobile", "Home"…). */
data class ContactNumber(val label: String, val number: String)

/** A device contact, aggregated across all of its phone rows. */
data class DeviceContact(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val numbers: List<ContactNumber>,
) {
    val primaryNumber: String get() = numbers.firstOrNull()?.number.orEmpty()
    /** Letter used for the A–Z fast-scroll index. */
    val sortLetter: String
        get() = name.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
}

/** Loads contacts from the system ContactsContract provider. */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** All contacts with at least one phone number, sorted alphabetically by name. */
    suspend fun loadContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        // Aggregate rows (one per number) into one entry per contact.
        val byId = LinkedHashMap<Long, MutableContact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val typeIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx)?.trim().orEmpty()
                    val number = c.getString(numberIdx)?.trim().orEmpty()
                    if (name.isBlank() || number.isBlank()) continue
                    val typeLabel = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, c.getInt(typeIdx), c.getString(labelIdx))
                        .toString()
                    val entry = byId.getOrPut(id) {
                        MutableContact(id, name, c.getString(photoIdx))
                    }
                    // De-dupe identical numbers that appear under multiple accounts.
                    if (entry.numbers.none { it.number == number }) {
                        entry.numbers.add(ContactNumber(typeLabel, number))
                    }
                }
            }
        }
        byId.values
            .map { DeviceContact(it.id, it.name, it.photoUri, it.numbers.toList()) }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * The saved contact that owns [number], or null when it isn't in the address
     * book. Matches on the last 10 digits so a stored "+91 98…" and a dialed
     * "98…" resolve to the same person (the app's numbers are Indian; this mirrors
     * how the OEM dialer tolerates country-code/format differences). Used by the
     * unified detail page to turn any tapped number into the full person.
     */
    suspend fun contactForNumber(number: String): DeviceContact? {
        val target = matchKey(number)
        if (target.isBlank()) return null
        return loadContacts().firstOrNull { c -> c.numbers.any { matchKey(it.number) == target } }
    }

    /** Digits only, reduced to the last 10 so format/country-code variants compare equal. */
    private fun matchKey(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private class MutableContact(val id: Long, val name: String, val photoUri: String?) {
        val numbers = mutableListOf<ContactNumber>()
    }
}
