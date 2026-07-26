package com.messages.app.ui.chat

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messages.app.mms.MmsSender
import com.messages.app.schedule.Scheduler
import com.messages.app.schedule.SmsRadio
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
    /** Secret space: which side of the wall this chat instance lives on. The
     *  same threadId can have one conversation per space (New locked chat). */
    val space: String = com.messages.core.db.Spaces.NORMAL,
    /** Survives process death: staged attachment + in-flight camera target —
     *  Android routinely kills us while the camera app is foreground. */
    private val savedState: androidx.lifecycle.SavedStateHandle =
        androidx.lifecycle.SavedStateHandle(),
) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val inLockedSpace: Boolean get() = space == com.messages.core.db.Spaces.LOCKED

    val messages: StateFlow<List<MessageEntity>> =
        repo.db.messages().messagesForThread(threadId, space)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contactName = MutableStateFlow<String?>(null)
    val address = MutableStateFlow("")

    /** Conversation category — tints the top-bar avatar to match the list row. */
    val category = MutableStateFlow<String?>(null)

    /** Locked conversation (§8.2): gate the chat UI until authenticated. */
    val locked = MutableStateFlow(false)
    val chatUnlocked = MutableStateFlow(false)

    /** Dual-SIM (§8.1): available SIMs and this chat's choice (null = system default). */
    data class SimOption(val subId: Int, val slotIndex: Int, val displayName: String)
    val simOptions = MutableStateFlow<List<SimOption>>(emptyList())
    val selectedSubId = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val conv = repo.db.conversations().byThreadId(threadId, space)
            if (conv != null) {
                address.value = conv.address
                contactName.value = conv.contactName
                category.value = conv.category
                selectedSubId.value = conv.preferredSubId
                locked.value = conv.locked
            } else if (!fallbackAddress.isNullOrBlank()) {
                address.value = fallbackAddress
                contactName.value = withContext(Dispatchers.IO) {
                    repo.displayNameFor(fallbackAddress)
                }
            }
            repo.db.messages().markThreadRead(threadId, space)
            repo.db.conversations().clearUnread(threadId, space)
            com.messages.app.widget.WidgetUpdater.requestUpdate(getApplication())
            // Conversation shortcuts + direct-share ranking (§8.2). NEVER for
            // the secret space or legacy locked chats — no launcher identity.
            if (conv?.locked != true && !inLockedSpace) {
                val name = contactName.value ?: address.value
                if (name.isNotBlank()) {
                    com.messages.app.shortcut.ConversationShortcuts.push(
                        getApplication(), threadId, name,
                    )
                    com.messages.app.shortcut.ConversationShortcuts.reportUsed(
                        getApplication(), threadId,
                    )
                }
            }
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

    fun send(text: String) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val subId = selectedSubId.value
            val entity = repo.storeOutgoing(to, text, System.currentTimeMillis(), subId, space)
            SmsRadio.send(getApplication(), repo, entity)
        }
    }

    /** Scheduled send (§8.2): index-only row now, worker fires at [sendAt]. */
    fun scheduleSend(text: String, sendAt: Long) {
        val to = address.value
        if (to.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val entity = repo.storeScheduledSms(to, text, sendAt, selectedSubId.value, space)
            Scheduler.scheduleSend(getApplication(), entity.id, sendAt)
        }
    }

    /** "Send now" on a scheduled bubble. */
    fun sendScheduledNow(messageId: Long) = viewModelScope.launch {
        Scheduler.cancelSend(getApplication(), messageId)
        val entity = repo.promoteScheduledToSending(messageId) ?: return@launch
        SmsRadio.send(getApplication(), repo, entity)
    }

    /** Cancel a scheduled bubble: drop the row + the pending worker. */
    fun cancelScheduled(messageId: Long) = viewModelScope.launch {
        Scheduler.cancelSend(getApplication(), messageId)
        repo.cancelScheduled(messageId)
    }

    /** Snooze / remind-me-about-this-message (§8.2). */
    fun snooze(messageId: Long, remindAt: Long) {
        Scheduler.snooze(getApplication(), messageId, remindAt)
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
                MmsSender.send(ctx, message.address, message.body, attachment, selectedSubId.value, space)
            }
        } else {
            send(message.body)
        }
    }

    // ---- Per-chat customization (§8.2): bubble color + wallpaper ----
    val bubbleStyleId = MutableStateFlow(ChatStyle.bubbleId(app, threadId))
    val wallpaperId = MutableStateFlow(ChatStyle.wallpaperId(app, threadId))

    /** Bumped on photo import so a re-imported image invalidates the cache. */
    val wallpaperVersion = MutableStateFlow(0)

    fun setBubbleStyle(id: String) {
        ChatStyle.setBubble(getApplication(), threadId, id)
        bubbleStyleId.value = id
    }

    fun setWallpaper(id: String) {
        ChatStyle.setWallpaper(getApplication(), threadId, id)
        wallpaperId.value = id
    }

    fun importWallpaper(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        if (ChatStyle.importPhoto(getApplication(), threadId, uri)) {
            wallpaperId.value = ChatStyle.WALLPAPER_PHOTO
            wallpaperVersion.value++
        } else {
            sendError.value = "Couldn't use that image"
        }
    }

    /** Attachment picked in the composer, pending send (process-death safe). */
    val pendingAttachment: StateFlow<Uri?> =
        savedState.getStateFlow("pending_attachment", null)

    /** FileProvider target of an in-flight camera capture (process-death safe:
     *  TakePicture only returns a boolean — we must remember where it wrote). */
    val cameraTarget: StateFlow<Uri?> = savedState.getStateFlow("camera_target", null)

    val sendError = MutableStateFlow<String?>(null)

    fun attach(uri: Uri?) {
        savedState["pending_attachment"] = uri
    }

    fun setCameraTarget(uri: Uri?) {
        savedState["camera_target"] = uri
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
        savedState["pending_attachment"] = null
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val attachment = MmsSender.prepareAttachment(ctx, uri)
            if (attachment == null) {
                sendError.value = "Couldn't attach that file (too large or unreadable)"
                if (text.isNotBlank()) send(text)
                return@launch
            }
            MmsSender.send(ctx, to, text, attachment, selectedSubId.value, space)
        }
    }

    fun clearSendError() {
        sendError.value = null
    }

    fun markChatUnlocked() {
        chatUnlocked.value = true
    }

    // ---- Secret locked space: "Lock chat" bottom sheet actions ----

    /**
     * "New locked chat": existing history stays here in the normal list; a
     * locked conversation for the same address appears in the locked space
     * and claims all future incoming messages from it.
     */
    fun lockNewChat(onDone: () -> Unit) = viewModelScope.launch {
        repo.createLockedConversation(threadId)
        removeLauncherIdentity()
        onDone()
    }

    /** Shortcut, per-conversation channel, and any visible notification go
     *  away the moment a chat is locked (spec: "remove any existing on lock"). */
    private fun removeLauncherIdentity() {
        com.messages.app.shortcut.ConversationShortcuts.remove(getApplication(), threadId)
        com.messages.app.notify.ConversationChannels.remove(getApplication(), threadId)
        androidx.core.app.NotificationManagerCompat.from(getApplication())
            .cancel(threadId.toInt())
    }

    /** "Unlock chat" from inside the locked space: whole thread returns. */
    fun unlockChat(onDone: () -> Unit) = viewModelScope.launch {
        repo.moveThreadToSpace(
            threadId, com.messages.core.db.Spaces.LOCKED, com.messages.core.db.Spaces.NORMAL,
        )
        onDone()
    }

    /**
     * "Move entire chat": the whole thread disappears from the normal list,
     * FTS, and search, and lands in the locked space.
     */
    fun lockMoveChat(onDone: () -> Unit) = viewModelScope.launch {
        repo.moveThreadToSpace(
            threadId, com.messages.core.db.Spaces.NORMAL, com.messages.core.db.Spaces.LOCKED,
        )
        removeLauncherIdentity()
        com.messages.app.ui.common.DraftStore.clear(getApplication(), threadId)
        onDone()
    }

    fun moveToInbox(messageId: Long) = viewModelScope.launch { repo.moveToInbox(messageId) }
    fun moveToSpam(messageId: Long) = viewModelScope.launch { repo.moveToSpam(messageId) }
    fun star(messageId: Long, starred: Boolean) = viewModelScope.launch {
        repo.db.messages().setStarred(messageId, starred)
    }
    /** User delete → Trash (§6.4): provider row removed, restorable for 60 days. */
    fun delete(messageId: Long) = viewModelScope.launch { repo.moveToTrash(messageId) }

    /** Delete the whole conversation → Trash (§6.4). */
    fun deleteThread(onDone: () -> Unit) = viewModelScope.launch {
        repo.moveThreadToTrash(threadId, space)
        onDone()
    }

    /** Phase 4 item 16: plain-text export of this conversation to a SAF uri. */
    fun exportConversation(uri: android.net.Uri, onDone: (Boolean) -> Unit) =
        viewModelScope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val text = com.messages.core.export.ConversationExporter.format(
                        messages.value.filter { it.sendStatus != "SCHEDULED" },
                        conversationName = contactName.value ?: address.value.ifBlank { "Unknown" },
                    )
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            onDone(ok)
        }

    /** Phase 4 item 14: conversations for the forward picker (blank query = recents). */
    suspend fun conversationsForForward(query: String): List<com.messages.core.db.ConversationEntity> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            // '' LIKE-matches everything → recents by lastTimestamp. Locked
            // conversations are excluded: forwarding into them from an
            // unauthenticated picker would leak their existence.
            repo.db.conversations().searchByNameOrAddress(query.trim())
                .filter { !it.locked }
        }
}
