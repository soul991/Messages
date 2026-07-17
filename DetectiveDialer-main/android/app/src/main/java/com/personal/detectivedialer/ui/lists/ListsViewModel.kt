package com.personal.detectivedialer.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.local.AllowedNumber
import com.personal.detectivedialer.data.local.BlockedNumber
import com.personal.detectivedialer.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: CallRepository,
) : ViewModel() {

    val blocked = repository.observeBlocked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BlockedNumber>())

    val allowed = repository.observeAllowed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AllowedNumber>())

    fun block(number: String) = viewModelScope.launch { repository.block(number) }
    fun unblock(number: String) = viewModelScope.launch { repository.unblock(number) }
    fun allow(number: String, label: String) = viewModelScope.launch { repository.allow(number, label) }
    fun removeAllow(number: String) = viewModelScope.launch { repository.removeAllow(number) }
}
