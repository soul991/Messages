package com.messages.app.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.messages.app.R

/** Maps a sign-in failure to a message worth showing the user (§8.3 sign-in flow). */
object DriveSignInError {

    /**
     * The technical reason, unwrapped. GMS status codes are English identifiers
     * that go straight through — they are what a support thread is searched by,
     * so translating them would make the failure harder to look up, not easier.
     * Pure and JVM-tested; [describe] wraps it in the sentence.
     */
    fun reason(e: Throwable): String = when (e) {
        is ApiException -> GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
        else -> e.message ?: e.javaClass.simpleName
    }

    fun describe(context: Context, e: Throwable): String = when (e) {
        // The one failure with a real next step for the user; a status code
        // would say nothing about what to do.
        is DriveClient.RecoverableAuthException ->
            context.getString(R.string.drive_error_needs_permission)
        else -> context.getString(R.string.drive_sign_in_failed, reason(e))
    }
}
