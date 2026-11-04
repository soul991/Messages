package com.messages.core.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.messages.core.MessageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class MockTelephonyProvider : ContentProvider() {
    val rows = mutableListOf<ContentValues>()
    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = values?.getAsLong(Telephony.Sms._ID) ?: nextId++
        val cv = ContentValues(values).apply { put(Telephony.Sms._ID, id) }
        rows.add(cv)
        return Uri.parse("content://sms/$id")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val minId = selectionArgs?.firstOrNull()?.toLongOrNull() ?: 0L
        val cols = projection ?: arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        val cursor = MatrixCursor(cols)
        for (row in rows) {
            val id = row.getAsLong(Telephony.Sms._ID) ?: 0L
            if (id > minId) {
                val values = cols.map { col ->
                    when (col) {
                        Telephony.Sms._ID -> id
                        Telephony.Sms.THREAD_ID -> row.getAsLong(Telephony.Sms.THREAD_ID) ?: 1L
                        Telephony.Sms.ADDRESS -> row.getAsString(Telephony.Sms.ADDRESS) ?: ""
                        Telephony.Sms.BODY -> row.getAsString(Telephony.Sms.BODY) ?: ""
                        Telephony.Sms.DATE -> row.getAsLong(Telephony.Sms.DATE) ?: 1000L
                        Telephony.Sms.TYPE -> row.getAsInteger(Telephony.Sms.TYPE) ?: Telephony.Sms.MESSAGE_TYPE_INBOX
                        Telephony.Sms.READ -> row.getAsInteger(Telephony.Sms.READ) ?: 0
                        else -> null
                    }
                }.toTypedArray()
                cursor.addRow(values)
            }
        }
        return cursor
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelephonySyncManagerTest {

    private lateinit var context: Context
    private lateinit var repo: MessageRepository
    private lateinit var telephonyProvider: MockTelephonyProvider

    @Before
    fun setUp() {
        MessageRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.READ_SMS)

        telephonyProvider = Robolectric.buildContentProvider(MockTelephonyProvider::class.java)
            .create("sms").get()
        repo = MessageRepository.get(context)

        context.getSharedPreferences(TelephonySyncManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `sync imports new messages and advances high-water mark`() = runTest {
        telephonyProvider.rows.add(
            ContentValues().apply {
                put(Telephony.Sms._ID, 10L)
                put(Telephony.Sms.THREAD_ID, 1L)
                put(Telephony.Sms.ADDRESS, "AD-AIRTEL")
                put(Telephony.Sms.BODY, "Mega Offer! Recharge with Rs 299 for unlimited calls")
                put(Telephony.Sms.DATE, 5000L)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
            }
        )

        val result = TelephonySyncManager.sync(context)
        assertEquals(1, result.smsCount)

        val prefs = context.getSharedPreferences(TelephonySyncManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(10L, prefs.getLong(TelephonySyncManager.KEY_LAST_KNOWN_SMS_ID, -1L))

        val msg = repo.db.messages().bySmsId(10L)
        assertNotNull(msg)
        assertEquals("PROMOTIONS", msg?.category)

        // Second sync: no new messages
        val secondResult = TelephonySyncManager.sync(context)
        assertEquals(0, secondResult.smsCount)
    }

    @Test
    fun `sync handles multiple delta rows with correct categorization`() = runTest {
        telephonyProvider.rows.add(
            ContentValues().apply {
                put(Telephony.Sms._ID, 100L)
                put(Telephony.Sms.THREAD_ID, 2L)
                put(Telephony.Sms.ADDRESS, "+15551234")
                put(Telephony.Sms.BODY, "Hey, how are you?")
                put(Telephony.Sms.DATE, 10000L)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 1)
            }
        )
        telephonyProvider.rows.add(
            ContentValues().apply {
                put(Telephony.Sms._ID, 101L)
                put(Telephony.Sms.THREAD_ID, 3L)
                put(Telephony.Sms.ADDRESS, "AX-FLIPKT")
                put(Telephony.Sms.BODY, "Flash Sale is live now! Get flat 50% discount today.")
                put(Telephony.Sms.DATE, 11000L)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
            }
        )

        val result = TelephonySyncManager.sync(context)
        assertEquals(2, result.smsCount)

        val m1 = repo.db.messages().bySmsId(100L)
        assertNotNull(m1)
        assertEquals("INBOX", m1?.category)

        val m2 = repo.db.messages().bySmsId(101L)
        assertNotNull(m2)
        assertEquals("PROMOTIONS", m2?.category)

        val prefs = context.getSharedPreferences(TelephonySyncManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(101L, prefs.getLong(TelephonySyncManager.KEY_LAST_KNOWN_SMS_ID, -1L))
    }
}
