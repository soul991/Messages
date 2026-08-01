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
import com.messages.core.io.BoundedRead
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

    /**
     * Rough carrier ceiling for the whole PDU; images are compressed to fit.
     *
     * Public so every path that materialises attachment bytes (including the
     * resend path in ChatViewModel) bounds itself by the SAME number instead of
     * re-deriving one — R-17.
     */
    const val MAX_ATTACHMENT_BYTES = 1_000_000
    private const val MAX_IMAGE_DIMENSION = 1440

    /**
     * R-17: an image may arrive larger than the carrier ceiling because we
     * recompress it — but not arbitrarily larger. Past this the source is
     * rejected without ever being read into the heap.
     */
    private const val MAX_SOURCE_IMAGE_BYTES = 32 * 1024 * 1024

    /**
     * Reads + (for images) recompresses the content Uri into an MMS-ready
     * attachment.
     *
     * R-17: the limit is now enforced BEFORE the bytes are read. The old code
     * called `readBytes()` first and compared afterwards, so a huge (or
     * hostile) content URI was already resident when the check ran. Images are
     * decoded from the stream with `inSampleSize`, so a 100-megapixel source
     * never allocates a full-size bitmap either.
     */
    fun prepareAttachment(context: Context, uri: Uri): MmsPduParser.Attachment? = try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val declared = BoundedRead.declaredSize(resolver, uri)
        val isImage = mime.startsWith("image/")
        when {
            // Early rejection on the provider's own declared length.
            isImage && declared != null && declared > MAX_SOURCE_IMAGE_BYTES -> null
            !isImage && declared != null && declared > MAX_ATTACHMENT_BYTES -> null

            isImage -> {
                val fitsAsIs = mime in SENDABLE_IMAGE_TYPES &&
                    declared != null && declared <= MAX_ATTACHMENT_BYTES
                val asIs = if (!fitsAsIs) null else {
                    // Bounded even so: a provider may under-declare its length.
                    BoundedRead.readUri(context, uri, MAX_ATTACHMENT_BYTES)
                        ?.let { MmsPduParser.Attachment(mime, nameFor(mime), it) }
                }
                asIs ?: compressImage(context, uri)
                    ?.let { MmsPduParser.Attachment("image/jpeg", "image.jpg", it) }
            }

            // Non-image: no transcode is possible, so the ceiling is absolute.
            else -> BoundedRead.readUri(context, uri, MAX_ATTACHMENT_BYTES)
                ?.let { MmsPduParser.Attachment(mime, nameFor(mime), it) }
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

            val dir = MmsTransactions.pduDir(context).apply { mkdirs() }
            val pduName = "send_${entity.id}.pdu"
            val file = File(dir, pduName)
            file.writeBytes(pdu)
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            // #15: the callback carries an opaque one-shot token and nothing
            // else. The message id, the PDU name and the granted URI stay on
            // our side of the handoff — a mutable result extra must never be
            // what tells a receiver which file to delete.
            val token = MmsTransactions.register(
                context, pduName, contentUri, messageId = entity.id,
            )
            val pi = PendingIntent.getBroadcast(
                context, entity.id.toInt(),
                Intent(context, MmsSentReceiver::class.java)
                    .putExtra(MmsTransactions.EXTRA_TOKEN, token),
                // MUTABLE: the platform appends EXTRA_MMS_HTTP_STATUS to the result
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            // R-17: grant to every resolved telephony handler, not just the
            // AOSP package name — OEM stacks live elsewhere. MmsSentReceiver
            // revokes these once the transaction reports back.
            TelephonyGrants.grant(
                context, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val smsManager = com.messages.app.sms.SmsManagers.forSubscription(context, subId)
            smsManager.sendMultimediaMessage(context, contentUri, null, null, pi)
            entity.id
        } catch (_: Exception) {
            repo.onMmsSendResult(entity.id, success = false)
            null
        }
    }

    private val SENDABLE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif")

    /**
     * Downscale to [MAX_IMAGE_DIMENSION], then step JPEG quality until it fits.
     *
     * R-17: bounds are decoded from one stream and the pixels from a second, so
     * the full-resolution image is never materialised — neither as bytes nor as
     * a bitmap. `inJustDecodeBounds` allocates nothing.
     */
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_IMAGE_DIMENSION * 2 ||
                bounds.outHeight / sample > MAX_IMAGE_DIMENSION * 2
            ) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
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
