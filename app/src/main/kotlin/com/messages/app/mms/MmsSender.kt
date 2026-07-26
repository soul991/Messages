package com.messages.app.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.messages.app.receiver.MmsSentReceiver
import com.messages.core.MessageRepository
import com.messages.core.mms.MmsPduBuilder
import com.messages.core.mms.MmsPduParser
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Outgoing MMS: read the picked attachment, transcode oversized images down to
 * carrier-safe bytes, write the message to the Telephony provider first
 * (never-lose, mirrors the receive path), build the m-send-req PDU and hand it
 * to SmsManager.sendMultimediaMessage. [MmsSentReceiver] finalizes status.
 */
object MmsSender {

    /** Rough carrier ceiling for the whole PDU; images are compressed to fit. */
    private const val MAX_ATTACHMENT_BYTES = 1_000_000
    private const val MAX_IMAGE_DIMENSION = 1440

    /** Reads + (for images) recompresses the content Uri into an MMS-ready attachment. */
    fun prepareAttachment(context: Context, uri: Uri): MmsPduParser.Attachment? = try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        when {
            mime.startsWith("image/") && (raw.size > MAX_ATTACHMENT_BYTES || mime !in SENDABLE_IMAGE_TYPES) ->
                compressImage(raw)?.let { MmsPduParser.Attachment("image/jpeg", "image.jpg", it) }
            raw.size > MAX_ATTACHMENT_BYTES -> null // non-image too large — caller shows an error
            else -> MmsPduParser.Attachment(mime, nameFor(mime), raw)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Store (provider first) + send one MMS. Returns the stored message id, or
     * null when the send couldn't even be handed to the platform (the stored
     * row is then marked FAILED so the user can retry from the bubble).
     */
    suspend fun send(
        context: Context,
        address: String,
        textBody: String,
        attachment: MmsPduParser.Attachment?,
        subId: Int? = null,
        space: String = com.messages.core.db.Spaces.NORMAL,
    ): Long? {
        val repo = MessageRepository.get(context)
        val transactionId = "T${System.currentTimeMillis().toString(16)}"
        val entity = repo.storeOutgoingMms(
            address, textBody, System.currentTimeMillis(), transactionId, attachment, space,
        )
        return try {
            val parts = buildList {
                if (textBody.isNotBlank()) {
                    add(MmsPduBuilder.Part("text/plain", null, textBody.toByteArray(Charsets.UTF_8)))
                }
                if (attachment != null) {
                    add(MmsPduBuilder.Part(attachment.mimeType, attachment.name, attachment.data))
                }
            }
            require(parts.isNotEmpty()) { "empty MMS" }
            // Group MMS is one PDU addressed to every recipient (§8.1).
            val pdu = MmsPduBuilder.buildSendReq(repo.recipientsOf(address), parts, transactionId)

            val dir = File(context.cacheDir, "mms").apply { mkdirs() }
            val file = File(dir, "send_${entity.id}.pdu")
            file.writeBytes(pdu)
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val pi = PendingIntent.getBroadcast(
                context, entity.id.toInt(),
                Intent(context, MmsSentReceiver::class.java)
                    .putExtra("messageId", entity.id)
                    .putExtra("filePath", file.absolutePath),
                // MUTABLE: the platform appends EXTRA_MMS_HTTP_STATUS to the result
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            // The telephony process reads the PDU through this grant.
            context.grantUriPermission(
                "com.android.phone", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val smsManager = context.getSystemService(SmsManager::class.java)
                .let { if (subId != null) it.createForSubscriptionId(subId) else it }
            smsManager.sendMultimediaMessage(context, contentUri, null, null, pi)
            entity.id
        } catch (_: Exception) {
            repo.onMmsSendResult(entity.id, success = false)
            null
        }
    }

    private val SENDABLE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif")

    /** Downscale to [MAX_IMAGE_DIMENSION], then step JPEG quality until it fits. */
    private fun compressImage(raw: ByteArray): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_DIMENSION * 2 ||
            bounds.outHeight / sample > MAX_IMAGE_DIMENSION * 2
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
        val scale = minOf(
            1f,
            MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
            MAX_IMAGE_DIMENSION.toFloat() / bitmap.height,
        )
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true,
        ) else bitmap
        var quality = 90
        var out: ByteArray
        do {
            val buf = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, buf)
            out = buf.toByteArray()
            quality -= 15
        } while (out.size > MAX_ATTACHMENT_BYTES && quality > 15)
        if (out.size <= MAX_ATTACHMENT_BYTES) out else null
    } catch (_: Exception) {
        null
    }

    private fun nameFor(mime: String): String = when {
        mime.startsWith("image/jpeg") -> "image.jpg"
        mime.startsWith("image/png") -> "image.png"
        mime.startsWith("image/gif") -> "image.gif"
        mime.startsWith("video/") -> "video.mp4"
        mime.startsWith("audio/") -> "audio.bin"
        else -> "attachment.bin"
    }
}
