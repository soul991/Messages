package com.messages.app.ui.secret

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory unlock state of the secret locked space. Process-scoped and
 * deliberately aggressive about re-locking:
 *  - MainActivity.onStop → [lock] (immediate — NO grace period, ever,
 *    regardless of the main app-lock's "Lock after" setting).
 *  - Navigating to any non-secret destination → [lock] (backing out of the
 *    space re-locks it instantly).
 * There is no timed grace and no persisted unlock: a cold start is always
 * locked.
 */
object SecretSession {
    var unlocked by mutableStateOf(false)
        private set

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }
}
