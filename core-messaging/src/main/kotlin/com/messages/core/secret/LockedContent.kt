package com.messages.core.secret

import android.content.Context
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * V2-6 — encryption at rest for locked-space message text.
 *
 * ## What this changes
 *
 * Before this existed, the locked space was concealment and nothing more: the
 * credential gated the UI, but the message bodies sat in the app's Room index
 * as ordinary TEXT columns, and a second copy sat in the Telephony provider
 * where any app holding the SMS role could read it. Two facts followed that the
 * feature's own copy did not admit: a rooted phone, a forensic image, or a
 * privileged app read locked chats without ever meeting the credential.
 *
 * Now the text of a locked row is sealed with AES-256-GCM under a per-install
 * content key that never leaves the Android Keystore in the clear, and the
 * Telephony copy of a locked message is deleted when it enters the space. What
 * remains readable off-device is stated plainly in the setup disclaimer and in
 * [the residual list][open] below rather than being left to inference.
 *
 * ## Why a marker prefix instead of a schema column
 *
 * Sealed values carry [MARKER]. That makes [openText] safe to run over *any*
 * value — plaintext passes through untouched — which is what lets the ~40 read
 * paths that can surface a locked row be fixed by one unconditional call each
 * rather than by each of them first working out which space the row belongs to.
 * It also means the seal-in-place backlog repair can run row by row and be
 * interrupted at any point without leaving the table in a mixed state that
 * anything has to reason about.
 *
 * ## Why a content key rather than the credential
 *
 * Deriving from the secret code would mean every read needs the credential in
 * memory, and a credential change would have to re-encrypt every locked row.
 * The Keystore key is bound to the device instead, which is the property that
 * actually matters here: the threat is an attacker holding the *storage* (a
 * pulled image, a backup, a stolen disk), and Keystore material does not travel
 * with it. An attacker executing code as this app on this unlocked device can
 * still ask the Keystore to decrypt — that is a bound of the platform, not of
 * this design, and it is documented rather than papered over.
 *
 * ## What is deliberately NOT sealed
 *
 *  - **`address`** — the correspondent's number. It is the join key for thread
 *    routing, contact resolution, sender reputation, group-recipient splitting,
 *    duplicate detection and the provider-row mapping. Sealing it would either
 *    break all of those or require a deterministic (and therefore
 *    ECB-equivalent, therefore pointless) encryption of it.
 *  - **Timestamps, read/sent state, category and protection labels** — indexed
 *    and ordered on directly.
 *  - **Attachment files.** `mediaUri` points at an app-private file that is not
 *    encrypted here.
 *
 * So this hides *what was said*, not *who said it or when*. Both halves of that
 * are on screen in the setup disclaimer.
 *
 * ## Failure behaviour
 *
 * Unlike [LocalKeyBox], this does **not** fail closed by throwing, and the
 * reason is that the two are protecting different things. Losing a wrapped KEK
 * costs nothing — it is re-derived on the next unlock. Refusing to store an
 * arriving SMS because the Keystore is unavailable would drop the user's mail.
 * So when no content key can be obtained, [sealText] returns the plaintext
 * unchanged: the row is stored exactly as every locked row was stored before
 * this class existed, and [available] reports false so the disclaimer can say
 * so. That is a degradation to the previous behaviour, never below it.
 *
 * Symmetrically, [openText] returns the stored value unchanged when it cannot
 * decrypt. Showing an opaque blob is bad; destroying the only copy of a
 * message because the Keystore was briefly unhappy is worse, and the blob stays
 * decryptable if the key comes back.
 */
object LockedContent {

    /**
     * Sealed-value marker. SOH is used rather than a printable prefix because a
     * real SMS body may legitimately begin with any printable string, and a
     * false positive here means [openText] would try (and harmlessly fail) to
     * decrypt a user's actual message. Control characters below 0x20 other than
     * tab/newline do not occur in GSM-7 or UTF-16 SMS text. NUL is avoided
     * because it terminates strings in parts of the SQLite C API.
     */
    const val MARKER = "\u0001lc1:"

    /** AAD labels. Binding the field name stops a body ciphertext being moved
     *  into the preview column (or vice versa) by anyone editing the database. */
    private const val F_BODY = "body"
    private const val F_NORMALIZED = "normalizedBody"
    private const val F_PREVIEW = "lastMessage"

    private const val PREFS = "locked_content"
    private const val K_CONTENT_KEY = "content_key"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32

    private val rng = SecureRandom()
    private val lock = Any()

    @Volatile
    private var cached: ByteArray? = null

    /** True when locked text written from now on will actually be encrypted. */
    fun available(context: Context): Boolean = keyOrNull(context) != null

    fun isSealed(text: String): Boolean = text.startsWith(MARKER)

    // ---- Text ------------------------------------------------------------

    fun sealText(context: Context, field: String, plain: String): String {
        if (plain.isEmpty() || isSealed(plain)) return plain
        val key = keyOrNull(context) ?: return plain
        return try {
            val nonce = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(field.toByteArray(Charsets.UTF_8))
            val out = nonce + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            MARKER + Base64.getEncoder().encodeToString(out)
        } catch (_: Throwable) {
            plain
        }
    }

