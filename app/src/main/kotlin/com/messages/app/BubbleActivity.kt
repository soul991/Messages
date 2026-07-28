package com.messages.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.messages.app.security.AppLock
import com.messages.app.ui.chat.ChatScreen
import com.messages.core.MessageRepository
import com.messages.core.db.Spaces
import com.messages.designsystem.MessagesTheme
import kotlinx.coroutines.runBlocking

/**
 * Conversation bubbles host (§8.2, Android 11+): a minimal embedded/resizable
 * activity showing exactly one chat. Launched only via BubbleMetadata — never
 * from the launcher.
 *
 * R-02: MessageNotifier suppresses bubble *creation* while app lock is on, but a
 * bubble published before the user enabled the lock keeps a live PendingIntent
 * that survives the setting change. Privacy is therefore re-evaluated here at
 * OPEN time, on every onCreate, and the thread is verified to exist in the
 * normal space and not be locked. A stale bubble finishes instead of rendering.
 */
class BubbleActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId == -1L) {
            finish()
            return
        }
        // Open-time gate. Any of these means this bubble must not render.
        if (AppLock.isEnabled(this)) {
            finish()
            return
        }
        val repo = MessageRepository.get(applicationContext)
        val allowed = runBlocking {
            // Normal space only: a LOCKED-space thread has no normal-space row,
            // so byThreadId returns null and the bubble is refused.
            repo.db.conversations().byThreadId(threadId, Spaces.NORMAL)?.let { !it.locked } ?: false
        }
        if (!allowed) {
            finish()
            return
        }
        setContent {
            MessagesTheme {
                ChatScreen(
                    threadId = threadId,
                    onBack = { finish() },
                    // "Why?" needs the full nav graph — open the app on this thread.
                    onWhy = {
                        startActivity(
                            android.content.Intent(this, MainActivity::class.java).apply {
                                putExtra("threadId", threadId)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                )
            }
        }
    }
}
