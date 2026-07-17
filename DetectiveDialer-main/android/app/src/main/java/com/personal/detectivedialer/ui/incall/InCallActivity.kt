package com.personal.detectivedialer.ui.incall

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.detectivedialer.ui.theme.DetectiveDialerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen in-call UI. Shown over the lock screen (and turns the screen on)
 * so an incoming call is answerable without unlocking — the behaviour expected
 * of the default phone app. Finishes itself when there are no calls left.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val vm: InCallViewModel = hiltViewModel()
            val calls by vm.calls.collectAsState()
            val audio by vm.audio.collectAsState()
            val appearance by vm.appearance.collectAsState()
            val deviceWallpaper by vm.deviceWallpaper.collectAsState()

            // No calls left → the call ended; close the screen (side effect, not
            // during composition, and only after we've actually seen calls go away).
            LaunchedEffect(calls.isEmpty()) {
                if (calls.isEmpty()) finishAndRemoveTask()
            }

            DetectiveDialerTheme(darkTheme = true) {
                if (calls.isNotEmpty()) {
                    InCallScreen(
                        calls = calls,
                        audio = audio,
                        appearance = appearance,
                        deviceWallpaper = deviceWallpaper,
                        vm = vm,
                    )
                }
            }
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        // Keep the display awake for the whole call regardless of API level.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // Dismiss the (insecure) keyguard so the call UI is immediately usable;
            // on a secure lock this is a no-op until the user authenticates.
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
    }
}