    fun openText(context: Context, field: String, stored: String): String {
        if (!isSealed(stored)) return stored
        val key = keyOrNull(context) ?: return stored
        return try {
            val all = Base64.getDecoder().decode(stored.substring(MARKER.length))
            // Bound-before-use: a truncated value must not reach copyOfRange.
            if (all.size <= NONCE_BYTES) return stored
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, all.copyOfRange(0, NONCE_BYTES)),
            )
            cipher.updateAAD(field.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(all, NONCE_BYTES, all.size - NONCE_BYTES), Charsets.UTF_8)
        } catch (_: Throwable) {
            stored
        }
    }

    // ---- Entities --------------------------------------------------------

    /**
     * Seal a message row **if it belongs to the locked space**. Anything else
     * is returned untouched, so this is safe to apply to every write path
     * without each one first deciding whether it is dealing with a locked row.
     */
    fun seal(context: Context, message: MessageEntity): MessageEntity =
        if (message.space != Spaces.LOCKED) {
            message
        } else {
            message.copy(
                body = sealText(context, F_BODY, message.body),
                // The FTS4 mirror is external-content over exactly these two
                // columns, so sealing them is also what stops the locked space's
                // plaintext living on in the search index. Sealing rather than
                // blanking: `rowsNeedingNormalization()` selects on
                // `normalizedBody = ''` and would otherwise re-normalise these
                // rows forever.
                normalizedBody = sealText(context, F_NORMALIZED, message.normalizedBody),
            )
        }

    /** Reveal a message row. A no-op for anything not carrying [MARKER]. */
    fun open(context: Context, message: MessageEntity): MessageEntity =
        if (!isSealed(message.body) && !isSealed(message.normalizedBody)) {
            message
        } else {
            message.copy(
                body = openText(context, F_BODY, message.body),
                normalizedBody = openText(context, F_NORMALIZED, message.normalizedBody),
            )
        }

    fun open(context: Context, messages: List<MessageEntity>): List<MessageEntity> =
        messages.map { open(context, it) }

    /** Seal a conversation's preview snippet if the conversation is locked. */
    fun seal(context: Context, conversation: ConversationEntity): ConversationEntity =
        if (conversation.space != Spaces.LOCKED) {
            conversation
        } else {
            conversation.copy(lastMessage = sealText(context, F_PREVIEW, conversation.lastMessage))
        }

    fun open(context: Context, conversation: ConversationEntity): ConversationEntity =
        if (!isSealed(conversation.lastMessage)) {
            conversation
        } else {
            conversation.copy(lastMessage = openText(context, F_PREVIEW, conversation.lastMessage))
        }

    @JvmName("openConversations")
    fun open(context: Context, conversations: List<ConversationEntity>): List<ConversationEntity> =
        conversations.map { open(context, it) }

    // ---- Already in the right form? --------------------------------------

    /**
     * True when the row is already stored the way its space requires: sealed
     * in the locked space, plain everywhere else.
     *
     * Callers that repair rows in bulk need this because sealing is
     * *randomised* — every seal draws a fresh nonce, so re-sealing a row that
     * was already sealed yields different bytes. Comparing a row against its
     * re-encoded self therefore reports "changed" forever, and a repair pass
     * written that way rewrites the entire locked space on every cold start,
     * re-firing the FTS triggers for every row each time.
     *
     * Empty text is treated as already correct. [sealText] leaves `""` alone —
     * an MMS carrying only an attachment has no body — so requiring a marker
     * on empty fields would make the repair loop forever on those rows.
     */
    fun isCanonical(message: MessageEntity): Boolean {
        val wantSealed = message.space == Spaces.LOCKED
        return fieldIsCanonical(message.body, wantSealed) &&
            fieldIsCanonical(message.normalizedBody, wantSealed)
    }

    fun isCanonical(conversation: ConversationEntity): Boolean =
        fieldIsCanonical(conversation.lastMessage, conversation.space == Spaces.LOCKED)

    private fun fieldIsCanonical(text: String, wantSealed: Boolean): Boolean =
        text.isEmpty() || isSealed(text) == wantSealed

    // ---- Key -------------------------------------------------------------

    /**
     * The per-install content key, or null when the Keystore will not hold one.
     *
     * A failed *unwrap* deliberately does not mint a replacement: doing so would
     * turn a transient Keystore fault into permanent loss of every locked
     * message, and the wrapped key is not re-derivable from anything.
     */
    private fun keyOrNull(context: Context): ByteArray? {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(K_CONTENT_KEY, null)
            if (stored != null) {
                if (LocalKeyBox.isUnprotected(stored)) return null
                val raw = try {
                    LocalKeyBox.decrypt(stored)
                } catch (_: Throwable) {
                    return null
                }
                if (raw.size != KEY_BYTES) return null
                cached = raw
                return raw
            }
            val fresh = ByteArray(KEY_BYTES).also { rng.nextBytes(it) }
            val wrapped = try {
                LocalKeyBox.encrypt(fresh)
            } catch (_: Throwable) {
                return null
            }
            // commit(), not apply(): the very next statement starts producing
            // ciphertext under this key. A process death between the two would
            // otherwise leave unreadable rows behind.
            if (!prefs.edit().putString(K_CONTENT_KEY, wrapped).commit()) return null
            cached = fresh
            return fresh
        }
    }

    /**
     * Secret-space reset. The locked rows are hard-deleted by the caller; this
     * drops the key they were sealed with so no residue is decryptable even if
     * a row survived somewhere the delete could not reach.
     */
    fun destroyKey(context: Context) {
        synchronized(lock) {
            cached = null
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(K_CONTENT_KEY).commit()
        }
    }

    /** Tests only: forget the cached key so a fresh key box takes effect. */
    internal fun resetCacheForTests() {
        synchronized(lock) { cached = null }
    }
}
