package com.messages.app.ui.chat

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.mms.MmsSender
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

    /** Dual-SIM (§8.1): available SIMs and this chat's choice (null = system default). */
    data class SimOption(val subId: Int, val slotIndex: Int, val displayName: String)
    val simOptions = MutableStateFlow<List<SimOption>>(emptyList())
    val selectedSubId = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val conv = repo.db.conversations().byThreadId(threadId)
            if (conv != null) {
                address.value = conv.address
                contactName.value = conv.contactName
                selectedSubId.value = conv.preferredSubId
            } else if (!fallbackAddress.isNullOrBlank()) {
                address.value = fallbackAddress
                contactName.value = withContext(Dispatchers.IO) {
                    repo.lookupContactName(fallbackAddress)
                }
            }
            repo.db.messages().markThreadRead(threadId)
            repo.db.conversations().clearUnread(threadId)
            loadSimOptions()
        }
    }

    private fun loadSimOptions() {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val subs = try {
            ctx.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        // Only surface a choice when there genuinely are 2+ SIMs.
        simOptions.value = if (subs.size >= 2) subs.map {
            SimOption(
                it.subscriptionId, it.simSlotIndex,
                it.displayName?.toString() ?: "SIM ${it.simSlotIndex + 1}",
            )
        } else emptyList()
    }

    /** Per-chat SIM choice, persisted on the conversation row. */
    fun selectSim(subId: Int?) {
        selectedSubId.value = subId
        viewModelScope.launch { repo.db.conversations().setPreferredSubId(threadId, subId) }
    }

    private fun smsManagerFor(subId: Int?): SmsManager {
        val ctx = getApplication<Application>()
        val base = ctx.getSystemService(SmsManager::class.java)
        return if (subId != null) base.createForSubscriptionId(subId) else base
    }

    fun send(text: String) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val subId = selectedSubId.value
            val entity = repo.storeOutgoing(to, text, System.currentTimeMillis(), subId)
            try {
                val ctx = getApplication<Application>()
                val sms = smsManagerFor(subId)
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

    fun resend(message: MessageEntity) {
        // Failed MMS: rebuild from the locally saved media file, don't degrade to text.
        if (message.mmsTransactionId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val attachment = message.mediaUri
                    ?.let { path -> runCatching { java.io.File(path).readBytes() }.getOrNull() }
                    ?.let { bytes ->
                        com.messages.core.mms.MmsPduParser.Attachment(
                            message.mediaMimeType ?: "application/octet-stream", null, bytes,
                        )
                    }
                if (attachment == null && message.body.isBlank()) {
                    sendError.value = "Nothing left to resend"
                    return@launch
                }
                MmsSender.send(ctx, message.address, message.body, attachment, selectedSubId.value)
            }
        } else {
            send(message.body)
        }
    }

    /** Attachment picked in the composer, pending send. */
    val pendingAttachment = MutableStateFlow<Uri?>(null)
    val sendError = MutableStateFlow<String?>(null)

    fun attach(uri: Uri?) {
        pendingAttachment.value = uri
    }

    /** Send text + pending attachment as MMS (falls back to plain SMS when no attachment). */
    fun sendWithAttachment(text: String) {
        val uri = pendingAttachment.value
        if (uri == null) {
            send(text)
            return
        }
        val to = address.value
        if (to.isBlank()) return
        pendingAttachment.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val attachment = MmsSender.prepareAttachment(ctx, uri)
            if (attachment == null) {
                sendError.value = "Couldn't attach that file (too large or unreadable)"
                if (text.isNotBlank()) send(text)
                return@launch
            }
            MmsSender.send(ctx, to, text, attachment, selectedSubId.value)
        }
    }

    fun clearSendError() {
        sendError.value = null
    }

    fun moveToInbox(messageId: Long) = viewModelScope.launch { repo.moveToInbox(messageId) }
    fun moveToSpam(messageId: Long) = viewModelScope.launch { repo.moveToSpam(messageId) }
    fun star(messageId: Long, starred: Boolean) = viewModelScope.launch {
        repo.db.messages().setStarred(messageId, starred)
    }
    fun delete(messageId: Long) = viewModelScope.launch { repo.db.messages().userDelete(messageId) }
}
