package com.personal.detectivedialer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.telecom.PhoneCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardState(
    val screenedToday: Int = 0,
    val blockedToday: Int = 0,
    val allowedToday: Int = 0,
    val calls: List<CallLogEntry> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: CallRepository,
    private val phoneCaller: PhoneCaller,
) : ViewModel() {

    // Midnight is computed per emission (not at VM creation) so the "today"
    // stats don't go stale when the app stays alive across midnight.
    val state = repository.observeCalls().map { calls ->
        val midnight = startOfToday()
        val today = calls.filter { it.timestamp >= midnight }
        DashboardState(
            screenedToday = today.size,
            blockedToday = today.count { it.category == "SPAM" },
            allowedToday = today.count { it.category == "ALLOW" },
            calls = calls,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.syncCalls()
            // Pull block/allow lists too so server-side auto-blocks reach the device.
            repository.syncLists()
            // And the TRAI prefix-rule table (new rules without an APK rebuild).
            repository.syncScreeningRules()
            // Opportunistically resolve names for old rows that never had one —
            // picks up numbers the user has since saved as contacts.
            repository.backfillMissingNames()
        }
    }

    /**
     * Re-resolve a number's name after the user saved it from the call log and
     * returned from the system Contacts UI, so the row updates without a restart.
     */
    fun onContactSaved(number: String) {
        viewModelScope.launch { repository.refreshCallerName(number) }
    }

    fun block(number: String) {
        viewModelScope.launch { repository.block(number) }
    }

    /**
     * Place a call straight from a call-log row, bypassing the dialpad (9a).
     * Returns false when CALL_PHONE isn't granted so the screen can request it
     * and retry.
     */
    fun placeCall(number: String): Boolean = phoneCaller.placeCall(number)

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
