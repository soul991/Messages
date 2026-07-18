package com.personal.detectivedialer.ui.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.repository.SmsConversation
import com.personal.detectivedialer.data.repository.SmsProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the Messages tab conversation list, read from the system SMS provider. */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: SmsProviderRepository,
) : ViewModel() {

    val conversations: StateFlow<List<SmsConversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _hasPermission = MutableStateFlow(hasReadSms())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    fun refreshPermission() { _hasPermission.value = hasReadSms() }

    private fun hasReadSms(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
