package com.messages.core.secret

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Secret locked-space state: credential verifier, backup KEK cache, attempt
 * rate limiting, notification behavior, and the pending-restore envelope.
 *
 * Storage rules:
 *  - The credential itself is NEVER stored — only the salted PBKDF2 verifier
 *    ([SecretCrypto]).
 *  - The backup KEK (also credential-derived, separate salt) IS cached
 *    locally, Android-Keystore-encrypted, so scheduled backups can seal the
 *    locked sub-envelope without prompting. What the KEK protects is the
 *    BACKUP on Drive/disk — and there it never travels; only the credential
 *    unlocks a restored envelope. On-device, the row text is protected
 *    separately by [LockedContent] under its own Keystore-wrapped content key,
 *    so caching the KEK does not expose message content: an attacker who
 *    recovers the cached KEK gets the ability to open backups, not the local
 *    rows, and neither key survives leaving the device.
 *  - No recovery path: there is deliberately no way to reset the credential
 *    without knowing it.
 */
object SecretSpace {

    // Attempt outcome for the prompt UI.
    sealed class Attempt {
        data object Success : Attempt()
        /** Wrong credential; [cooldownMs] > 0 means further attempts now wait. */
        data class Wrong(val failCount: Int, val cooldownMs: Long) : Attempt()
        /** Still cooling down — the credential was not even checked. */
        data class Cooldown(val remainingMs: Long) : Attempt()
    }

    const val NOTIFY_GENERIC = "generic"
    const val NOTIFY_OFF = "off"

    private const val PREFS = "secret_space"
    private const val K_KIND = "kind"
    private const val K_SALT_V = "salt_v"
    private const val K_VERIFIER = "verifier"
    private const val K_SALT_K = "salt_k"
    private const val K_ITERATIONS = "iterations"
    private const val K_KEK_LOCAL = "kek_local"
    private const val K_FAIL_COUNT = "fail_count"
    private const val K_LAST_FAIL = "last_fail_at"
    private const val K_NOTIFY = "notify_mode"
    private const val K_WEAK = "credential_weak" // V2-7 nudge; never the credential itself
    private const val K_PENDING = "pending_restore" // carried auth of a not-yet-unlocked restore
    private const val PENDING_BLOB = "secret_pending.mbk"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Locally set up (credential established on THIS device). */
    fun isSetUp(context: Context): Boolean = prefs(context).contains(K_VERIFIER)

    /**
     * A restore brought locked chats whose credential hasn't been entered yet.
     * The prompt verifies against the carried auth; success adopts it locally
     * and imports the pending envelope.
     */
    fun hasPendingRestore(context: Context): Boolean =
        prefs(context).contains(K_PENDING) && pendingBlobFile(context).exists()

    /** Anything to show behind the long-press entry at all? */
    fun exists(context: Context): Boolean = isSetUp(context) || hasPendingRestore(context)

    fun kind(context: Context): String {
        val p = prefs(context)
        if (p.contains(K_KIND)) return p.getString(K_KIND, SecretCrypto.KIND_PIN)!!
        // Pending restore: kind travels with the carried auth.
        return pendingAuth(context)?.kind ?: SecretCrypto.KIND_PIN
    }

    // ---- Setup / change ----

    fun setUp(context: Context, kind: String, credential: CharArray) {
        val saltV = SecretCrypto.newSalt()
        val saltK = SecretCrypto.newSalt()
        val verifier = SecretCrypto.derive(credential, saltV)
        val kek = SecretCrypto.derive(credential, saltK)
        // R-18: the KEK is only ever stored Keystore-wrapped. If the Keystore is
        // unavailable the wrap is skipped entirely — never stored in plaintext.
        // Setup still succeeds: the space works without a cached KEK (only the
        // backup sub-envelope needs it) and `attempt` re-wraps once the Keystore
        // recovers.
        val wrapped = wrapKekOrNull(kek)
        prefs(context).edit()
            .putString(K_KIND, kind)
            .putString(K_SALT_V, b64(saltV))
            .putString(K_VERIFIER, b64(verifier))
            .putString(K_SALT_K, b64(saltK))
            .putInt(K_ITERATIONS, SecretCrypto.ITERATIONS)
            .apply { if (wrapped != null) putString(K_KEK_LOCAL, wrapped) else remove(K_KEK_LOCAL) }
            .putInt(K_FAIL_COUNT, 0)
            .putLong(K_LAST_FAIL, 0L)
            .putBoolean(K_WEAK, SecretCrypto.setupError(kind, credential) != null)
            .apply()
    }

