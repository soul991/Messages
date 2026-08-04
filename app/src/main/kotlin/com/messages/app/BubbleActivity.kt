package com.messages.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.messages.app.security.AppLock
import com.messages.app.ui.chat.ChatScreen
import com.messages.core.MessageRepository
import com.messages.core.db.Spaces
import com.messages.designsystem.MessagesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private enum class Gate { CHECKING, ALLOWED, REFUSED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val threadId = intent.getLongExtra("threadId", -1L)
        if (threadId == -1L) {
            finish()
            return
        }
        // Open-time gate, part one: app lock is a preference read, cheap enough
        // to answer before there is anything to render.
        if (AppLock.isEnabled(this)) {
            finish()
            return
        }
        val repo = MessageRepository.get(applicationContext)
        setContent {
            MessagesTheme {
                // V2-37: keep the bubble's system bar icons legible in dark mode.
                com.messages.app.ui.common.SyncSystemBars()
                // V2-33: part two used to be a runBlocking Room query on the
                // main thread, before the first frame. A bubble tap therefore
                // waited on whatever else held the database — a migration, a
                // backfill batch, a restore — with no frame drawn and no way to
                // back out, which is the shape of an ANR. The query now runs on
                // IO behind a loading gate, and a refusal finishes as before.
                var gate by remember { mutableStateOf(Gate.CHECKING) }
                LaunchedEffect(threadId) {
                    val allowed = withContext(Dispatchers.IO) {
                        try {
                            // Normal space only: a LOCKED-space thread has no
                            // normal-space row, so byThreadId returns null and
                            // the bubble is refused.
                            repo.db.conversations().byThreadId(threadId, Spaces.NORMAL)
                                ?.let { !it.locked } ?: false
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // A database that cannot answer is not a permission
                            // to render: refuse.
                            false
                        }
                    }
                    gate = if (allowed) Gate.ALLOWED else Gate.REFUSED
                    if (!allowed) finish()
                }
                when (gate) {
                    Gate.ALLOWED -> ChatScreen(
                        threadId = threadId,
                        onBack = { finish() },
                        // "Why?" needs the full nav graph — open the app on this thread.
                        onWhy = {
                            startActivity(
                                android.content.Intent(this, MainActivity::class.java).apply {
                                    putExtra("threadId", threadId)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                },
                            )
                        },
                    )
                    // A refused bubble is finishing; keep the same neutral
                    // surface rather than flashing an empty chat at the user.
                    Gate.CHECKING, Gate.REFUSED -> BubbleLoading()
                }
            }
        }
    }
}

@Composable
private fun BubbleLoading() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
