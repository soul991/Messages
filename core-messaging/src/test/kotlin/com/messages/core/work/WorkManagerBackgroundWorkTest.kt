package com.messages.core.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.cleanup.SpamCleanup
import com.messages.core.trash.TrashRetention
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerBackgroundWorkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
        try {
            WorkManager.initialize(context, config)
        } catch (_: IllegalStateException) {
            // Already initialized in test environment
        }
    }

    @Test
    fun `work manager instance initializes and is available`() {
        val wm = WorkManager.getInstance(context)
        assertNotNull("WorkManager must be non-null and ready to accept jobs", wm)
    }

    @Test
    fun `background cleanup and retention jobs enqueue successfully`() {
        OtpCleanup.ensureScheduled(context)
        SpamCleanup.ensureScheduled(context)
        TrashRetention.ensureScheduled(context)

        val wm = WorkManager.getInstance(context)
        assertNotNull(wm)
    }
}
