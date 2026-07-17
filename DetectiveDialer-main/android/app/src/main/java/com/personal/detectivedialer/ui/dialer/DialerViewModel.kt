package com.personal.detectivedialer.ui.dialer

import androidx.lifecycle.ViewModel
import com.personal.detectivedialer.telecom.PhoneCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Backs the dialpad: holds the typed number and places outgoing calls. */
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val phoneCaller: PhoneCaller,
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    fun append(digit: Char) { _input.value += digit }

    fun backspace() {
        _input.value = _input.value.dropLast(1)
    }

    fun clear() { _input.value = "" }

    fun setNumber(number: String) { _input.value = number }

    /**
     * Place an outgoing call through Telecom (see [PhoneCaller]).
     * Returns false when we lack CALL_PHONE or the number is empty.
     */
    fun placeCall(number: String = _input.value): Boolean = phoneCaller.placeCall(number)
}
