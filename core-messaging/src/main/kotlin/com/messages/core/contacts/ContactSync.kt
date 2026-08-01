package com.messages.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.messages.core.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps cached contact names on conversation rows in sync with the Contacts
 * provider: newly saved, renamed, or deleted contacts update existing threads
 * without waiting for their next message. Two triggers, both cheap:
 * app-foreground (throttled) and a ContactsContract [ContentObserver]
 * (debounced — contact sync fires onChange in bursts).
 */
object ContactSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerRegistered = false
    private var pending: Job? = null
    @Volatile private var lastForegroundRefresh = 0L

    private const val FOREGROUND_THROTTLE_MS = 30_000L
    private const val OBSERVER_DEBOUNCE_MS = 1_500L

    /** Bumped after every refresh that changed rows — UI photo caches key off it. */
    val refreshVersion = MutableStateFlow(0)

    private fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Idempotent; safe to call before READ_CONTACTS is granted (no-ops, and
     *  will succeed on a later call once the permission arrives). */
    fun ensureObserver(context: Context) {
        if (observerRegistered || !granted(context)) return
        val appContext = context.applicationContext
        try {
            appContext.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = refresh(appContext)
                },
            )
            observerRegistered = true
        } catch (_: SecurityException) {
            // Permission raced away — retried on next ensureObserver call.
        }
    }

    /** Called from MainActivity.onStart — heals stale rows, throttled. */
    fun refreshOnForeground(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastForegroundRefresh < FOREGROUND_THROTTLE_MS) return
        lastForegroundRefresh = now
        refresh(context.applicationContext)
    }

    private fun refresh(context: Context) {
        if (!granted(context)) return
        pending?.cancel()
        pending = scope.launch {
            delay(OBSERVER_DEBOUNCE_MS)
            // V2-28: the shared name cache is now the thing lists read from, so
            // it has to go before the rows are re-resolved — otherwise a rename
            // would heal on the conversation rows and stay stale in search.
            ContactNameCache.invalidate()
            val changed = try {
                MessageRepository.get(context).refreshContactNames()
            } catch (_: Exception) {
                0
            }
            if (changed > 0) refreshVersion.value = refreshVersion.value + 1
        }
    }
}
