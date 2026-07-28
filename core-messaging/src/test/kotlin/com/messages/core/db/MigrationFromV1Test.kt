package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-25 — every shipped schema migrates to current WITHOUT data loss.
 *
 * Versions 1-4 predate `exportSchema = true`, so there are no schema JSONs and
 * `MigrationTestHelper` cannot drive them. Instead each old schema is created
 * here from explicit DDL — transcribed from the entity definitions at the
 * commits named in [Migrations] — seeded with rows, then opened through the
 * real Room builder.
 *
 * Opening is the assertion that matters: Room validates the live schema against
 * `schemas/…/9.json` on open and throws if a migration left anything off by a
 * column, an index or a nullability flag. The data checks then prove the
 * migration *carried* state rather than merely arriving at the right shape —
 * which is exactly what `fallbackToDestructiveMigrationFrom(1, 2, 3)` failed to
 * do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationFromV1Test {

    private val dbName = "migration-test.db"
    private lateinit var context: Context
    private var db: MessagesDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    // ---- old schemas, transcribed from history --------------------------

    private val v1Messages = """
        CREATE TABLE IF NOT EXISTS `messages` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsId` INTEGER NOT NULL,
        `threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, `body` TEXT NOT NULL,
        `timestamp` INTEGER NOT NULL, `isOutgoing` INTEGER NOT NULL, `read` INTEGER NOT NULL,
        `category` TEXT NOT NULL, `dangerous` INTEGER NOT NULL, `fraudWarning` INTEGER NOT NULL,
        `protectedLabel` TEXT NOT NULL, `score` INTEGER NOT NULL,
        `matchedPatternIds` TEXT NOT NULL, `matchedComboIds` TEXT NOT NULL,
        `explanations` TEXT NOT NULL, `starred` INTEGER NOT NULL, `archived` INTEGER NOT NULL,
        `sendStatus` TEXT NOT NULL)
    """.trimIndent()

    /** v2 relaxed `smsId` and added the MMS/media columns. */
    private val v2Messages = """
        CREATE TABLE IF NOT EXISTS `messages` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsId` INTEGER, `mmsId` INTEGER,
        `mmsTransactionId` TEXT, `threadId` INTEGER NOT NULL, `address` TEXT NOT NULL,
        `body` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isOutgoing` INTEGER NOT NULL,
        `read` INTEGER NOT NULL, `category` TEXT NOT NULL, `dangerous` INTEGER NOT NULL,
        `fraudWarning` INTEGER NOT NULL, `protectedLabel` TEXT NOT NULL, `score` INTEGER NOT NULL,
        `matchedPatternIds` TEXT NOT NULL, `matchedComboIds` TEXT NOT NULL,
        `explanations` TEXT NOT NULL, `mediaUri` TEXT, `mediaMimeType` TEXT,
        `starred` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sendStatus` TEXT NOT NULL)
    """.trimIndent()

    private fun conversations(extraColumns: String) = """
        CREATE TABLE IF NOT EXISTS `conversations` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `threadId` INTEGER NOT NULL,
        `address` TEXT NOT NULL, `contactName` TEXT, `lastMessage` TEXT NOT NULL,
        `lastTimestamp` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL,
        `category` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL,
        `muted` INTEGER NOT NULL$extraColumns)
    """.trimIndent()

    private val sharedTables = listOf(
        """
        CREATE TABLE IF NOT EXISTS `sender_reputation` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `address` TEXT NOT NULL,
        `score` INTEGER NOT NULL, `userMarkedSpamCount` INTEGER NOT NULL,
        `userMarkedNotSpamCount` INTEGER NOT NULL)
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sender_reputation_address` " +
            "ON `sender_reputation` (`address`)",
        """
        CREATE TABLE IF NOT EXISTS `user_rules` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `position` INTEGER NOT NULL,
        `kind` TEXT NOT NULL, `target` TEXT NOT NULL, `pattern` TEXT NOT NULL,
        `category` TEXT NOT NULL)
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_threadId` " +
            "ON `conversations` (`threadId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_threadId` ON `messages` (`threadId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_category` ON `messages` (`category`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_smsId` ON `messages` (`smsId`)",
    )

    private val v2PlusIndices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_mmsId` ON `messages` (`mmsId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_mmsTransactionId` " +
            "ON `messages` (`mmsTransactionId`)",
    )

    /** DDL for [version], as Room would have created it at that point in history. */
    private fun ddlFor(version: Int): List<String> = buildList {
        add(if (version == 1) v1Messages else v2Messages)
        add(
            when (version) {
                1, 2 -> conversations("")
                3 -> conversations(", `preferredSubId` INTEGER")
                else -> conversations(", `locked` INTEGER NOT NULL DEFAULT 0, `preferredSubId` INTEGER")
            }
        )
        addAll(sharedTables)
        if (version >= 2) addAll(v2PlusIndices)
        if (version >= 3) add("ALTER TABLE `messages` ADD COLUMN `subId` INTEGER")
    }

    // ---- helpers ---------------------------------------------------------

    /** Creates [dbName] at [version] with its historical schema, then seeds it. */
    private fun createOldDatabase(version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        ddlFor(version).forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { seed(it) }
        helper.close()
    }

    /** Opens through the real builder, running the migration chain. */
    private fun openMigrated(): MessagesDatabase {
        val opened = Room.databaseBuilder(context, MessagesDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        // Touching the helper forces open, which runs migrations and validates
        // the final schema against 9.json.
        opened.openHelper.writableDatabase
        db = opened
        return opened
    }

    private fun seedMessage(
        db: SupportSQLiteDatabase,
        version: Int,
        smsId: Long,
        body: String,
        category: String = "SPAM",
    ) {
        val columns = "`smsId`, `threadId`, `address`, `body`, `timestamp`, `isOutgoing`, " +
            "`read`, `category`, `dangerous`, `fraudWarning`, `protectedLabel`, `score`, " +
            "`matchedPatternIds`, `matchedComboIds`, `explanations`, `starred`, `archived`, " +
            "`sendStatus`"
        db.execSQL(
            "INSERT INTO `messages` ($columns) VALUES " +
                "($smsId, 42, '+919876543210', '$body', 1700000000000, 0, 0, '$category', " +
                "0, 0, 'NONE', 7, 'promo-1', '', 'looked promotional', 1, 0, 'NONE')"
        )
        if (version >= 1) {
            db.execSQL(
                "INSERT INTO `conversations` (`threadId`, `address`, `contactName`, " +
                    "`lastMessage`, `lastTimestamp`, `unreadCount`, `category`, `pinned`, " +
                    "`archived`, `muted`) VALUES " +
                    "(42, '+919876543210', 'Bank', '$body', 1700000000000, 3, '$category', 1, 0, 1)"
            )
        }
        db.execSQL(
            "INSERT INTO `user_rules` (`position`, `kind`, `target`, `pattern`, `category`) " +
                "VALUES (1, 'BLOCK', 'SENDER', '+911112223334', 'BLOCKED')"
        )
        db.execSQL(
            "INSERT INTO `sender_reputation` (`address`, `score`, `userMarkedSpamCount`, " +
                "`userMarkedNotSpamCount`) VALUES ('+919876543210', -5, 2, 0)"
        )
    }

    /** Every local-only value that a destructive fallback would have erased. */
    private fun assertUserStateSurvived(db: MessagesDatabase, expectedBody: String) {
        db.openHelper.readableDatabase.query(
            "SELECT `body`, `category`, `score`, `starred`, `matchedPatternIds`, `explanations` " +
                "FROM `messages`"
        ).use { c ->
            assertTrue("message row was lost", c.moveToFirst())
            assertEquals(expectedBody, c.getString(0))
            assertEquals("SPAM", c.getString(1))
            assertEquals(7, c.getInt(2))
            assertEquals(1, c.getInt(3))
            assertEquals("promo-1", c.getString(4))
            assertEquals("looked promotional", c.getString(5))
        }
        db.openHelper.readableDatabase.query(
            "SELECT `contactName`, `unreadCount`, `pinned`, `muted`, `space` FROM `conversations`"
        ).use { c ->
            assertTrue("conversation row was lost", c.moveToFirst())
            assertEquals("Bank", c.getString(0))
            assertEquals(3, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals(1, c.getInt(3))
            // v8 default: pre-existing rows land in the normal space.
            assertEquals(Spaces.NORMAL, c.getString(4))
        }
        db.openHelper.readableDatabase.query(
            "SELECT `kind`, `pattern` FROM `user_rules`"
        ).use { c ->
            assertTrue("user rule was lost", c.moveToFirst())
            assertEquals("BLOCK", c.getString(0))
            assertEquals("+911112223334", c.getString(1))
        }
        db.openHelper.readableDatabase.query(
            "SELECT `score`, `userMarkedSpamCount` FROM `sender_reputation`"
        ).use { c ->
            assertTrue("reputation row was lost", c.moveToFirst())
            assertEquals(-5, c.getInt(0))
            assertEquals(2, c.getInt(1))
        }
    }

    // ---- the migrations --------------------------------------------------

    @Test
    fun `v1 migrates to current with every local-only value intact`() {
        createOldDatabase(1) { seedMessage(it, 1, 1001, "you have won a prize") }
        assertUserStateSurvived(openMigrated(), "you have won a prize")
    }

    @Test
    fun `v2 migrates to current with every local-only value intact`() {
        createOldDatabase(2) { seedMessage(it, 2, 2001, "kyc update required") }
        assertUserStateSurvived(openMigrated(), "kyc update required")
    }

    @Test
    fun `v3 migrates to current with every local-only value intact`() {
        createOldDatabase(3) { seedMessage(it, 3, 3001, "claim your refund") }
        assertUserStateSurvived(openMigrated(), "claim your refund")
    }

    @Test
    fun `v4 migrates to current with every local-only value intact`() {
        createOldDatabase(4) { seedMessage(it, 4, 4001, "account suspended") }
        assertUserStateSurvived(openMigrated(), "account suspended")
    }

    /**
     * v1 used `smsId = -1` for "not yet in the provider". v2 expresses that as
     * NULL, and the UNIQUE index means the sentinel could only ever apply to
     * one row — so the conversion has to happen, not just be tolerated.
     */
    @Test
    fun `v1 draft sentinel becomes null rather than a bogus provider id`() {
        createOldDatabase(1) { db ->
            seedMessage(db, 1, -1, "unsent draft")
        }
        val migrated = openMigrated()
        migrated.openHelper.readableDatabase.query(
            "SELECT `smsId` FROM `messages` WHERE `body` = 'unsent draft'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("smsId -1 should have become NULL", c.isNull(0))
        }
    }

    /**
     * The v9 integrity tables (R-05) are seeded from existing provider ids, so
     * a message that came all the way from v1 stays deletable.
     */
    @Test
    fun `v1 message gains a provider_rows mapping by v9`() {
        createOldDatabase(1) { seedMessage(it, 1, 5005, "seeded from v1") }
        val migrated = openMigrated()
        migrated.openHelper.readableDatabase.query(
            "SELECT `uri`, `kind` FROM `provider_rows`"
        ).use { c ->
            assertTrue("no provider_rows mapping was seeded", c.moveToFirst())
            assertEquals("content://sms/5005", c.getString(0))
            assertEquals("SMS", c.getString(1))
        }
    }

    /** A -1 sentinel is not a real provider row and must not be mapped. */
    @Test
    fun `draft sentinel does not produce a provider_rows mapping`() {
        createOldDatabase(1) { seedMessage(it, 1, -1, "unsent draft") }
        val migrated = openMigrated()
        migrated.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM `provider_rows`"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    /** The migration chain must be complete — no gaps between 1 and current. */
    @Test
    fun `migrations cover every version with no gaps`() {
        val edges = Migrations.ALL.map { it.startVersion to it.endVersion }.sortedBy { it.first }
        assertEquals(1, edges.first().first)
        edges.zipWithNext { a, b ->
            assertEquals("gap between schema v${a.second} and v${b.first}", a.second, b.first)
        }
        assertNull(
            "a migration skips a version",
            edges.firstOrNull { it.second != it.first + 1 },
        )
    }
}