    /**
     * V2-7: does the stored credential fall below the *current* setup floors?
     *
     * Set from the credential itself — at setup, and again on every successful
     * [attempt], which is the only other moment the credential is in hand. Only
     * this boolean is persisted; nothing about the credential's length, kind, or
     * content is written anywhere, so the flag adds no offline-attack signal
     * beyond what the verifier already carries.
     *
     * This drives a one-line nudge in settings. It deliberately does NOT gate
     * entry, expire the credential, or force a change: NIST SP 800-63B-4
     * prohibits forced rotation without evidence of compromise, and locking
     * someone out of their own messages over a policy change would be a far
     * worse outcome than the weak credential. Defaults to false, so a space set
     * up by an older build stays quiet until its owner next unlocks it.
     */
    fun isCredentialWeak(context: Context): Boolean =
        prefs(context).getBoolean(K_WEAK, false)

    /**
     * Keystore-wrap the KEK, or null when the Keystore refuses (R-18). Never
     * falls back to a plaintext encoding; a null result means "no cached KEK",
     * which callers already handle by asking for the credential again.
     */
    private fun wrapKekOrNull(kek: ByteArray): String? =
        try {
            LocalKeyBox.encrypt(kek)
        } catch (_: KeyBoxUnavailableException) {
            null
        }

    /** Change requires the current credential; re-derives verifier AND KEK. */
    fun changeCredential(
        context: Context,
        current: CharArray,
        newKind: String,
        new: CharArray,
    ): Attempt {
        val result = attempt(context, current)
        if (result is Attempt.Success) setUp(context, newKind, new)
        return result
    }

    // ---- Prompt attempts (rate-limited) ----

    fun remainingCooldownMs(context: Context, now: Long = System.currentTimeMillis()): Long {
        val p = prefs(context)
        return SecretCooldown.remainingMs(p.getInt(K_FAIL_COUNT, 0), p.getLong(K_LAST_FAIL, 0L), now)
    }

    fun attempt(
        context: Context,
        credential: CharArray,
        now: Long = System.currentTimeMillis(),
    ): Attempt {
        val p = prefs(context)
        val remaining = remainingCooldownMs(context, now)
        if (remaining > 0) return Attempt.Cooldown(remaining)

        val (saltV, verifier, iterations) = authTriple(context) ?: return Attempt.Wrong(0, 0)
        val ok = SecretCrypto.verify(credential, saltV, verifier, iterations)
        return if (ok) {
            p.edit().putInt(K_FAIL_COUNT, 0).putLong(K_LAST_FAIL, 0L).apply()
            if (hasPendingRestore(context) && !isSetUp(context)) adoptPendingAuth(context, credential)
            // Refresh the KEK cache — cheap, and heals a lost Keystore entry.
            // R-18: this is also the recovery path for a legacy `plain:` value
            // or a Keystore that was briefly unavailable. On failure the cache
            // entry is REMOVED rather than downgraded, so a later read cannot
            // find unprotected key material.
            saltK(context)?.let { sk ->
                val wrapped = wrapKekOrNull(SecretCrypto.derive(credential, sk, iterations))
                p.edit()
                    .apply {
                        if (wrapped != null) putString(K_KEK_LOCAL, wrapped) else remove(K_KEK_LOCAL)
                    }
                    .apply()
            }
            // V2-7: re-judge strength against today's floors. This runs after
            // adoptPendingAuth so a restored space is judged by its own kind,
            // not the default. Only the boolean verdict is stored.
            p.edit()
                .putBoolean(K_WEAK, SecretCrypto.setupError(kind(context), credential) != null)
                .apply()
            Attempt.Success
        } else {
            val fails = p.getInt(K_FAIL_COUNT, 0) + 1
            p.edit().putInt(K_FAIL_COUNT, fails).putLong(K_LAST_FAIL, now).apply()
            Attempt.Wrong(fails, SecretCooldown.cooldownMs(fails))
        }
    }

