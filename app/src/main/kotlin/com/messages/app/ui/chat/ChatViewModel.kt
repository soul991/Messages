package com.messages.app.ui.chat

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.telephony.SmsManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.receiver.SmsSentReceiver
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    app: Application,
    private val threadId: Long,
    /** Recipient for a brand-new thread with no conversation row yet (compose flow). */
    private val fallbackAddress: String? = null,
) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val messages: StateFlow<List<MessageEntity>> =
        repo.db.messages().messagesForThread(threadId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactName = MutableStateFlow<String?>(null)
    val address = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val conv = repo.db.conversations().byThreadId(threadId)
            if (conv != null) {
                address.value = conv.address
                contactName.value = conv.contactName
            } else if (!fallbackAddress.isNullOrBlank()) {
                address.value = fallbackAddress
                contactName.value = withContext(Dispatchers.IO) {
                    repo.lookupContactName(fallbackAddress)
                }
            }
            repo.db.messages().markThreadRead(threadId)
            repo.db.conversations().clearUnread(threadId)
        }
    }

    fun send(text: String) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val entity = repo.storeOutgoing(to, text, System.currentTimeMillis())
            try {
                val ctx = getApplication<Application>()
                val sms = ctx.getSystemService(SmsManager::class.java)
                val parts = sms.divideMessage(text)
                val sentIntent = PendingIntent.getBroadcast(
                    ctx, entity.id.toInt(),
                    Intent(ctx, SmsSentReceiver::class.java).putExtra("messageId", entity.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                if (parts.size == 1) {
                    sms.sendTextMessage(to, null, text, sentIntent, null)
                } else {
                    sms.sendMultipartTextMessage(
                        to, null, parts,
                        ArrayList(parts.map { sentIntent }), null,
                    )
                }
            } catch (_: Exception) {
                repo.db.messages().update(entity.copy(sendStatus = "FAILED"))
            }
        }
    }

    fun resend(message: MessageEntity) = send(message.body)

    fun moveToInbox(messageId: Long) = viewModelScope.launch { repo.moveToInbox(messageId) }
    fun moveToSpam(messageId: Long) = viewModelScope.launch { repo.moveToSpam(messageId) }
    fun star(messageId: Long, starred: Boolean) = viewModelScope.launch {
        repo.db.messages().setStarred(messageId, starred)
    }
    fun delete(messageId: Long) = viewModelScope.launch { repo.db.messages().userDelete(messageId) }
}
