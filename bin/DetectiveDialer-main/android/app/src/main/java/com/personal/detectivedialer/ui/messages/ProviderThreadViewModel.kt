package com.personal.detectivedialer.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.repository.ProviderSms
import com.personal.detectivedialer.data.repository.SmsProviderRepository
import com.personal.detectivedialer.service.ContactsHelper
import com.personal.detectivedialer.telecom.PhoneCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** UI state for one conversation from the system SMS provider. */
data class ThreadUiState(
    val messages: List<ProviderSms> = emptyList(),
    /** More (older) messages remain and can be paged in. */
    val hasMore: Boolean = false,
    /** Whether the composer should be shown — false for A2P headers / short codes. */
    val repliable: Boolean = true,
)

/** One conversation, read from the system SMS provider, with optimistic sends. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderThreadViewModel @Inject constructor(
    private val repository: SmsProviderRepository,
    private val contacts: ContactsHelper,
    private val phoneCaller: PhoneCaller,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val address: String = savedStateHandle.get<String>("sender").orEmpty()

    /** Whether a reply reaches anyone; drives whether the composer is shown at all. */
    val repliable: Boolean = repository.isRepliable(address)

    // Resolve the contact name off the main thread; fall back to the number.
    private val _title = MutableStateFlow(address.ifBlank { "Conversation" })
    val title: StateFlow<String> = _title.asStateFlow()

    // How many messages to load; bumped by loadOlder() to page in history.
    private val pageLimit = MutableStateFlow(SmsProviderRepository.DEFAULT_PAGE)

    // Messages just sent, shown immediately until the provider observer catches up.
    private val optimistic = MutableStateFlow<List<ProviderSms>>(emptyList())

    val state: StateFlow<ThreadUiState> =
        combine(
            pageLimit.flatMapLatest { limit -> repository.observeThread(address, limit) },
            optimistic,
        ) { page, pending ->
            // Drop optimistic rows once a matching real (outgoing) message shows up.
            val realBodies = page.messages.filter { !it.incoming }.map { it.body }.toMutableList()
            val stillPending = pending.filter { p ->
                val i = realBodies.indexOf(p.body)
                if (i >= 0) { realBodies.removeAt(i); false } else true
            }
            ThreadUiState(
                messages = (page.messages + stillPending).sortedBy { it.date },
                hasMore = page.hasMore,
                repliable = repliable,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ThreadUiState(repliable = repliable),
        )

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { contacts.displayNameFor(address) }
                ?.let { _title.value = it }
        }
    }

    /** Page in an older chunk of the conversation (called on scroll-to-top). */
    fun loadOlder() {
        if (state.value.hasMore) {
            pageLimit.value += SmsProviderRepository.DEFAULT_PAGE
        }
    }

    fun send(body: String) {
        val text = body.trim()
        if (text.isBlank() || address.isBlank() || !repliable) return
        // Optimistic echo so the bubble appears instantly.
        optimistic.value = optimistic.value + ProviderSms(
            id = -System.currentTimeMillis(),
            address = address,
            body = text,
            date = System.currentTimeMillis(),
            incoming = false,
        )
        viewModelScope.launch { repository.send(address, text) }
    }

    fun call(): Boolean = phoneCaller.placeCall(address)

    fun canPlaceCalls(): Boolean = phoneCaller.canPlaceCalls()
}
