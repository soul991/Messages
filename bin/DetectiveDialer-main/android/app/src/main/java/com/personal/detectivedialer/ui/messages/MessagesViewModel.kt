package com.personal.detectivedialer.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.local.SmsMessage
import com.personal.detectivedialer.data.local.SmsThreadSummary
import com.personal.detectivedialer.data.repository.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: SmsRepository,
) : ViewModel() {

    val threads: StateFlow<List<SmsThreadSummary>> = repository.observeThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blocked: StateFlow<List<SmsMessage>> = repository.observeBlocked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedBadge: StateFlow<Int> = repository.observeBlockedUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun restore(id: Long) = viewModelScope.launch { repository.restore(id) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun neverBlock(sender: String) = viewModelScope.launch { repository.neverBlock(sender) }

    fun markRead(id: Long) = viewModelScope.launch { repository.markRead(id) }

    fun send(destination: String, body: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repository.send(destination, body)) }
    }
}
