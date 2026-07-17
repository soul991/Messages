package com.personal.detectivedialer.ui

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Root state: whether onboarding is complete (null while DataStore loads). */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repository: CallRepository,
) : ViewModel() {

    /** null = still loading — render nothing until this resolves to avoid a wrong start destination. */
    val onboarded: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Warm the backend + OkHttp connection each time the app comes to the
     * foreground, so an incoming call screened shortly after doesn't pay the
     * cold-start / TLS-handshake cost and miss the ring.
     */
    private val warmUpObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            viewModelScope.launch { runCatching { repository.warmUp() } }
        }
    }

    init {
        registerFcmToken()
        ProcessLifecycleOwner.get().lifecycle.addObserver(warmUpObserver)
        // Refresh the local screening-rules table (best-effort, offline defaults otherwise).
        viewModelScope.launch {
            runCatching { repository.syncScreeningRules() }
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(warmUpObserver)
        super.onCleared()
    }

    /** Fetch the current FCM token, store it, and register it with the backend. */
    private fun registerFcmToken() {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                settings.setFcmToken(token)
                repository.registerDevice(token)
            }.onFailure { Log.w("RootViewModel", "FCM token registration failed", it) }
        }
    }
}