    /**
     * Cached backup KEK (Keystore-decrypted); null before setup / after a wipe.
     *
     * R-18: a stored value that is not Keystore-wrapped (a legacy `plain:` entry
     * written by an older build) is treated as absent AND purged, so it stops
     * being readable key material at rest. The KEK is credential-derived, so the
     * next successful [attempt] re-derives and re-wraps it.
     */
    fun kekOrNull(context: Context): ByteArray? {
        val stored = prefs(context).getString(K_KEK_LOCAL, null) ?: return null
        if (LocalKeyBox.isUnprotected(stored)) {
            prefs(context).edit().remove(K_KEK_LOCAL).apply()
            return null
        }
        return runCatching { LocalKeyBox.decrypt(stored) }.getOrNull()
    }

    /**
     * Upgrade sweep for R-18: drop any legacy plaintext KEK cache left behind by
     * an older build. Safe to call on every start — the value is re-created,
     * Keystore-wrapped, on the next successful credential attempt.
     */
    fun purgeUnprotectedKekCache(context: Context) {
        val stored = prefs(context).getString(K_KEK_LOCAL, null) ?: return
        if (LocalKeyBox.isUnprotected(stored)) {
            prefs(context).edit().remove(K_KEK_LOCAL).apply()
        }
    }

    fun saltK(context: Context): ByteArray? =
        prefs(context).getString(K_SALT_K, null)?.let(::unb64)
            ?: pendingAuth(context)?.saltK?.let(::unb64)

    fun iterations(context: Context): Int = prefs(context).getInt(
        K_ITERATIONS, pendingAuth(context)?.iterations ?: SecretCrypto.ITERATIONS,
    )

    // ---- Notification behavior inside the locked space ----

    fun notifyMode(context: Context): String =
        prefs(context).getString(K_NOTIFY, NOTIFY_GENERIC)!!

    fun setNotifyMode(context: Context, mode: String) {
        prefs(context).edit().putString(K_NOTIFY, mode).apply()
    }

    // ---- Pending restore (locked envelope waiting for its credential) ----

    /** Carried auth from a backup: verifier/salts serialized as `saltV|verifier|saltK|iterations|kind`. */
    data class PendingAuth(
        val saltV: String, val verifier: String, val saltK: String,
        val iterations: Int, val kind: String,
    ) {
        fun serialize() = "$saltV|$verifier|$saltK|$iterations|$kind"

        companion object {
            /** Hard caps for R-19. The serialized form is 5 short Base64 fields. */
            private const val MAX_SERIALIZED = 1024
            internal const val MIN_ITERATIONS = 100_000
            internal const val MAX_ITERATIONS = 2_000_000
            private const val SALT_BYTES = 16
            private const val VERIFIER_BYTES = 32

            /**
             * R-19: this string arrives inside a restored backup, so every field
             * is untrusted. Validate LENGTH, FIELD COUNT, BASE64 DECODABILITY,
             * EXACT DECODED SIZES, the credential KIND enum and the PBKDF2
             * ITERATION BOUND before storing it — an unbounded iteration count
             * would otherwise be attacker-chosen CPU work executed on every
             * unlock attempt, and malformed Base64 would throw at the
             * derivation boundary instead of here.
             */
            fun parse(s: String): PendingAuth? = runCatching {
                if (s.length > MAX_SERIALIZED) return null
                val f = s.split('|')
                if (f.size != 5) return null
                val iterations = f[3].toIntOrNull() ?: return null
                if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return null
                if (f[4] !in ALLOWED_KINDS) return null
                if (decodedSize(f[0]) != SALT_BYTES) return null
                if (decodedSize(f[1]) != VERIFIER_BYTES) return null
                if (decodedSize(f[2]) != SALT_BYTES) return null
                PendingAuth(f[0], f[1], f[2], iterations, f[4])
            }.getOrNull()

            private val ALLOWED_KINDS = setOf(
                SecretCrypto.KIND_PIN, SecretCrypto.KIND_PATTERN, SecretCrypto.KIND_PASSWORD,
            )

            /** Strict Base64 decode; returns -1 when the field is not valid Base64. */
            private fun decodedSize(field: String): Int =
                runCatching { java.util.Base64.getDecoder().decode(field).size }.getOrElse { -1 }
        }
    }

