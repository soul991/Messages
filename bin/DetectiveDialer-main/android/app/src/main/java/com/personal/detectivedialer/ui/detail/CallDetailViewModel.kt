package com.personal.detectivedialer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    private val repository: CallRepository,
) : ViewModel() {

    private var currentNumber: String = ""

    fun call(id: String) = repository.observeCall(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: String) {
        viewModelScope.launch {
            val entry = repository.refreshCall(id)
            currentNumber = entry?.number.orEmpty()
        }
    }

    fun block(entry: CallLogEntry?) {
        val number = entry?.number ?: return
        viewModelScope.launch { repository.block(number) }
    }

    fun allow(entry: CallLogEntry?) {
        val number = entry?.number ?: return
        viewModelScope.launch { repository.allow(number, label = "From call ${entry.id}") }
    }

    /**
     * Re-resolve the caller name after the user saved this number from the system
     * Contacts UI, so the detail header updates without a restart.
     */
    fun onContactSaved(number: String) {
        if (number.isBlank()) return
        viewModelScope.launch { repository.refreshCallerName(number) }
    }
}
