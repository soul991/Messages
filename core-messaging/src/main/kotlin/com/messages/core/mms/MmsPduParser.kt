package com.messages.core.mms

import java.nio.charset.Charset

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

    // WSP content-type parameter tokens (well-known values, high bit set).
    private const val P_CHARSET = 0x81
    private const val P_NAME = 0x85
    private const val P_FILENAME = 0x97
    private const val P_FILENAME_ALT = 0x98

    /** WSP Short-length tops out at 30; 31 introduces a uintvar instead. */
    private const val MAX_SHORT_LENGTH = 30

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
        var bodyType: MediaType? = null

        while (r.hasMore()) {
            val field = r.readByte()
            if (field < 0x80) break
            if (field == H_CONTENT_TYPE) {
                // Content-Type is always the last header; the body follows it.
                bodyType = r.readMediaType()
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
        if (bodyType != null && bodyType.mime.startsWith("application/vnd.wap.multipart")) {
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
                val partType = r.readMediaType()
                r.pos = headersEnd // skip remaining part headers
                val data = r.readBytes(dataLen)
                when {
                    partType.mime.startsWith("text/plain") ->
                        texts += decodeText(data, partType.charset)
                    partType.mime.startsWith("application/smil") -> Unit // layout markup, not content
                    else -> attachments += Attachment(partType.mime, partType.name, data)
                }
            }
        } else if (bodyType != null && r.hasMore()) {
            // Single-part body
            val data = r.readBytes(pdu.size - r.pos)
            if (bodyType.mime.startsWith("text/plain")) texts += decodeText(data, bodyType.charset)
            else attachments += Attachment(bodyType.mime, bodyType.name, data)
        }
        return RetrieveConf(from, to, subject, texts.joinToString("\n").trim(), attachments)
    }

    /**
     * V2-29: WSP encodes a text charset as an IANA MIBenum, and a message from
     * a non-UTF-8 locale carries one routinely — a Cyrillic or Shift_JIS body
     * decoded as UTF-8 is not a rare corruption, it is the normal result. Only
     * charsets a handset can actually emit are listed; anything else falls back
     * to UTF-8 with replacement characters rather than failing the parse.
     *
     * MIBenum 36 (KS_C_5601) is mapped to its EUC-KR superset, which is what
     * encoders that announce it actually produce.
     */
    private val CHARSET_NAMES = mapOf(
        3 to "US-ASCII", 4 to "ISO-8859-1", 5 to "ISO-8859-2", 6 to "ISO-8859-3",
        7 to "ISO-8859-4", 8 to "ISO-8859-5", 9 to "ISO-8859-6", 10 to "ISO-8859-7",
        11 to "ISO-8859-8", 12 to "ISO-8859-9", 13 to "ISO-8859-10",
        17 to "Shift_JIS", 18 to "EUC-JP", 36 to "EUC-KR", 37 to "ISO-2022-KR",
        38 to "EUC-KR", 39 to "ISO-2022-JP", 106 to "UTF-8", 113 to "GBK",
        1000 to "UTF-16BE", 1013 to "UTF-16BE", 1014 to "UTF-16LE", 1015 to "UTF-16",
        2025 to "GB2312", 2026 to "Big5", 2084 to "KOI8-R",
        2250 to "windows-1250", 2251 to "windows-1251", 2252 to "windows-1252",
        2253 to "windows-1253", 2254 to "windows-1254", 2255 to "windows-1255",
        2256 to "windows-1256", 2257 to "windows-1257", 2258 to "windows-1258",
    )

    /**
     * Body text in whatever the part declared, never failing over a bad
     * declaration: an unknown charset falls back to UTF-8 with replacement
     * characters, which is still readable, where throwing would lose the whole
     * message.
     */
    private fun decodeText(data: ByteArray, charset: Charset?): String =
        runCatching { data.toString(charset ?: Charsets.UTF_8) }
            .getOrElse { data.toString(Charsets.UTF_8) }

    /** Null when the MIBenum is unknown or the platform lacks that charset. */
    internal fun charsetForMib(mib: Int): Charset? =
        CHARSET_NAMES[mib]?.let { runCatching { Charset.forName(it) }.getOrNull() }

    /** Wide encodings embed NUL bytes, so byte-wise NUL termination is wrong for them. */
    private fun Charset.isWide(): Boolean =
        name().startsWith("UTF-16") || name().startsWith("UTF-32")

    /** Content type, plus the two parameters we care about. */
    private data class MediaType(
        val mime: String,
        val name: String?,
        val charset: Charset?,
    )

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

        /**
         * Long-integer: Short-length (0–30) followed by that many octets.
         *
         * V2-29: this used to consume only the first eight octets of a longer
         * field, leaving the rest to be read as if they were the next header —
         * one over-wide integer misaligned everything after it and corrupted an
         * otherwise parseable PDU. Every declared octet is now consumed; only
         * the representable ones are accumulated, and a value too large for a
         * Long saturates rather than silently wrapping to a small number.
         *
         * A declared length above 30 is not a Long-integer at all, so no length
         * is trusted: null is returned without consuming, and the caller's
         * header loop terminates on the next unrecognised octet.
         */
        fun readLongInteger(): Long? {
            if (!hasMore()) return null
            val len = readByte()
            if (len > MAX_SHORT_LENGTH) return null
            var value = 0L
            var overflow = false
            repeat(len) {
                if (!hasMore()) return if (overflow) Long.MAX_VALUE else value
                val b = readByte()
                if (value > (Long.MAX_VALUE ushr 8)) overflow = true
                if (!overflow) value = (value shl 8) or b.toLong()
            }
            return if (overflow) Long.MAX_VALUE else value
        }

        /**
         * Integer-value used as a charset: Short-integer (one octet, high bit
         * set) or Long-integer. Returns null when the field is absent or names
         * a charset this platform does not have.
         */
        fun readCharsetValue(end: Int): Charset? {
            if (pos >= end || !hasMore()) return null
            val first = peek()
            return when {
                first >= 0x80 -> { pos++; charsetForMib(first and 0x7F) }
                first in 1..MAX_SHORT_LENGTH -> readLongInteger()?.let {
                    if (it in 0..Int.MAX_VALUE.toLong()) charsetForMib(it.toInt()) else null
                }
                // Not a charset: the text starts here (or it is Any-charset).
                else -> null
            }
        }

        /** Decode with a replacement-safe fallback — never fail a whole PDU over one field. */
        private fun decode(bytes: ByteArray, charset: Charset?): String =
            runCatching { bytes.toString(charset ?: Charsets.UTF_8) }
                .getOrElse { bytes.toString(Charsets.UTF_8) }

        /**
         * The text between [start] and [end], NUL-terminated the way the
         * charset actually terminates: byte-wise for 8-bit encodings, but a
         * UTF-16 body is full of legitimate 0x00 bytes and must be taken whole
         * with only its trailing NUL unit trimmed.
         */
        private fun sliceText(start: Int, end: Int, charset: Charset?): String {
            if (charset != null && charset.isWide()) {
                var stop = end
                while (stop - 2 >= start && buf[stop - 1].toInt() == 0 && buf[stop - 2].toInt() == 0) {
                    stop -= 2
                }
                return decode(buf.copyOfRange(start, stop.coerceAtLeast(start)), charset)
            }
            var stop = start
            while (stop < end && buf[stop].toInt() != 0) stop++
            return decode(buf.copyOfRange(start, stop), charset)
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
                // V2-29: the charset was previously skipped and the bytes read
                // as UTF-8, which mangles every non-UTF-8 sender name and
                // subject line.
                val charset = readCharsetValue(end)
                val s = sliceText(pos, end, charset)
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

        /**
         * Content-type-value: constrained-media or general-form. The message
         * body and each multipart part use the same grammar, so both go through
         * here — which is how the body of a single-part MMS finally gets its
         * charset parameter honoured (V2-29) instead of being read as UTF-8.
         */
        fun readMediaType(): MediaType {
            if (!hasMore()) return MediaType("application/octet-stream", null, null)
            val first = peek()
            return when {
                first in 0x80..0xFF -> { pos++; MediaType(wellKnown(first and 0x7F), null, null) }
                first in 0x20..0x7F -> MediaType(readTextString(), null, null)
                else -> { // general form: value-length + media-type + parameters
                    val len = readValueLength()
                    val end = (pos + len).coerceAtMost(buf.size)
                    val media = when {
                        !hasMore() -> "application/octet-stream"
                        peek() >= 0x80 -> wellKnown(readByte() and 0x7F)
                        else -> readTextString()
                    }
                    var name: String? = null
                    var charset: Charset? = null
                    while (pos < end) {
                        when (readByte()) {
                            P_CHARSET -> charset = readCharsetValue(end)
                            // name / filename variants across encoder vintages
                            P_NAME, P_FILENAME, P_FILENAME_ALT -> name = readTextString()
                            else -> {
                                if (pos >= end) break
                                skipHeaderValueBounded(end)
                            }
                        }
                    }
                    pos = end // any parameters we did not recognise (start-part, type, …)
                    MediaType(media, name, charset)
                }
            }
        }

        private fun wellKnown(code: Int): String =
            WELL_KNOWN_TYPES[code] ?: "application/octet-stream"

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
