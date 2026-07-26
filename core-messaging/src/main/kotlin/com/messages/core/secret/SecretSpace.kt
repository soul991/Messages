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
 *    locked sub-envelope without prompting. This does not weaken the on-device
 *    model: the locked rows live in plaintext in the app-private Room index
 *    anyway (the space gates the UI; the app sandbox + FLAG_SECURE gate the
 *    device). What the KEK protects is the BACKUP on Drive/disk — and there
 *    it never travels; only the credential unlocks a restored envelope.
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
        prefs(context).edit()
            .putString(K_KIND, kind)
            .putString(K_SALT_V, b64(saltV))
            .putString(K_VERIFIER, b64(verifier))
            .putString(K_SALT_K, b64(saltK))
            .putInt(K_ITERATIONS, SecretCrypto.ITERATIONS)
            .putString(K_KEK_LOCAL, LocalKeyBox.encrypt(kek))
            .putInt(K_FAIL_COUNT, 0)
            .putLong(K_LAST_FAIL, 0L)
            .apply()
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
            saltK(context)?.let { sk ->
                p.edit().putString(K_KEK_LOCAL, LocalKeyBox.encrypt(SecretCrypto.derive(credential, sk, iterations))).apply()
            }
            Attempt.Success
        } else {
            val fails = p.getInt(K_FAIL_COUNT, 0) + 1
            p.edit().putInt(K_FAIL_COUNT, fails).putLong(K_LAST_FAIL, now).apply()
            Attempt.Wrong(fails, SecretCooldown.cooldownMs(fails))
        }
    }

    /** Cached backup KEK (Keystore-decrypted); null before setup / after a wipe. */
    fun kekOrNull(context: Context): ByteArray? =
        prefs(context).getString(K_KEK_LOCAL, null)?.let {
            runCatching { LocalKeyBox.decrypt(it) }.getOrNull()
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
            fun parse(s: String): PendingAuth? = s.split('|').takeIf { it.size == 5 }?.let {
                PendingAuth(it[0], it[1], it[2], it[3].toIntOrNull() ?: return null, it[4])
            }
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
        prefs(context).edit()
            .putString(K_KIND, a.kind)
            .putString(K_SALT_V, a.saltV)
            .putString(K_VERIFIER, a.verifier)
            .putString(K_SALT_K, a.saltK)
            .putInt(K_ITERATIONS, a.iterations)
            .putString(
                K_KEK_LOCAL,
                LocalKeyBox.encrypt(SecretCrypto.derive(credential, unb64(a.saltK), a.iterations)),
            )
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
