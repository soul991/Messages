package com.personal.detectivedialer.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.contacts.ContactNumber
import com.personal.detectivedialer.data.contacts.ContactsRepository
import com.personal.detectivedialer.data.contacts.DeviceContact
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.telecom.PhoneCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unified person/number detail. When the tapped number belongs to a saved contact
 * the page becomes the whole person: every one of their numbers is actionable and
 * the history is one merged timeline across all of them. An unknown number stays a
 * single-number page with a Save action. Either entry point (Contacts tab or a call
 * log row) resolves number → contact, so both land on the same page.
 */
data class PersonDetailState(
    val name: String,
    val photoUri: String?,
    val isSaved: Boolean,
    /** Numbers rendered as call/message rows — all of a contact's, or the lone unknown one. */
    val numbers: List<ContactNumber>,
    /** Merged history across [numbers], newest-first. */
    val history: List<CallLogEntry>,
    /** True for a multi-number person, so each history entry names the number used. */
    val annotateNumbers: Boolean,
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val contactsRepository: ContactsRepository,
    private val phoneCaller: PhoneCaller,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rawNumber: String = savedStateHandle.get<String>("number").orEmpty()

    // Resolved contact for [rawNumber] (null = unknown/unsaved). Re-resolved after a
    // Save so the page flips from number mode to person mode without a restart.
    private val contact = MutableStateFlow<DeviceContact?>(null)
    private val loaded = MutableStateFlow(false)
    // Numbers whose history to observe; empty until the contact lookup finishes so
    // we never briefly query the wrong (single-number) slice for a saved person.
    private val queryNumbers = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val history = queryNumbers.flatMapLatest { nums ->
        if (nums.isEmpty()) flowOf(emptyList()) else callRepository.observeCallsByNumbers(nums)
    }

    val state: StateFlow<PersonDetailState?> =
        combine(loaded, contact, history) { isLoaded, c, calls ->
            if (!isLoaded) null else buildState(c, calls)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { resolve() }

    private fun resolve() {
        viewModelScope.launch {
            val c = contactsRepository.contactForNumber(rawNumber)
            contact.value = c
            queryNumbers.value = c?.numbers?.map { it.number } ?: listOf(rawNumber)
            loaded.value = true
        }
    }

    private fun buildState(c: DeviceContact?, calls: List<CallLogEntry>): PersonDetailState {
        val numbers = c?.numbers ?: listOf(ContactNumber(label = "", number = rawNumber))
        // Session 1 priority: saved contact name → network CNAP → the number → Unknown.
        val name = c?.name
            ?: calls.firstOrNull { it.callerName.isNotBlank() }?.callerName
            ?: rawNumber.ifBlank { "Unknown" }
        return PersonDetailState(
            name = name,
            photoUri = c?.photoUri,
            isSaved = c != null,
            numbers = numbers,
            history = calls,
            annotateNumbers = numbers.size > 1,
        )
    }

    fun placeCall(number: String): Boolean = phoneCaller.placeCall(number)

    /**
     * After the user saves this number from the system Contacts UI: stamp the new
     * name onto history rows, then re-resolve so the header and actions switch to
     * the saved-person layout live.
     */
    fun onContactSaved() {
        viewModelScope.launch {
            callRepository.refreshCallerName(rawNumber)
            resolve()
        }
    }
}
