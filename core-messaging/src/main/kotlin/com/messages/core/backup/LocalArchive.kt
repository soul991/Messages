package com.messages.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.messages.core.io.BoundedRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * V2-49: password-encrypted local export/import over the Storage Access
 * Framework — a portable backup with no Google account in the picture.
 *
 * ## What changed, and why the plain export did not survive
 *
 * There was already a local SAF export. It wrote the backup as plain JSON to
 * wherever the picker pointed: every message body, every address, every
 * attachment, in clear text, on shared storage, indexed by whatever else can
 * read that directory, synced by whatever cloud client the user has installed.
 * That is a strictly worse disclosure than the Telephony copy V2-6 went to
 * some trouble to delete, and it was one tap from the Settings screen.
 *
 * So this does not add an encrypted option beside the plaintext one. Offering
 * both would put them one tap apart with equal billing, and the failure mode of
 * choosing wrongly is unrecoverable — a file that has already been written and
 * possibly already synced. **Export is encrypted, full stop.** Import still
 * accepts plain JSON, because files people already have must keep working;
 * that asymmetry is the point, not an oversight.
 *
 * ## Verified before it is called a backup
 *
 * [export] re-reads the finished document through the same SAF URI and
 * decrypts it with the same password before reporting success. That is
 * deliberately not a check of the bytes it just wrote in memory: what matters
 * is whether the object *the provider actually stored* opens — a truncated
 * write, a provider that silently dropped the tail, a full volume. A backup
 * that reports success without that check is a promise made on the strength of
 * a return value.
 *
 * The comparison is over SHA-256 of the payload rather than the payload text,
 * so verification does not need a second full copy of the plaintext alive
 * beside the first.
 *
 * ## Plaintext is never cached
 *
 * The payload exists as an in-memory String and goes straight into the
 * envelope. Nothing is staged in `cacheDir`, no temporary file is written and
 * removed, and the only bytes that reach storage are ciphertext. Password
 * arrays belong to the caller, which zeroes them; this file never copies one
 * into a String, where it could not be zeroed at all.
 *
 * ## Bounds (findings 10-12)
 *
 * Every read is bounded by [RestoreBudget.forDevice] before allocation, the
 * envelope goes through [BackupCrypto.readHeader]'s structural checks, and the
 * decompression ceiling is the device's expanded budget rather than the
 * structural one. A file chosen in a picker is attacker-supplied input in
 * exactly the way a downloaded snapshot is.
 */
object LocalArchive {

    private const val TAG = "LocalArchive"

    /** Same envelope format as a Drive snapshot — the file extension follows it. */
    const val EXTENSION = "mbk"

    /**
     * The MIME type handed to `CreateDocument`. Deliberately generic: an
     * encrypted envelope is not any registered type, and claiming one would
     * invite other apps to open it and fail.
     */
    const val MIME_TYPE = "application/octet-stream"

    /** What an exported archive is called. [stamp] is a sortable timestamp. */
    fun suggestedName(stamp: String): String = "messages-backup-$stamp.$EXTENSION"

    /** Why an export or import stopped, when it stopped for a reason worth naming. */
    class VerificationFailed(message: String) : Exception(message)

    /** What a completed export wrote. Sizes only — never content. */
    data class ExportSummary(val bytesWritten: Long, val messageCount: Int)

