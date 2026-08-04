package com.messages.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock (§8.2 privacy & security): biometric with device-credential
 * (PIN/pattern/password) fallback via BiometricPrompt. Settings live in the
 * shared "settings" prefs alongside sensitivity/OTP-cleanup.
 */
object AppLock {

    private const val PREFS = "settings"
    private const val KEY_APP_LOCK = "app_lock"
    private const val KEY_HIDE_PREVIEWS = "hide_previews"
    private const val KEY_LOCK_AFTER = "app_lock_after_ms"

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    /** Grace period before re-locking after backgrounding; 0 = immediately. */
    fun lockAfterMs(context: Context): Long =
        prefs(context).getLong(KEY_LOCK_AFTER, LockGrace.IMMEDIATELY)

    fun setLockAfterMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LOCK_AFTER, ms).apply()
    }

    fun hidePreviews(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_PREVIEWS, false)

    fun setHidePreviews(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_PREVIEWS, hide).apply()
    }

    /** Device has some way to authenticate (biometric or PIN/pattern/password). */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Show the system unlock sheet. [onFailure] fires on cancel/error, not on
     * a single failed attempt (the prompt handles retries itself).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onFailure()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}
