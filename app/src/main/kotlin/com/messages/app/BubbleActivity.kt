package com.messages.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.messages.app.ui.chat.ChatScreen
import com.messages.designsystem.MessagesTheme

/**
 * Conversation bubbles host (§8.2, Android 11+): a minimal embedded/resizable
 * activity showing exactly one chat. Launched only via BubbleMetadata — never
 * from the launcher. Bubbles are disabled entirely while app lock is on and
 * for locked conversations (see MessageNotifier), so no auth gate is needed
 * beyond the chat's own locked-conversation check.
 */
class BubbleActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId == -1L) {
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
