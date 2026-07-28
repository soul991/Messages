package com.messages.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.messages.app.schedule.SmsRadio
import com.messages.core.MessageRepository
import com.messages.core.db.Spaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Default-SMS mandatory component: ACTION_RESPOND_VIA_MESSAGE — the dialer's
 * "can't talk now" quick reply.
 *
 * R-14: this used to call SmsManager.sendTextMessage directly, so a quick reply
 * never appeared in history, ignored multipart division, requested no status
 * callbacks, and failed silently. It now runs the SAME path as the composer
 * (repository row → SmsRadio fan-out → status receivers) on a service-scoped
 * coroutine, and stops only once dispatch has been handed off.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = recipientOf(intent)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        if (recipient == null || text == null || text.length > MAX_TEXT_CHARS) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            var messageId: Long? = null
            try {
                val repo = MessageRepository.get(applicationContext)
                // Quick replies never route into the secret space: the dialer
                // cannot express it and the space may not even be unlocked.
                val entity = repo.storeOutgoing(
                    address = recipient,
                    body = text,
                    timestamp = System.currentTimeMillis(),
                    subId = null,
                    space = Spaces.NORMAL,
                )
                messageId = entity.id
                SmsRadio.send(applicationContext, repo, entity)
            } catch (t: Throwable) {
                // A row exists but nothing reached the radio: leave a FAILED
                // message the user can resend instead of a silent no-op.
                messageId?.let { id ->
                    runCatching {
                        MessageRepository.get(applicationContext).failAllSendAttempts(
                            id, com.messages.core.send.SendFailure.LOCAL_SEND_ERROR,
                        )
                    }
                }
                android.util.Log.e("HeadlessSmsSend", "quick reply failed", t)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The platform passes `sms:`, `smsto:`, `mms:` or `mmsto:` URIs. Anything
     * else (an app handing us a `file:`/`content:` URI, say) is not a recipient
     * and is rejected rather than sent to.
     */
    private fun recipientOf(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return null
        val raw = uri.schemeSpecificPart?.trim()?.removePrefix("//") ?: return null
        val recipient = android.net.Uri.decode(raw).trim()
        if (recipient.isBlank() || recipient.length > MAX_RECIPIENT_CHARS) return null
        // Reject anything carrying a group separator or newline into the
        // recipient fan-out; a quick reply always goes to exactly one address.
        if (recipient.any { it == ';' || it == ',' || it == '\n' }) return null
        return recipient
    }

    private companion object {
        val ALLOWED_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

        /** ~10 SMS parts; beyond that the dialer should open the composer. */
        const val MAX_TEXT_CHARS = 1_530

        const val MAX_RECIPIENT_CHARS = 64
    }
}