    /**
     * Encrypt the whole backup under [password] and write it to [uri], then
     * prove the written document opens before returning success.
     *
     * [password] is not consumed: the caller owns it and must zero it.
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        password: CharArray,
        options: BackupManager.ExportOptions = BackupManager.ExportOptions(),
    ): Result<ExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            // The count comes from the export itself — the number of rows that
            // actually went into this payload. Re-querying the database for it
            // afterwards can disagree with the file (a message can arrive in
            // between), and a summary that does not describe the backup it is
            // summarising is worse than no number.
            var messageCount = 0
            val payload = BackupManager.export(context, options) { _, total -> messageCount = total }
            val payloadDigest = sha256(payload)

            val dataKey = BackupCrypto.newDataKey()
            val blob = try {
                BackupCrypto.seal(
                    payloadJson = payload,
                    dataKey = dataKey,
                    wrappedKeys = listOf(BackupCrypto.wrapWithPassword(dataKey, password)),
                    createdAt = System.currentTimeMillis(),
                    checkpointAt = System.currentTimeMillis(),
                    deviceModel = android.os.Build.MODEL ?: "Android",
                    messageCount = messageCount,
                )
            } finally {
                // The data key has done its job the moment the envelope exists;
                // the copy that matters from here on is the wrapped one inside
                // the header.
                dataKey.fill(0)
            }

            // "wt" truncates. Without it, exporting over an existing larger
            // archive leaves the old tail behind and produces a file that is
            // neither the old backup nor the new one.
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                var offset = 0
                while (offset < blob.size) {
                    val n = minOf(WRITE_CHUNK, blob.size - offset)
                    out.write(blob, offset, n)
                    offset += n
                }
                out.flush()
            } ?: throw VerificationFailed("The chosen location could not be written to")

            verifyWritten(context, uri, password, payloadDigest, blob.size.toLong())
            ExportSummary(bytesWritten = blob.size.toLong(), messageCount = messageCount)
        }.onFailure { e -> Log.w(TAG, "local export failed", e) }
    }

    /**
     * Read [uri] back and check it decrypts to the payload that was exported.
     *
     * Failure here is reported as a failed export, not a warning, and the
     * document is left in place: deleting it would destroy the only evidence of
     * what went wrong, and the user may still be able to open it elsewhere.
     */
    private fun verifyWritten(
        context: Context,
        uri: Uri,
        password: CharArray,
        expectedDigest: ByteArray,
        expectedBytes: Long,
    ) {
        val budget = RestoreBudget.forDevice(context)
        val readBack = BoundedRead.readUri(context, uri, budget.maxExpandedBytes)
            ?: throw VerificationFailed("The backup was written but could not be read back")
        if (readBack.size.toLong() != expectedBytes) {
            throw VerificationFailed(
                "The backup was written but came back a different size — " +
                    "the storage location may be full",
            )
        }
        val roundTrip = try {
            BackupCrypto.openWithPassword(readBack, password, budget.maxExpandedBytes)
        } catch (e: Exception) {
            throw VerificationFailed("The backup was written but could not be opened: ${e.message}")
        }
        if (!MessageDigest.isEqual(sha256(roundTrip), expectedDigest)) {
            throw VerificationFailed("The backup was written but its contents did not match")
        }
    }

    /**
     * Whether [uri] holds an encrypted envelope, read from its first four
     * bytes. Null when the file cannot be opened at all.
     *
     * The import UI needs this *before* it can know whether to ask for a
     * password, and asking for one unconditionally would mean prompting for a
     * password that plain files do not have.
     */
    fun isEncrypted(context: Context, uri: Uri): Boolean? {
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(BackupCrypto.MAGIC_BYTES)
                var read = 0
                while (read < buf.size) {
                    val n = stream.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                buf.copyOf(read)
            }
        }.getOrNull() ?: return null
        return BackupCrypto.looksLikeEnvelope(head)
    }

    /**
     * Import from [uri], encrypted or not.
     *
     * [password] is required for an envelope and ignored for plain JSON. As on
     * the export side the caller owns it and zeroes it.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        password: CharArray?,
    ): Result<BackupManager.ImportStats> = withContext(Dispatchers.IO) {
        val encrypted = isEncrypted(context, uri)
            ?: return@withContext Result.failure(
                VerificationFailed("That file could not be opened"),
            )
        val text = runCatching {
            if (encrypted) {
                val budget = RestoreBudget.forDevice(context)
                requireNotNull(password) { "This backup needs the password it was made with" }
                // The read ceiling is the *expanded* budget even though these
                // bytes are compressed. That is not a mistake: an envelope
                // whose compressed size already exceeds what this device can
                // hold expanded is unrestorable here whatever it contains, and
                // refusing it during the read costs nothing.
                val blob = BoundedRead.readUri(context, uri, budget.maxExpandedBytes)
                    ?: error("That file could not be read")
                BackupCrypto.openWithPassword(blob, password, budget.maxExpandedBytes)
            } else {
                // Unchanged legacy path, bounds and all.
                BackupManager.readBackupText(context, uri)
                    ?: error("That file could not be read")
            }
        }
        val payload = text.getOrElse { return@withContext Result.failure(it) }
        BackupManager.import(context, payload)
    }

    private const val WRITE_CHUNK = 256 * 1024

    private fun sha256(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
}
