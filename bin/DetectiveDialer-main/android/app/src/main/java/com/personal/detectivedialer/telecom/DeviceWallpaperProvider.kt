package com.personal.detectivedialer.telecom

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the device's current system wallpaper as a fallback background for the
 * incoming-call screen (bug batch #1). Reading it via
 * [WallpaperManager.getDrawable] needs all-files access
 * ([Environment.isExternalStorageManager]) on Android 11+, so this is best-effort:
 * when access isn't granted the flow simply stays null and the UI shows its own
 * neutral fallback.
 *
 * The decoded bitmap is cached in [wallpaper] and only re-read when the system
 * broadcasts [Intent.ACTION_WALLPAPER_CHANGED] — never on every incoming call.
 */
@Singleton
class DeviceWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    /** The current device wallpaper, or null when unavailable / not yet loaded. */
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    private var receiverRegistered = false
    private var loadedOnce = false

    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            // The user changed their wallpaper — drop the cache and re-fetch.
            Log.d(TAG, "Wallpaper changed; invalidating cache")
            loadedOnce = false
            load()
        }
    }

    /**
     * True when this app can actually read the device wallpaper. Before Android 11
     * a plain install can read it; from Android 11 on it needs all-files access.
     */
    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    /**
     * Ensure the wallpaper is loaded and the change-listener is registered. Cheap
     * to call repeatedly (e.g. each time the in-call screen appears) — it only does
     * work on the first call or after an invalidation. Safe to call when access
     * isn't granted: it just leaves the flow null.
     */
    fun ensureLoaded() {
        registerReceiverOnce()
        if (!loadedOnce) load()
    }

    // ACTION_WALLPAPER_CHANGED is deprecated but remains the only broadcast a
    // third-party app receives when the wallpaper changes — there is no modern
    // replacement, so we deliberately keep using it to invalidate our cache.
    @Suppress("DEPRECATION")
    private fun registerReceiverOnce() {
        if (receiverRegistered) return
        runCatching {
            context.registerReceiver(
                wallpaperChangedReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            )
            receiverRegistered = true
        }.onFailure { Log.w(TAG, "Could not register wallpaper-changed receiver", it) }
    }

    private fun load() {
        loadedOnce = true
        if (!hasAccess()) {
            _wallpaper.value = null
            return
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val wm = WallpaperManager.getInstance(context)
                    // getDrawable() is the system (home-screen) wallpaper; it can be
                    // null on devices using a live wallpaper.
                    @Suppress("DEPRECATION")
                    wm.drawable?.toBitmap()?.asImageBitmap()
                }.onFailure { Log.w(TAG, "Failed to read device wallpaper", it) }.getOrNull()
            }
            _wallpaper.value = bitmap
        }
    }

    companion object {
        private const val TAG = "DeviceWallpaper"
    }
}
