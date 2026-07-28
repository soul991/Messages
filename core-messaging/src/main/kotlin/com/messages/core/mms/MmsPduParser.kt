package com.messages.core.mms

/**
 * Minimal WSP/MMS PDU parser — just enough of OMA-MMS-ENC to handle the two
 * PDUs an SMS app receives: m-notification-ind (arrives in WAP_PUSH_DELIVER)
 * and m-retrieve-conf (written by SmsManager.downloadMultimediaMessage).
 * Anything unrecognized is skipped with the generic WSP header-skip rule, so
 * carrier quirks degrade to "fields missing", never to a crash.
 */
object MmsPduParser {

    data class NotificationInd(
        val transactionId: String?,
        val from: String?,
        val subject: String?,
        val contentLocation: String?,
        val messageSize: Long?,
    )

    data class Attachment(val mimeType: String, val name: String?, val data: ByteArray)

    data class RetrieveConf(
        val from: String?,
        /** To/CC recipients — non-empty beyond our own number means a group MMS. */
        val to: List<String>,
        val subject: String?,
        val textBody: String,
        val attachments: List<Attachment>,
    )

    // MMS header field IDs (with high bit set)
    private const val H_CC = 0x82
    private const val H_CONTENT_LOCATION = 0x83
    private const val H_CONTENT_TYPE = 0x84
    private const val H_FROM = 0x89
    private const val H_MESSAGE_TYPE = 0x8C
    private const val H_MESSAGE_SIZE = 0x8E
    private const val H_SUBJECT = 0x96
    private const val H_TO = 0x97
    private const val H_TRANSACTION_ID = 0x98

    private const val TYPE_NOTIFICATION_IND = 0x82
    private const val TYPE_RETRIEVE_CONF = 0x84

    fun parseNotificationInd(pdu: ByteArray): NotificationInd? = try {
        // R-17: a notification-ind is a few hundred bytes; anything PDU-sized
        // is not one and is rejected before parsing.
        if (pdu.size > MAX_PDU_BYTES) throw IllegalArgumentException("oversize notification")
        val r = Reader(pdu)
        var transactionId: String? = null
        var from: String? = null
        var subject: String? = null
        var contentLocation: String? = null
        var messageSize: Long? = null
        var messageType = -1

        while (r.hasMore()) {
            val field = r.readByte()
            if (field < 0x80) break // not a well-known header — stop cleanly
            when (field) {
                H_MESSAGE_TYPE -> messageType = r.readByte()
                H_TRANSACTION_ID -> transactionId = r.readTextString()
                H_FROM -> from = r.readFromValue()
                H_SUBJECT -> subject = r.readEncodedString()
                H_CONTENT_LOCATION -> contentLocation = r.readTextString()
                H_MESSAGE_SIZE -> messageSize = r.readLongInteger()
                else -> r.skipHeaderValue()
            }
        }
        if (messageType != -1 && messageType != TYPE_NOTIFICATION_IND) null
        else NotificationInd(transactionId, from, subject, contentLocation, messageSize)
    } catch (_: Exception) {
        null
    }

    /**
     * R-17: hard bounds on carrier-supplied input. A hostile or corrupt PDU can
     * claim any part count and any per-part length; without these the parser
     * would try to honour whatever it claimed.
     */
    /** Whole-PDU ceiling — far above any real MMS (carriers cap around 1–3 MB). */
    const val MAX_PDU_BYTES = 8 * 1024 * 1024

    /** No legitimate MMS carries anywhere near this many parts. */
    private const val MAX_PARTS = 64

    /** Per-part payload ceiling. */
    private const val MAX_PART_BYTES = 4 * 1024 * 1024

    /** Part-header block ceiling. */
    private const val MAX_PART_HEADER_BYTES = 64 * 1024

    fun parseRetrieveConf(pdu: ByteArray): RetrieveConf? = try {
        if (pdu.size > MAX_PDU_BYTES) null else parseRetrieveConfInner(pdu)
    } catch (_: Exception) {
        null
    }