    fun pendingBlobFile(context: Context): File = File(context.filesDir, PENDING_BLOB)

    /**
     * This device's credential auth state, serialized to travel with a backup
     * (verifier + both salts + iterations + kind). The verifier is a salted
     * 600k-iteration PBKDF2 hash — carrying it is the standard shadow-file
     * trade-off, and the sub-envelope itself is what actually protects the
     * locked content. Null before setup.
     */
    fun authForBackup(context: Context): PendingAuth? {
        val p = prefs(context)
        return PendingAuth(
            saltV = p.getString(K_SALT_V, null) ?: return pendingAuth(context),
            verifier = p.getString(K_VERIFIER, null) ?: return null,
            saltK = p.getString(K_SALT_K, null) ?: return null,
            iterations = p.getInt(K_ITERATIONS, SecretCrypto.ITERATIONS),
            kind = p.getString(K_KIND, SecretCrypto.KIND_PIN)!!,
        )
    }

    fun pendingAuth(context: Context): PendingAuth? =
        prefs(context).getString(K_PENDING, null)?.let(PendingAuth::parse)

    fun storePendingRestore(context: Context, blob: ByteArray, auth: PendingAuth) {
        pendingBlobFile(context).writeBytes(blob)
        prefs(context).edit().putString(K_PENDING, auth.serialize()).apply()
    }

    fun clearPendingRestore(context: Context) {
        pendingBlobFile(context).delete()
        prefs(context).edit().remove(K_PENDING).apply()
    }

    /**
     * RESET: forget everything about the secret space — credential verifier,
     * salts, KEK cache, rate-limit state, notification preference, and any
     * pending restore envelope. The caller wipes the locked rows themselves
     * (MessageRepository.wipeLockedSpace) FIRST; with no KEK and no locked
     * rows, the next backup carries no locked sub-envelope at all. Old
     * backups' envelopes stay sealed under the forgotten credential — a
     * restored app treats them as an (undecryptable) pending state, never as
     * content. There is deliberately no partial reset.
     */
    fun clearAll(context: Context) {
        pendingBlobFile(context).delete()
        prefs(context).edit().clear().apply()
    }

    /** On first successful entry after a fresh-install restore: the carried
     *  auth becomes this device's locked-space credential state. */
    private fun adoptPendingAuth(context: Context, credential: CharArray) {
        val a = pendingAuth(context) ?: return
        val wrapped = wrapKekOrNull(
            SecretCrypto.derive(credential, unb64(a.saltK), a.iterations)
        )
        prefs(context).edit()
            .putString(K_KIND, a.kind)
            .putString(K_SALT_V, a.saltV)
            .putString(K_VERIFIER, a.verifier)
            .putString(K_SALT_K, a.saltK)
            .putInt(K_ITERATIONS, a.iterations)
            .apply { if (wrapped != null) putString(K_KEK_LOCAL, wrapped) else remove(K_KEK_LOCAL) }
            .apply()
    }

    private fun authTriple(context: Context): Triple<ByteArray, ByteArray, Int>? {
        val p = prefs(context)
        val saltV = p.getString(K_SALT_V, null)
        val verifier = p.getString(K_VERIFIER, null)
        if (saltV != null && verifier != null) {
            return Triple(unb64(saltV), unb64(verifier), p.getInt(K_ITERATIONS, SecretCrypto.ITERATIONS))
        }
        val a = pendingAuth(context) ?: return null
        return Triple(unb64(a.saltV), unb64(a.verifier), a.iterations)
    }

    internal fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    internal fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
