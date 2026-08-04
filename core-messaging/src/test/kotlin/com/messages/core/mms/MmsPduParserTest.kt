package com.messages.core.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * V2-29 corpus tests. Carrier PDUs are not something we can re-request when a
 * parse goes wrong — the two failure modes here (an over-wide integer that
 * misaligns every later field, and a charset declaration that is announced and
 * then ignored) both produce plausible-looking output, so they need pinning by
 * construction rather than by inspection.
 */
class MmsPduParserTest {

    // ---- PDU construction helpers (OMA-MMS-ENC / WSP) ----

    private class Pdu {
        private val out = java.io.ByteArrayOutputStream()
        fun byte(vararg b: Int) = apply { b.forEach { out.write(it and 0xFF) } }
        fun bytes(b: ByteArray) = apply { out.write(b) }
        /** Null-terminated Text-string. */
        fun text(s: String) = apply { out.write(s.toByteArray(Charsets.US_ASCII)); out.write(0) }
        fun build(): ByteArray = out.toByteArray()
    }

    /** Encoded-string-value: value-length + charset + NUL-terminated text. */
    private fun encodedString(text: String, charsetOctets: ByteArray, charset: Charset): ByteArray {
        val body = text.toByteArray(charset)
        val terminator = if (charset.name().startsWith("UTF-16")) byteArrayOf(0, 0) else byteArrayOf(0)
        val value = charsetOctets + body + terminator
        require(value.size <= 30) { "test fixture must fit a literal value-length" }
        return byteArrayOf(value.size.toByte()) + value
    }

    /** Short-integer form of a charset MIBenum (only valid below 128). */
    private fun shortCharset(mib: Int) = byteArrayOf((0x80 or mib).toByte())

    /**
     * Long-integer form: short-length + big-endian octets, zero-padded on the
     * left when [octets] exceeds the eight a Long has. (Shift counts are taken
     * mod 64 on the JVM, so the padding has to be explicit — shifting by 64
     * would put the low byte back.)
     */
    private fun longInteger(value: Long, octets: Int): ByteArray {
        val out = ByteArray(octets + 1)
        out[0] = octets.toByte()
        for (i in 0 until minOf(octets, 8)) {
            out[octets - i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    private fun notificationInd(build: Pdu.() -> Unit): ByteArray =
        Pdu().byte(0x8C, 0x82).apply(build).build()

    // ---- Long-integer handling ----

    @Test
    fun `a message size wider than eight octets does not misalign later headers`() {
        // Nine declared octets with the value in the last one: the old parser
        // consumed eight and then read 0x2A as if it were the next header id,
        // losing Content-Location and everything after it.
        val pdu = notificationInd {
            byte(0x8E); bytes(longInteger(42L, 9))
            byte(0x83); text("http://mmsc/42")
        }
        val parsed = MmsPduParser.parseNotificationInd(pdu)!!
        assertEquals(42L, parsed.messageSize)
        assertEquals("http://mmsc/42", parsed.contentLocation)
    }

    @Test
    fun `a message size too large for a Long saturates instead of wrapping`() {
        val pdu = notificationInd {
            byte(0x8E); bytes(ByteArray(13).also { it[0] = 12; for (i in 1..12) it[i] = 0xFF.toByte() })
            byte(0x83); text("http://mmsc/big")
        }
        val parsed = MmsPduParser.parseNotificationInd(pdu)!!
        assertEquals(Long.MAX_VALUE, parsed.messageSize)
        // Alignment survives: the field after the oversized integer still parses.
        assertEquals("http://mmsc/big", parsed.contentLocation)
    }

    @Test
    fun `an impossible declared length is refused rather than trusted`() {
        // 200 octets is not a WSP Short-length at all. Nothing is consumed on
        // its word, and the PDU as a whole still yields a result.
        val pdu = notificationInd {
            byte(0x98); text("T1")
            byte(0x8E); byte(200)
        }
        val parsed = MmsPduParser.parseNotificationInd(pdu)!!
        assertNull(parsed.messageSize)
        assertEquals("T1", parsed.transactionId)
    }

    // ---- Charsets ----

    @Test
    fun `a subject declared as ISO-8859-1 is decoded as ISO-8859-1`() {
        val latin1 = Charset.forName("ISO-8859-1")
        val pdu = notificationInd {
            byte(0x96); bytes(encodedString("Rückruf", shortCharset(4), latin1))
        }
        assertEquals("Rückruf", MmsPduParser.parseNotificationInd(pdu)!!.subject)
    }

    @Test
    fun `a subject declared as UTF-16 survives its embedded NUL bytes`() {
        // MIBenum 1000 needs the long-integer form, and the body is full of
        // 0x00 octets — byte-wise NUL termination would truncate it to empty.
        val utf16 = Charset.forName("UTF-16BE")
        val pdu = notificationInd {
            byte(0x96); bytes(encodedString("Hi", longInteger(1000L, 2), utf16))
        }
        assertEquals("Hi", MmsPduParser.parseNotificationInd(pdu)!!.subject)
    }

    @Test
    fun `an unknown charset falls back to UTF-8 rather than failing the parse`() {
        val pdu = notificationInd {
            byte(0x96); bytes(encodedString("plain", shortCharset(77), Charsets.UTF_8))
            byte(0x83); text("http://mmsc/9")
        }
        val parsed = MmsPduParser.parseNotificationInd(pdu)!!
        assertEquals("plain", parsed.subject)
        assertEquals("http://mmsc/9", parsed.contentLocation)
    }

    @Test
    fun `a multipart text part is decoded in the charset its own header declares`() {
        val cp1251 = Charset.forName("windows-1251")
        val body = "Привет".toByteArray(cp1251)
        // Part Content-Type: value-length, text/plain (0x83), charset param
        // (0x81) carrying MIBenum 2251 as a long-integer.
        val contentType = byteArrayOf(0x05, 0x83.toByte(), 0x81.toByte()) + longInteger(2251L, 2)
        val pdu = Pdu()
            .byte(0x8C, 0x84)                       // m-retrieve-conf
            .byte(0x84, 0x01, 0xA3)                 // Content-Type: multipart.mixed
            .byte(0x01)                             // one part
            .byte(contentType.size, body.size)      // headersLen, dataLen (uintvars < 128)
            .bytes(contentType)
            .bytes(body)
            .build()
        val parsed = MmsPduParser.parseRetrieveConf(pdu)!!
        assertEquals("Привет", parsed.textBody)
        assertTrue(parsed.attachments.isEmpty())
    }

    @Test
    fun `a multipart part with no declared charset still reads as UTF-8`() {
        val body = "hello".toByteArray(Charsets.UTF_8)
        val contentType = byteArrayOf(0x01, 0x83.toByte())
        val pdu = Pdu()
            .byte(0x8C, 0x84)
            .byte(0x84, 0x01, 0xA3)
            .byte(0x01)
            .byte(contentType.size, body.size)
            .bytes(contentType)
            .bytes(body)
            .build()
        assertEquals("hello", MmsPduParser.parseRetrieveConf(pdu)!!.textBody)
    }

    @Test
    fun `MIBenum lookup covers the encodings handsets actually emit`() {
        assertEquals(Charsets.UTF_8, MmsPduParser.charsetForMib(106))
        assertEquals(Charset.forName("ISO-8859-1"), MmsPduParser.charsetForMib(4))
        assertEquals(Charset.forName("Shift_JIS"), MmsPduParser.charsetForMib(17))
        assertEquals(Charset.forName("windows-1251"), MmsPduParser.charsetForMib(2251))
        assertNull(MmsPduParser.charsetForMib(9999))
    }
}
