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

    /**
     * v6 (§8.5 search): normalizedBody column + external-content FTS4 index.
     * Existing rows keep the '' default (still searchable via the body
     * column); the one-time [com.messages.core.search.FtsRenormalizeWorker]
     * pass fills them with the engine's Stage-0 normalization
     * (leet/homoglyph/separator undo).
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN normalizedBody TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(" +
                    "`body` TEXT NOT NULL, `normalizedBody` TEXT NOT NULL, " +
                    "`address` TEXT NOT NULL, content=`messages`)"
            )
            db.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
        }
    }

    /** v7 (failed-send reasons): raw SmsManager result code on failed messages. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sendResultCode INTEGER")
        }
    }

    /**
     * v8 (secret locked space): `space` column (NORMAL/LOCKED) on messages and
     * conversations. The conversations unique index widens from threadId to
     * (threadId, space) — "New locked chat" keeps a second conversation row
     * for the same system thread. Existing rows all default to NORMAL;
     * legacy biometric-locked conversations migrate to the LOCKED space later,
     * when the user first completes secret-space setup (not here — the space
     * has no credential yet at schema-migration time).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN space TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_space ON messages (space)")
            db.execSQL("ALTER TABLE conversations ADD COLUMN space TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("DROP INDEX IF EXISTS index_conversations_threadId")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_conversations_threadId_space " +
                    "ON conversations (threadId, space)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
