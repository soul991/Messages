package com.messages.core.export

import com.messages.core.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text conversation export (Phase 4 item 16). Pure formatting so the
 * output is JVM-testable; the SAF write lives in the ChatViewModel.
 *
 * Format, one message per block:
 *   [Wed, 22 Jul 2026 21:45] Sam: see you at 8
 * Multi-line bodies indent continuation lines so blocks stay parseable by eye.
 */
object ConversationExporter {

    private const val INDENT = "    "

    fun format(
        messages: List<MessageEntity>,
        conversationName: String,
        ownLabel: String = "Me",
    ): String {
        val fmt = SimpleDateFormat("EEE, d MMM yyyy HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("Conversation with ").append(conversationName).append('\n')
        sb.append("Exported from Messages · ").append(messages.size).append(" messages\n\n")
        messages.sortedBy { it.timestamp }.forEach { m ->
            val who = if (m.isOutgoing) ownLabel else conversationName
            sb.append('[').append(fmt.format(Date(m.timestamp))).append("] ")
                .append(who).append(": ")
            val lines = m.body.ifBlank { attachmentPlaceholder(m) }.split('\n')
            sb.append(lines.first())
            lines.drop(1).forEach { sb.append('\n').append(INDENT).append(it) }
            if (m.sendStatus == "FAILED") sb.append(" [failed to send]")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun attachmentPlaceholder(m: MessageEntity): String =
        if (m.mediaUri != null || m.mmsId != null) "[media message]" else ""
}
