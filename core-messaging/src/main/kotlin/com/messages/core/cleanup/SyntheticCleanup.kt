package com.messages.core.cleanup

import android.content.Context
import android.os.Build
import com.messages.core.db.MessagesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time maintenance cleanup for devices that ran version 2.1.0.
 *
 * Deletes rows matching the synthetic signature (threadId == 100, address == "+1 (555) 019-2834",
 * and smsId between 900001 and 900100) using a dedicated DAO query, removes the obsolete
 * reaction SharedPreferences file, and sets a SharedPreferences flag to ensure it runs once.
 */
object SyntheticCleanup {
    const val PREFS_SETTINGS = "settings"
    const val FLAG_CLEANUP = "cleanup_synthetic_v1"
    const val PREFS_REACTIONS = "message_reactions"

    suspend fun perform(context: Context, db: MessagesDatabase): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG_CLEANUP, false)) return@withContext 0

        val deleted = db.messages().deleteSyntheticRows()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(PREFS_REACTIONS)
            } else {
                context.getSharedPreferences(PREFS_REACTIONS, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        } catch (_: Exception) {
        }
        prefs.edit().putBoolean(FLAG_CLEANUP, true).apply()
        deleted
    }
}
