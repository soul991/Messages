package com.messages.core.trash

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.MessagesDatabase
import com.messages.core.db.Migrations
import com.messages.core.db.Spaces
import com.messages.core.db.TrashReason
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrashReasonAndRestoreTest {

    private lateinit var context: Context
    private lateinit var db: MessagesDatabase
    private lateinit var repo: MessageRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MessageRepository.resetForTests()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MessageRepository.get(context)
    }

    @After
    fun tearDown() {
        db.close()
        MessageRepository.resetForTests()
    }

    private fun msg(
        id: Long = 0,
        threadId: Long = 100L,
        body: String = "Test message",
        trashed: Boolean = false,
        trashReason: String? = null,
    ) = MessageEntity(
        id = id,
        threadId = threadId,
        address = "+1234567890",
        body = body,
        normalizedBody = body,
        timestamp = System.currentTimeMillis(),
        isOutgoing = false,
        trashed = trashed,
        trashedAt = if (trashed) System.currentTimeMillis() else null,
        trashReason = trashReason,
    )

    @Test
    fun `moveToTrash records USER reason by default`() = runTest {
        val id = db.messages().insert(msg(body = "User deleted message"))
        val now = System.currentTimeMillis()
        db.messages().moveToTrash(id, now, TrashReason.USER)

        val trashed = db.messages().byId(id)!!
        assertTrue(trashed.trashed)
        assertEquals(now, trashed.trashedAt)
        assertEquals(TrashReason.USER, trashed.trashReason)
    }

    @Test
    fun `moveThreadToTrash sets reason on all thread messages`() = runTest {
        val id1 = db.messages().insert(msg(threadId = 200L, body = "Msg 1"))
        val id2 = db.messages().insert(msg(threadId = 200L, body = "Msg 2"))
        val now = System.currentTimeMillis()

        db.messages().moveThreadToTrash(200L, now, Spaces.NORMAL, TrashReason.AUTO_SWIPE)

        val m1 = db.messages().byId(id1)!!
        val m2 = db.messages().byId(id2)!!
        assertTrue(m1.trashed)
        assertTrue(m2.trashed)
        assertEquals(TrashReason.AUTO_SWIPE, m1.trashReason)
        assertEquals(TrashReason.AUTO_SWIPE, m2.trashReason)
    }

    @Test
    fun `restoreFromTrash clears trashed flag, timestamp, and trashReason`() = runTest {
        val id = db.messages().insert(
            msg(body = "Auto-trashed msg", trashed = true, trashReason = TrashReason.AUTO_SWIPE)
        )
        db.messages().restoreFromTrash(id)

        val restored = db.messages().byId(id)!!
        assertFalse(restored.trashed)
        assertNull(restored.trashedAt)
        assertNull(restored.trashReason)
    }

    @Test
    fun `autoTrashedMessages filters only auto-trashed and excludes user deletions`() = runTest {
        db.messages().insert(msg(body = "User 1", trashed = true, trashReason = TrashReason.USER))
        db.messages().insert(msg(body = "Auto 1", trashed = true, trashReason = TrashReason.AUTO_SWIPE))
        db.messages().insert(msg(body = "Auto 2", trashed = true, trashReason = TrashReason.AUTO_SPAM))
        db.messages().insert(msg(body = "Live msg", trashed = false, trashReason = null))

        val auto = db.messages().autoTrashedMessages()
        assertEquals(2, auto.size)
        assertTrue(auto.any { it.body == "Auto 1" && it.trashReason == TrashReason.AUTO_SWIPE })
        assertTrue(auto.any { it.body == "Auto 2" && it.trashReason == TrashReason.AUTO_SPAM })
        assertFalse(auto.any { it.trashReason == TrashReason.USER })
    }

    @Test
    fun `restoreAllAutoTrashed restores only auto-trashed messages`() = runTest {
        val userId = repo.db.messages().insert(msg(body = "Explicit user delete", trashed = true, trashReason = TrashReason.USER))
        val auto1 = repo.db.messages().insert(msg(threadId = 300L, body = "Accidental swipe 1", trashed = true, trashReason = TrashReason.AUTO_SWIPE))
        val auto2 = repo.db.messages().insert(msg(threadId = 300L, body = "Accidental swipe 2", trashed = true, trashReason = TrashReason.AUTO_SWIPE))

        val count = repo.restoreAllAutoTrashed()
        assertEquals(2, count)

        // Auto-trashed messages are restored
        val restored1 = repo.db.messages().byId(auto1)!!
        val restored2 = repo.db.messages().byId(auto2)!!
        assertFalse(restored1.trashed)
        assertNull(restored1.trashReason)
        assertFalse(restored2.trashed)
        assertNull(restored2.trashReason)

        // User deletion remains untouched in Trash
        val userMsg = repo.db.messages().byId(userId)!!
        assertTrue(userMsg.trashed)
        assertEquals(TrashReason.USER, userMsg.trashReason)
    }

    @Test
    fun `restoreAllUnknownTrashed restores only legacy unknown trashed messages`() = runTest {
        val userId = repo.db.messages().insert(msg(body = "User delete", trashed = true, trashReason = TrashReason.USER))
        val autoSwipe = repo.db.messages().insert(msg(threadId = 300L, body = "Swipe delete", trashed = true, trashReason = TrashReason.AUTO_SWIPE))
        val unknown1 = repo.db.messages().insert(msg(threadId = 300L, body = "Legacy 1", trashed = true, trashReason = TrashReason.UNKNOWN))
        val unknown2 = repo.db.messages().insert(msg(threadId = 300L, body = "Legacy 2", trashed = true, trashReason = TrashReason.UNKNOWN))

        val count = repo.restoreAllUnknownTrashed()
        assertEquals(2, count)

        // Legacy unknown messages are restored
        val restored1 = repo.db.messages().byId(unknown1)!!
        val restored2 = repo.db.messages().byId(unknown2)!!
        assertFalse(restored1.trashed)
        assertNull(restored1.trashReason)
        assertFalse(restored2.trashed)
        assertNull(restored2.trashReason)

        // User and auto-swipe deletions remain untouched
        val userMsg = repo.db.messages().byId(userId)!!
        assertTrue(userMsg.trashed)
        assertEquals(TrashReason.USER, userMsg.trashReason)

        val swipeMsg = repo.db.messages().byId(autoSwipe)!!
        assertTrue(swipeMsg.trashed)
        assertEquals(TrashReason.AUTO_SWIPE, swipeMsg.trashReason)
    }

    @Test
    fun `relabelAutoSwipeToUnknown updates AUTO_SWIPE rows to UNKNOWN`() = runTest {
        val id = db.messages().insert(msg(body = "Falsely auto-swiped", trashed = true, trashReason = "AUTO_SWIPE"))
        db.messages().relabelAutoSwipeToUnknown()
        val updated = db.messages().byId(id)!!
        assertEquals(TrashReason.UNKNOWN, updated.trashReason)
    }

    @Test
    fun `MIGRATION_11_12 migrates existing trashed rows to UNKNOWN`() {
        val dbName = "migration-11-12-test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(sdb: SupportSQLiteDatabase) {
                        sdb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `messages` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsId` INTEGER, " +
                                "`mmsId` INTEGER, `mmsTransactionId` TEXT, `threadId` INTEGER NOT NULL, " +
                                "`address` TEXT NOT NULL, `body` TEXT NOT NULL, `normalizedBody` TEXT NOT NULL, " +
                                "`timestamp` INTEGER NOT NULL, `isOutgoing` INTEGER NOT NULL, `read` INTEGER NOT NULL, " +
                                "`category` TEXT NOT NULL, `dangerous` INTEGER NOT NULL, `fraudWarning` INTEGER NOT NULL, " +
                                "`protectedLabel` TEXT NOT NULL, `score` INTEGER NOT NULL, `matchedPatternIds` TEXT NOT NULL, " +
                                "`matchedComboIds` TEXT NOT NULL, `explanations` TEXT NOT NULL, `mediaUri` TEXT, " +
                                "`mediaMimeType` TEXT, `starred` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                                "`sendStatus` TEXT NOT NULL, `sendResultCode` INTEGER, `subId` INTEGER, " +
                                "`retryCount` INTEGER NOT NULL, `nextRetryAt` INTEGER, `trashed` INTEGER NOT NULL, " +
                                "`trashedAt` INTEGER, `space` TEXT NOT NULL)"
                        )
                    }

                    override fun onUpgrade(sdb: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )

        val sqliteDb = helper.writableDatabase
        sqliteDb.execSQL(
            "INSERT INTO `messages` (`threadId`, `address`, `body`, `normalizedBody`, `timestamp`, " +
                "`isOutgoing`, `read`, `category`, `dangerous`, `fraudWarning`, `protectedLabel`, `score`, " +
                "`matchedPatternIds`, `matchedComboIds`, `explanations`, `starred`, `archived`, `sendStatus`, " +
                "`retryCount`, `trashed`, `trashedAt`, `space`) " +
                "VALUES (10, '+111', 'Old trashed bank message', 'Old trashed bank message', 1000, " +
                "0, 1, 'INBOX', 0, 0, 'BANK', 0, '', '', '', 0, 0, 'NONE', 0, 1, 1000, 'NORMAL')"
        )
        sqliteDb.execSQL(
            "INSERT INTO `messages` (`threadId`, `address`, `body`, `normalizedBody`, `timestamp`, " +
                "`isOutgoing`, `read`, `category`, `dangerous`, `fraudWarning`, `protectedLabel`, `score`, " +
                "`matchedPatternIds`, `matchedComboIds`, `explanations`, `starred`, `archived`, `sendStatus`, " +
                "`retryCount`, `trashed`, `trashedAt`, `space`) " +
                "VALUES (10, '+111', 'Old live message', 'Old live message', 1000, " +
                "0, 1, 'INBOX', 0, 0, 'BANK', 0, '', '', '', 0, 0, 'NONE', 0, 0, NULL, 'NORMAL')"
        )

        // Apply migration 11 -> 12
        Migrations.MIGRATION_11_12.migrate(sqliteDb)

        sqliteDb.query("SELECT `body`, `trashReason`, `trashed` FROM `messages` ORDER BY `id`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Old trashed bank message", c.getString(0))
            assertEquals(TrashReason.UNKNOWN, c.getString(1))
            assertEquals(1, c.getInt(2))

            assertTrue(c.moveToNext())
            assertEquals("Old live message", c.getString(0))
            assertNull(c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        helper.close()
    }
}
