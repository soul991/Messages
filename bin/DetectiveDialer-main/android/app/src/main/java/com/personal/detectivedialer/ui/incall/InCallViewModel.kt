package com.personal.detectivedialer.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.repository.SmsRepository
import com.personal.detectivedialer.telecom.AudioUi
import com.personal.detectivedialer.telecom.CallManager
import com.personal.detectivedialer.telecom.CallUi
import com.personal.detectivedialer.telecom.DeviceWallpaperProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wallpaper + scrim applied behind the incoming/in-call surface. */
data class CallAppearance(val wallpaperPath: String = "", val scrimPercent: Int = 45)

/** Exposes the shared CallManager state (and call appearance) to the in-call UI. */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callManager: CallManager,
    private val smsRepository: SmsRepository,
    private val deviceWallpaperProvider: DeviceWallpaperProvider,
    settings: SettingsRepository,
) : ViewModel() {

    val calls: StateFlow<List<CallUi>> = callManager.calls
    val audio: StateFlow<AudioUi> = callManager.audio

    val appearance: StateFlow<CallAppearance> = settings.settings
        .map { CallAppearance(it.incomingWallpaperPath, it.incomingScrimPercent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallAppearance())

    /**
     * The device's current wallpaper, used as the incoming-call background when the
     * user hasn't set a custom one and all-files access is granted (bug batch #1).
     * Cached in the provider; null when unavailable → the UI shows a neutral scrim.
     */
    val deviceWallpaper: StateFlow<ImageBitmap?> = deviceWallpaperProvider.wallpaper

    init {
        // Warm the cache (and register the wallpaper-changed listener) once. Cheap
        // and idempotent — it won't re-read on every call.
        deviceWallpaperProvider.ensureLoaded()
    }

    fun answer(id: String) = callManager.answer(id)
    fun reject(id: String) = callManager.reject(id)
    fun hangup(id: String) = callManager.hangup(id)
    fun toggleHold(call: CallUi) = if (call.isOnHold) callManager.unhold(call.id) else callManager.hold(call.id)
    fun playDtmf(id: String, digit: Char) = callManager.playDtmf(id, digit)
    fun setMuted(muted: Boolean) = callManager.setMuted(muted)
    fun setSpeaker(on: Boolean) = callManager.setSpeaker(on)
    fun cycleAudioRoute() = callManager.cycleAudioRoute()

    /** Deflect an incoming call to voicemail (reject; carrier routes to VM). */
    fun sendToVoicemail(id: String) = callManager.sendToVoicemail(id)

    /** Send a quick text to the caller, then decline the call. */
    fun replyWithMessage(call: CallUi, text: String) {
        viewModelScope.launch {
            if (call.number.isNotBlank() && text.isNotBlank()) {
                runCatching { smsRepository.send(call.number, text) }
            }
            callManager.rejectWithMessage(call.id)
        }
    }
}
