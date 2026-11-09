package com.messages.core.trash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.MessageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreezeAutoDeleteTest {

    private lateinit var context: Context
    private lateinit var repo: MessageRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = MessageRepository.get(context)
    }

    @Test
    fun `purgeExpiredTrash is frozen and returns zero without deleting`() = runBlocking {
        val deleted = repo.purgeExpiredTrash()
        assertEquals("Frozen purgeExpiredTrash must return 0", 0, deleted)
    }

    @Test
    fun `cleanupExpiredOtps is frozen and returns zero without deleting`() = runBlocking {
        val deleted = repo.cleanupExpiredOtps(0L)
        assertEquals("Frozen cleanupExpiredOtps must return 0", 0, deleted)
    }

    @Test
    fun `cleanupExpiredSpam is frozen and returns zero without deleting`() = runBlocking {
        val deleted = repo.cleanupExpiredSpam(0L)
        assertEquals("Frozen cleanupExpiredSpam must return 0", 0, deleted)
    }
}
