package com.messages.app.drive

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException

/** Maps a sign-in failure to a message worth showing the user (§8.3 sign-in flow). */
object DriveSignInError {

    fun describe(e: Throwable): String = when (e) {
        is ApiException -> "Sign-in failed (${GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)})"
        else -> "Sign-in failed: ${e.message ?: e.javaClass.simpleName}"
    }
}
