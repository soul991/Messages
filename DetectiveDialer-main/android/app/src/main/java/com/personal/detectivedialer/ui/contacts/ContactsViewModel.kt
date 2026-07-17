package com.personal.detectivedialer.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.contacts.ContactsRepository
import com.personal.detectivedialer.data.contacts.DeviceContact
import com.personal.detectivedialer.telecom.PhoneCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository,
    private val phoneCaller: PhoneCaller,
) : ViewModel() {

    private val all = MutableStateFlow<List<DeviceContact>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Contacts filtered by the live search query (name or number substring). */
    val contacts: StateFlow<List<DeviceContact>> = combine(all, _query) { list, q ->
        val term = q.trim()
        if (term.isEmpty()) {
            list
        } else {
            list.filter { c ->
                c.name.contains(term, ignoreCase = true) ||
                    c.numbers.any { it.number.contains(term) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            all.value = repository.loadContacts()
            _loading.value = false
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun hasPermission(): Boolean = repository.hasPermission()

    fun canPlaceCalls(): Boolean = phoneCaller.canPlaceCalls()

    /** Returns false when CALL_PHONE is missing so the UI can request it. */
    fun placeCall(number: String): Boolean = phoneCaller.placeCall(number)
}
