package com.messages.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real Room migrations, mandatory from schema v4 forward (v4 shipped with the
 * M4 extras; anything older is a dev-only schema and may still be rebuilt
 * destructively — the Telephony provider holds the real message content, and
 * the backfill re-indexes it).
 *
 * Every future schema bump MUST add its migration here and to [ALL] — the
 * database builder no longer falls back to destruction for v4+.
 */
object Migrations {

    /** v5 (§6.4 Trash): trashed flag + trash timestamp on messages. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN trashed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN trashedAt INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_trashed ON messages (trashed)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5)
}
