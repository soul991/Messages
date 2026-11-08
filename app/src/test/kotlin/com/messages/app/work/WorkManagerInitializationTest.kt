package com.messages.app.work

import com.messages.app.MessagesApp
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkManagerInitializationTest {

    @Test
    fun `messages app implements Configuration Provider for on-demand initialization`() {
        val app = MessagesApp()
        assertTrue(
            "MessagesApp must implement Configuration.Provider so WorkManager initializes on-demand",
            app is androidx.work.Configuration.Provider
        )
    }
}