    private fun parseRetrieveConfInner(pdu: ByteArray): RetrieveConf? {
        val r = Reader(pdu)
        var from: String? = null
        val to = mutableListOf<String>()
        var subject: String? = null
        var messageType = -1
        var bodyContentType: String? = null

        while (r.hasMore()) {
            val field = r.readByte()
            if (field < 0x80) break
            if (field == H_CONTENT_TYPE) {
                // Content-Type is always the last header; the body follows it.
                bodyContentType = r.readContentTypeValue()
                break
            }
            when (field) {
                H_MESSAGE_TYPE -> messageType = r.readByte()
                H_FROM -> from = r.readFromValue()
                H_TO, H_CC -> r.readEncodedString().substringBefore("/TYPE=")
                    .takeIf { it.isNotBlank() }?.let { to += it }
                H_SUBJECT -> subject = r.readEncodedString()
                else -> r.skipHeaderValue()
            }
        }
        if (messageType != -1 && messageType != TYPE_RETRIEVE_CONF) return null

        val texts = mutableListOf<String>()
        val attachments = mutableListOf<Attachment>()
        if (bodyContentType != null && bodyContentType.startsWith("application/vnd.wap.multipart")) {
            // R-17: the claimed part count is attacker-controlled; cap it.
            var count = r.readUintvar().toInt().coerceIn(0, MAX_PARTS)
            while (count-- > 0 && r.hasMore()) {
                val headersLen = r.readUintvar().toInt()
                val dataLen = r.readUintvar().toInt()
                // A claimed length that is negative (overflowed), over the
                // per-part ceiling, or past the end of the PDU means the frame
                // is malformed — stop rather than allocate on its word.
                if (headersLen < 0 || headersLen > MAX_PART_HEADER_BYTES) break
                if (dataLen < 0 || dataLen > MAX_PART_BYTES) break
                val headersEnd = r.pos + headersLen
                if (headersEnd > pdu.size || headersEnd + dataLen > pdu.size) break
                val (mime, name) = r.readPartContentType()
                r.pos = headersEnd // skip remaining part headers
                val data = r.readBytes(dataLen)
                when {
                    mime.startsWith("text/plain") -> texts += data.toString(Charsets.UTF_8)
                    mime.startsWith("application/smil") -> Unit // layout markup, not content
                    else -> attachments += Attachment(mime, name, data)
                }
            }
        } else if (bodyContentType != null && r.hasMore()) {
            // Single-part body
            val data = r.readBytes(pdu.size - r.pos)
            if (bodyContentType.startsWith("text/plain")) texts += data.toString(Charsets.UTF_8)
            else attachments += Attachment(bodyContentType, null, data)
        }
        return RetrieveConf(from, to, subject, texts.joinToString("\n").trim(), attachments)
    }

    /** WSP well-known content types we expect; others arrive as literal strings. */
    private val WELL_KNOWN_TYPES = mapOf(
        0x03 to "text/plain",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x1F to "image/tiff",
        0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x33 to "application/vnd.wap.multipart.related",
    )

    private class Reader(val buf: ByteArray) {
        var pos = 0

        fun hasMore() = pos < buf.size
        fun readByte(): Int = buf[pos++].toInt() and 0xFF
        fun peek(): Int = buf[pos].toInt() and 0xFF
        fun readBytes(n: Int): ByteArray {
            val end = (pos + n).coerceAtMost(buf.size)
            val out = buf.copyOfRange(pos, end)
            pos = end
            return out
        }

        fun readUintvar(): Long {
            var value = 0L
            while (hasMore()) {
                val b = readByte()
                value = (value shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) break
            }
            return value
        }

        /** Value-length: 0–30 literal, 31 → uintvar follows. */
        fun readValueLength(): Int {
            val first = readByte()
            return if (first <= 30) first else readUintvar().toInt()
        }

        fun readLongInteger(): Long {
            val len = readByte()
            var v = 0L
            repeat(len.coerceAtMost(8)) { v = (v shl 8) or readByte().toLong() }
            return v
        }

