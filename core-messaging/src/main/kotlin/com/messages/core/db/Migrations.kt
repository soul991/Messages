package com.messages.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real Room migrations for **every** shipped schema version.
 *
 * R-25: this used to start at v4, with `fallbackToDestructiveMigrationFrom(1, 2, 3)`
 * covering the older ones on the grounds that they were pre-release dev schemas.
 * That reasoning does not survive contact with a real install: `versionCode` is
 * already 1, and the destroyed data is not recoverable from the Telephony
 * provider. Categories, user rules, sender reputation, per-conversation
 * preferences, starred/archived flags and search metadata are all local-only —
 * a backfill re-imports message *text* and nothing else.
 *
 * Versions 1-4 predate `exportSchema = true`, so there are no schema JSONs for
 * them. The migrations below were derived from the entity definitions at the
 * exact commits that introduced each version:
 *
 * | Version | Commit    | Change                                          |
 * |---------|-----------|-------------------------------------------------|
 * | 1       | `cf129ae` | M1/M2 baseline                                  |
 * | 2       | `63ff679` | MMS receive — nullable `smsId`, MMS columns     |
 * | 3       | `b6d0314` | Dual-SIM — `subId`, `preferredSubId`            |
 * | 4       | `0184d15` | Locked conversations — `locked`                 |
 *
 * Every future schema bump MUST add its migration here and to [ALL] — the
 * database builder has no destructive fallback at any version.
 */
object Migrations {

    /**
     * v2 (MMS receive): `smsId` becomes nullable and gains MMS siblings.
     *
     * SQLite cannot relax a NOT NULL constraint in place, so `messages` is
     * rebuilt. v1 used `smsId = -1` as the "not in the provider yet" sentinel;
     * v2 expresses that as NULL, so the copy maps it with NULLIF. That also
     * repairs a v1 quirk: the UNIQUE index on `smsId` meant only ONE such row
     * could exist at a time, whereas SQLite permits many NULLs.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`smsId` INTEGER, `mmsId` INTEGER, `mmsTransactionId` TEXT, " +
                    "`threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, " +
                    "`body` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`isOutgoing` INTEGER NOT NULL, `read` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, `dangerous` INTEGER NOT NULL, " +
                    "`fraudWarning` INTEGER NOT NULL, `protectedLabel` TEXT NOT NULL, " +
                    "`score` INTEGER NOT NULL, `matchedPatternIds` TEXT NOT NULL, " +
                    "`matchedComboIds` TEXT NOT NULL, `explanations` TEXT NOT NULL, " +
                    "`mediaUri` TEXT, `mediaMimeType` TEXT, " +
                    "`starred` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                    "`sendStatus` TEXT NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `messages_new` (" +
                    "`id`, `smsId`, `threadId`, `address`, `body`, `timestamp`, " +
                    "`isOutgoing`, `read`, `category`, `dangerous`, `fraudWarning`, " +
                    "`protectedLabel`, `score`, `matchedPatternIds`, `matchedComboIds`, " +
                    "`explanations`, `starred`, `archived`, `sendStatus`) " +
                    "SELECT `id`, NULLIF(`smsId`, -1), `threadId`, `address`, `body`, " +
                    "`timestamp`, `isOutgoing`, `read`, `category`, `dangerous`, " +
                    "`fraudWarning`, `protectedLabel`, `score`, `matchedPatternIds`, " +
                    "`matchedComboIds`, `explanations`, `starred`, `archived`, `sendStatus` " +
                    "FROM `messages`"
            )
            db.execSQL("DROP TABLE `messages`")
            db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_threadId` ON `messages` (`threadId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_category` ON `messages` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_smsId` ON `messages` (`smsId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_mmsId` ON `messages` (`mmsId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_mmsTransactionId` " +
                    "ON `messages` (`mmsTransactionId`)"
            )
        }
    }

    /** v3 (dual-SIM §8.1): the subscription a message used, and a per-chat default. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `subId` INTEGER")
            db.execSQL("ALTER TABLE `conversations` ADD COLUMN `preferredSubId` INTEGER")
        }
    }

    /**
     * v4 (§8.2 locked conversations): the legacy per-conversation biometric
     * lock. Superseded by the `space` column in v8, which is why
     * `migrateLegacyLockedConversations()` exists — but the flag still has to
     * arrive here first for that later migration to find anything.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `conversations` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
        }
    }

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

    /**
     * v9 — three integrity tables the review asked for:
     *
     * - `provider_rows` (R-05): the one-to-many message → Telephony-provider-row
     *   mapping. Seeded from the existing `smsId`/`mmsId` columns so messages
     *   sent before this version stay deletable; group messages sent before it
     *   can only recover their FIRST recipient row (that history was never
     *   stored) — the pre-existing behaviour, not a new regression.
     * - `sms_attempts` (R-13): per-(recipient, part) send/delivery state.
     *   Nothing to seed — sends in flight at upgrade time have no attempt rows
     *   and fall back to the legacy message-level path.
     * - `thread_aliases` (R-16): collision-free synthetic thread IDs. Nothing
     *   to seed; old hash-derived thread IDs keep working as opaque numbers.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `provider_rows` (" +
                    "`uri` TEXT NOT NULL, `messageId` INTEGER NOT NULL, " +
                    "`recipient` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`deleteFailed` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_provider_rows_messageId` " +
                    "ON `provider_rows` (`messageId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_provider_rows_deleteFailed` " +
                    "ON `provider_rows` (`deleteFailed`)"
            )
            // Seed one mapping per already-indexed provider row.
            db.execSQL(
                "INSERT OR IGNORE INTO `provider_rows` " +
                    "(`uri`, `messageId`, `recipient`, `kind`, `deleteFailed`) " +
                    "SELECT 'content://sms/' || `smsId`, `id`, `address`, 'SMS', 0 " +
                    "FROM `messages` WHERE `smsId` IS NOT NULL"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `provider_rows` " +
                    "(`uri`, `messageId`, `recipient`, `kind`, `deleteFailed`) " +
                    "SELECT 'content://mms/' || `mmsId`, `id`, `address`, 'MMS', 0 " +
                    "FROM `messages` WHERE `mmsId` IS NOT NULL"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_attempts` (" +
                    "`attemptId` TEXT NOT NULL, `messageId` INTEGER NOT NULL, " +
                    "`recipientIndex` INTEGER NOT NULL, `partIndex` INTEGER NOT NULL, " +
                    "`sentState` TEXT NOT NULL, `deliveryState` TEXT NOT NULL, " +
                    "`wantDelivery` INTEGER NOT NULL, `resultCode` INTEGER, " +
                    "PRIMARY KEY(`attemptId`))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sms_attempts_messageId` " +
                    "ON `sms_attempts` (`messageId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `thread_aliases` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recipientKey` TEXT NOT NULL, `threadId` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_aliases_recipientKey` " +
                    "ON `thread_aliases` (`recipientKey`)"
            )
        }
    }

    /**
     * v10 (V2-48) — two columns behind the outbox's automatic retry.
     *
     * `retryCount` defaults to 0 and `nextRetryAt` to NULL, which is exactly
     * right for every existing row: nothing that was sent before this version
     * has an automatic retry pending, and a failed message from before the
     * upgrade simply waits for the user like it always did.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN nextRetryAt INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10,
    )
}
