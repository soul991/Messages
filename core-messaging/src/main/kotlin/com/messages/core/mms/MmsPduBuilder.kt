package com.messages.core.mms

import java.io.ByteArrayOutputStream

/**
 * Minimal WSP/MMS PDU encoder — the send-side counterpart of [MmsPduParser].
 * Builds only the one PDU an SMS app submits: m-send-req, handed to
 * SmsManager.sendMultimediaMessage. Multipart-mixed body: optional text part
 * plus binary attachments.
 */
object MmsPduBuilder {

    data class Part(val mimeType: String, val name: String?, val data: ByteArray)

    // MMS header field IDs (high bit set) — same space as MmsPduParser
    private const val H_MESSAGE_TYPE = 0x8C
    private const val H_TRANSACTION_ID = 0x98
    private const val H_MMS_VERSION = 0x8D
    private const val H_FROM = 0x89
    private const val H_TO = 0x97
    private const val H_CONTENT_TYPE = 0x84

    private const val TYPE_SEND_REQ = 0x80
    private const val MMS_VERSION_1_2 = 0x92 // short-integer 1.2
    private const val INSERT_ADDRESS_TOKEN = 0x81 // MMSC fills in our number
    private const val CT_MULTIPART_MIXED = 0xA3 // 0x23 | 0x80, constrained form
    private const val WK_TEXT_PLAIN = 0x03
    private const val CHARSET_UTF8 = 0xEA // MIBenum 106 as short-integer

    private val WELL_KNOWN_BINARY = mapOf(
        "image/gif" to 0x1D,
        "image/jpeg" to 0x1E,
        "image/tiff" to 0x1F,
        "image/png" to 0x20,
    )

    fun buildSendReq(recipients: List<String>, parts: List<Part>, transactionId: String): ByteArray {
        val out = ByteArrayOutputStream()

        // Headers. X-Mms-Message-Type MUST be first; Content-Type MUST be last
        // (the body follows it — same rule the parser relies on).
        out.write(H_MESSAGE_TYPE); out.write(TYPE_SEND_REQ)
        out.write(H_TRANSACTION_ID); out.writeTextString(transactionId)
        out.write(H_MMS_VERSION); out.write(MMS_VERSION_1_2)
        // From: value-length 1 + insert-address-token
        out.write(H_FROM); out.write(1); out.write(INSERT_ADDRESS_TOKEN)
        recipients.forEach { addr ->
            out.write(H_TO); out.writeTextString("$addr/TYPE=PLMN")
        }
        out.write(H_CONTENT_TYPE); out.write(CT_MULTIPART_MIXED)

        // Body: uintvar part count, then per part uintvar(headersLen) +
        // uintvar(dataLen) + content-type + data.
        out.writeUintvar(parts.size.toLong())
        parts.forEach { part ->
            val ct = encodePartContentType(part)
            out.writeUintvar(ct.size.toLong())
            out.writeUintvar(part.data.size.toLong())
            out.write(ct)
            out.write(part.data)
        }
        return out.toByteArray()
    }

    /** Content-type value for one part (this is the part's entire header block). */
    private fun encodePartContentType(part: Part): ByteArray {
        val out = ByteArrayOutputStream()
        when {
            part.mimeType.startsWith("text/plain") -> {
                // General form: value-length + media + charset param (UTF-8)
                val inner = byteArrayOf(
                    (WK_TEXT_PLAIN or 0x80).toByte(),
                    0x81.toByte(), CHARSET_UTF8.toByte(),
                )
                out.write(inner.size) // ≤30, literal value-length
                out.write(inner)
            }
            WELL_KNOWN_BINARY.containsKey(part.mimeType) && part.name == null ->
                out.write(WELL_KNOWN_BINARY.getValue(part.mimeType) or 0x80)
            else -> {
                // General form: value-length + extension-media (+ name param)
                val inner = ByteArrayOutputStream()
                val known = WELL_KNOWN_BINARY[part.mimeType]
                if (known != null) inner.write(known or 0x80) else inner.writeTextString(part.mimeType)
                if (part.name != null) {
                    inner.write(0x85) // name parameter
                    inner.writeTextString(part.name)
                }
                val bytes = inner.toByteArray()
                if (bytes.size <= 30) out.write(bytes.size)
                else { out.write(31); out.writeUintvar(bytes.size.toLong()) }
                out.write(bytes)
            }
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeTextString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) write(0x7F) // quote octet
        write(bytes)
        write(0)
    }

    private fun ByteArrayOutputStream.writeUintvar(value: Long) {
        var v = value
        val stack = ArrayDeque<Int>()
        stack.addFirst((v and 0x7F).toInt())
        v = v ushr 7
        while (v > 0) {
            stack.addFirst(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        stack.forEach { write(it) }
    }
}