        /** Null-terminated text; a leading 0x7F quote octet is skipped. */
        fun readTextString(): String {
            if (hasMore() && peek() == 0x7F) pos++
            val start = pos
            while (hasMore() && buf[pos].toInt() != 0) pos++
            val s = buf.copyOfRange(start, pos).toString(Charsets.UTF_8)
            if (hasMore()) pos++ // consume NUL
            return s
        }

        /** Encoded-string-value: text-string, or value-length + charset + text. */
        fun readEncodedString(): String {
            if (!hasMore()) return ""
            val first = peek()
            if (first <= 31) {
                val len = readValueLength()
                val end = (pos + len).coerceAtMost(buf.size)
                if (hasMore() && peek() >= 0x80) pos++ // charset short-integer — assume UTF-8-compatible
                val start = pos
                var stop = start
                while (stop < end && buf[stop].toInt() != 0) stop++
                val s = buf.copyOfRange(start, stop).toString(Charsets.UTF_8)
                pos = end
                return s
            }
            return readTextString()
        }

        /** From-value: value-length + (address-present-token 0x80 + address | insert-address-token 0x81). */
        fun readFromValue(): String? {
            val len = readValueLength()
            val end = (pos + len).coerceAtMost(buf.size)
            if (pos >= end) return null
            val token = readByte()
            val addr = if (token == 0x80 && pos < end) {
                readEncodedString().substringBefore("/TYPE=").ifBlank { null }
            } else null // 0x81: MMSC inserts the address; we don't know it
            pos = end
            return addr
        }

        /** Content-type-value at message level: constrained-media or general-form. */
        fun readContentTypeValue(): String {
            val first = peek()
            return when {
                first in 0x80..0xFF -> { pos++; WELL_KNOWN_TYPES[first and 0x7F] ?: "application/octet-stream" }
                first in 0x20..0x7F -> readTextString()
                else -> { // general form: value-length + media-type + parameters
                    val len = readValueLength()
                    val end = (pos + len).coerceAtMost(buf.size)
                    val media = when {
                        peek() >= 0x80 -> WELL_KNOWN_TYPES[readByte() and 0x7F] ?: "application/octet-stream"
                        else -> readTextString()
                    }
                    pos = end // skip parameters (start-part, type, …)
                    media
                }
            }
        }

        /** Part content type + best-effort name/filename parameter. */
        fun readPartContentType(): Pair<String, String?> {
            val first = peek()
            var name: String? = null
            val mime: String = when {
                first in 0x80..0xFF -> { pos++; WELL_KNOWN_TYPES[first and 0x7F] ?: "application/octet-stream" }
                first in 0x20..0x7F -> readTextString()
                else -> {
                    val len = readValueLength()
                    val end = (pos + len).coerceAtMost(buf.size)
                    val media = when {
                        peek() >= 0x80 -> WELL_KNOWN_TYPES[readByte() and 0x7F] ?: "application/octet-stream"
                        else -> readTextString()
                    }
                    // Scan parameters for Name (0x85) / Filename (0x98 in params space)
                    while (pos < end) {
                        val p = readByte()
                        when (p) {
                            0x85, 0x97, 0x98 -> name = readTextString() // name / filename variants
                            else -> {
                                if (pos >= end) break
                                skipHeaderValueBounded(end)
                            }
                        }
                    }
                    pos = end
                    media
                }
            }
            return mime to name
        }

        /** Generic WSP rule for skipping a header value we don't understand. */
        fun skipHeaderValue() {
            if (!hasMore()) return
            val first = peek()
            when {
                first <= 30 -> { pos++; pos += first }
                first == 31 -> { pos++; pos += readUintvar().toInt() }
                first <= 127 -> readTextString()
                else -> pos++ // single-octet value
            }
            if (pos > buf.size) pos = buf.size
        }

        private fun skipHeaderValueBounded(end: Int) {
            skipHeaderValue()
            if (pos > end) pos = end
        }
    }
}
