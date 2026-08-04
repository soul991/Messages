package com.messages.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

/**
 * V2-5 / V2-46 — the user-held wrap of the backup master key.
 *
 * The properties worth pinning here are the ones whose failure is silent. A
 * vault that opens with the wrong code, a rotation that reuses a nonce, or an
 * `iterations` field an attacker can rewrite downward all produce a system that
 * looks exactly like a working one right up until it matters.
 */
class MasterKeyVaultTest {

    private val rng = SecureRandom()

    private fun masterKey() = ByteArray(32).also { rng.nextBytes(it) }

    private fun rewrite(vault: ByteArray, field: String, value: JsonPrimitive): ByteArray {
        val obj = Json.parseToJsonElement(vault.toString(Charsets.UTF_8)).jsonObject
        return buildJsonObject {
            obj.forEach { (k, v) -> put(k, if (k == field) value else v) }
        }.toString().toByteArray(Charsets.UTF_8)
    }

    // ---- Round trip ------------------------------------------------------

    @Test
    fun `the right code returns the same master key`() {
        val key = masterKey()
        val code = MasterKeyVault.newRecoveryCode()
        val vault = MasterKeyVault.seal(key, code, MasterKeyVault.METHOD_RECOVERY_CODE, 1_700_000_000_000)
        assertArrayEquals2("round trip must return the original key", key, MasterKeyVault.open(vault, code))
    }

    @Test
    fun `a password vault round trips and reports its method`() {
        val key = masterKey()
        val pw = "correct horse battery staple".toCharArray()
        val vault = MasterKeyVault.seal(key, pw, MasterKeyVault.METHOD_PASSWORD, 1L)
        assertEquals("methodOf must not need the secret", MasterKeyVault.METHOD_PASSWORD, MasterKeyVault.methodOf(vault))
        assertEquals("createdAt must survive sealing", 1L, MasterKeyVault.createdAtOf(vault))
        assertArrayEquals2("password vault must round trip", key, MasterKeyVault.open(vault, pw))
    }

    @Test
    fun `a wrong code is rejected rather than returning garbage`() {
        val vault = MasterKeyVault.seal(
            masterKey(), MasterKeyVault.newRecoveryCode(),
            MasterKeyVault.METHOD_RECOVERY_CODE, 0,
        )
        assertThrows(MasterKeyVault.WrongSecretException::class.java) {
            MasterKeyVault.open(vault, MasterKeyVault.newRecoveryCode())
        }
    }

    // ---- Rotation --------------------------------------------------------

    @Test
    fun `resealing never reuses a salt or a nonce`() {
        // V2-46 asks for rotation "without reusing nonces". Rotation reseals the
        // SAME master key, so a fixed nonce would be a catastrophic (key, nonce)
        // reuse across two ciphertexts of identical plaintext.
        val key = masterKey()
        val salts = mutableSetOf<String>()
        val nonces = mutableSetOf<String>()
        repeat(24) {
            val vault = MasterKeyVault.seal(
                key, MasterKeyVault.newRecoveryCode(),
                MasterKeyVault.METHOD_RECOVERY_CODE, it.toLong(),
            )
            val obj = Json.parseToJsonElement(vault.toString(Charsets.UTF_8)).jsonObject
            salts += obj["salt"].toString()
            nonces += obj["nonce"].toString()
        }
        assertEquals("every seal must mint a fresh salt", 24, salts.size)
        assertEquals("every seal must mint a fresh nonce", 24, nonces.size)
    }

    @Test
    fun `rotation keeps the master key so old snapshots stay readable`() {
        val key = masterKey()
        val first = MasterKeyVault.newRecoveryCode()
        val second = MasterKeyVault.newRecoveryCode()
        val rotated = MasterKeyVault.open(
            MasterKeyVault.seal(
                MasterKeyVault.open(
                    MasterKeyVault.seal(key, first, MasterKeyVault.METHOD_RECOVERY_CODE, 0),
                    first,
                ),
                second, MasterKeyVault.METHOD_PASSWORD, 1,
            ),
            second,
        )
        assertArrayEquals2("rotation must not change the master key", key, rotated)
    }

    // ---- Recovery codes --------------------------------------------------

    @Test
    fun `generated codes are 32 Crockford symbols and never repeat`() {
        val seen = mutableSetOf<String>()
        repeat(200) {
            val code = MasterKeyVault.newRecoveryCode()
            assertEquals("a code is 32 symbols", MasterKeyVault.RECOVERY_CODE_CHARS, code.size)
            assertTrue("a generated code must be well formed", MasterKeyVault.isWellFormedRecoveryCode(code))
            assertTrue("codes must not repeat", seen.add(String(code)))
        }
    }

    @Test
    fun `the alphabet excludes the letters that are misread as digits`() {
        // Crockford's whole point. If I, L, O or U ever appear in a generated
        // code, the transcription folding below becomes ambiguous.
        val alphabet = buildString { repeat(400) { append(MasterKeyVault.newRecoveryCode()) } }
        for (c in "ILOU") {
            assertFalse("the alphabet must not contain '$c'", alphabet.contains(c))
        }
    }

    @Test
    fun `hand transcription slips still open the vault`() {
        val key = masterKey()
        val code = MasterKeyVault.newRecoveryCode()
        val vault = MasterKeyVault.seal(key, code, MasterKeyVault.METHOD_RECOVERY_CODE, 0)

        // What a person actually types back: the grouped form, lower case, with
        // O for 0 and l for 1 — every one of which must still decode.
        val typed = MasterKeyVault.formatForDisplay(code)
            .lowercase()
            .replace('0', 'O')
            .replace('1', 'l')
        val normalized = MasterKeyVault.normalizeRecoveryCode(typed.toCharArray())
        assertArrayEquals2("mistranscribed code must still open", key, MasterKeyVault.open(vault, normalized))
    }

    @Test
    fun `normalization does not invent symbols that were never there`() {
        // Folding I/L/O is a transcription aid, not a spell-checker. A genuinely
        // wrong symbol must stay wrong, or a mistyped code silently becomes a
        // different one and the failure surfaces much later.
        val normalized = MasterKeyVault.normalizeRecoveryCode("ABCD-EFG!".toCharArray())
        assertFalse("a bad symbol must not be corrected", MasterKeyVault.isWellFormedRecoveryCode(normalized))
    }

    @Test
    fun `display grouping is reversible`() {
        val code = MasterKeyVault.newRecoveryCode()
        val shown = MasterKeyVault.formatForDisplay(code)
        assertEquals("8 groups means 7 separators", 7, shown.count { it == '-' })
        assertArrayEquals2(
            "the grouped form must normalize back to the code",
            code.map { it.code.toByte() }.toByteArray(),
            MasterKeyVault.normalizeRecoveryCode(shown.toCharArray()).map { it.code.toByte() }.toByteArray(),
        )
    }

    // ---- Hostile vaults --------------------------------------------------

    @Test
    fun `an iteration downgrade fails the tag instead of cheapening the attack`() {
        // The KDF parameters travel in the clear. Binding them as AAD is what
        // stops an attacker rewriting 600k down to the floor and then brute
        // forcing a password at 6x the speed.
        val key = masterKey()
        val pw = "a long enough passphrase".toCharArray()
        val vault = MasterKeyVault.seal(key, pw, MasterKeyVault.METHOD_PASSWORD, 5)
        val downgraded = rewrite(vault, "iterations", JsonPrimitive(100_000))
        assertThrows(MasterKeyVault.WrongSecretException::class.java) {
            MasterKeyVault.open(downgraded, pw)
        }
    }

    @Test
    fun `an absurd iteration count is refused before any derivation runs`() {
        val vault = MasterKeyVault.seal(
            masterKey(), MasterKeyVault.newRecoveryCode(),
            MasterKeyVault.METHOD_RECOVERY_CODE, 0,
        )
        val bomb = rewrite(vault, "iterations", JsonPrimitive(Int.MAX_VALUE))
        assertThrows(MasterKeyVault.MalformedVaultException::class.java) {
            MasterKeyVault.open(bomb, MasterKeyVault.newRecoveryCode())
        }
    }

    @Test
    fun `structurally impossible vaults are malformed, not wrong-secret`() {
        // The distinction is user-facing: "your code is wrong" tells someone to
        // retype, which is useless advice when the object itself is broken.
        val good = MasterKeyVault.seal(
            masterKey(), MasterKeyVault.newRecoveryCode(),
            MasterKeyVault.METHOD_RECOVERY_CODE, 0,
        )
        val code = MasterKeyVault.newRecoveryCode()
        val cases = mapOf(
            "version" to rewrite(good, "formatVersion", JsonPrimitive(99)),
            "method" to rewrite(good, "method", JsonPrimitive("passkey")),
            "kdf" to rewrite(good, "kdf", JsonPrimitive("scrypt")),
            "short salt" to rewrite(good, "salt", JsonPrimitive(b64(4))),
            "long salt" to rewrite(good, "salt", JsonPrimitive(b64(128))),
            "short nonce" to rewrite(good, "nonce", JsonPrimitive(b64(8))),
            "wrong wrapped size" to rewrite(good, "wrapped", JsonPrimitive(b64(31))),
            "not json" to "not a vault".toByteArray(),
            "empty" to ByteArray(0),
            "oversize" to ByteArray(MasterKeyVault.MAX_VAULT_BYTES + 1) { '{'.code.toByte() },
        )
        for ((name, bytes) in cases) {
            assertThrows(
                "$name must be reported as malformed",
                MasterKeyVault.MalformedVaultException::class.java,
            ) { MasterKeyVault.open(bytes, code) }
        }
    }

    @Test
    fun `a tampered wrapped key is rejected`() {
        val key = masterKey()
        val code = MasterKeyVault.newRecoveryCode()
        val vault = MasterKeyVault.seal(key, code, MasterKeyVault.METHOD_RECOVERY_CODE, 0)
        val obj = Json.parseToJsonElement(vault.toString(Charsets.UTF_8)).jsonObject
        val wrapped = Base64.getDecoder().decode(obj["wrapped"]!!.toString().trim('"'))
        wrapped[0] = (wrapped[0].toInt() xor 0x01).toByte()
        val flipped = rewrite(vault, "wrapped", JsonPrimitive(Base64.getEncoder().encodeToString(wrapped)))
        assertThrows(MasterKeyVault.WrongSecretException::class.java) {
            MasterKeyVault.open(flipped, code)
        }
    }

    @Test
    fun `sealing refuses inputs that would produce a vault worth nothing`() {
        val code = MasterKeyVault.newRecoveryCode()
        assertThrows(IllegalArgumentException::class.java) {
            MasterKeyVault.seal(ByteArray(16), code, MasterKeyVault.METHOD_RECOVERY_CODE, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MasterKeyVault.seal(masterKey(), CharArray(0), MasterKeyVault.METHOD_RECOVERY_CODE, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MasterKeyVault.seal(masterKey(), code, "passkey", 0)
        }
    }

    @Test
    fun `the KDF cost matches the rest of the app`() {
        // One knob, not two. A vault derived at a weaker cost than the snapshot
        // password path would quietly become the cheapest way in.
        assertEquals(
            "vault iterations must track BackupCrypto",
            BackupCrypto.PBKDF2_ITERATIONS, MasterKeyVault.ITERATIONS,
        )
    }

    @Test
    fun `no plaintext key material appears in the sealed object`() {
        val key = masterKey()
        val vault = MasterKeyVault.seal(
            key, MasterKeyVault.newRecoveryCode(),
            MasterKeyVault.METHOD_RECOVERY_CODE, 0,
        )
        val text = vault.toString(Charsets.UTF_8)
        assertFalse(
            "the master key must not be in the vault in the clear",
            text.contains(Base64.getEncoder().encodeToString(key)),
        )
        assertNotEquals("wrapped must not be the bare key", 32, decoded(text, "wrapped"))
    }

    // ---- Helpers ---------------------------------------------------------

    private fun b64(n: Int) = Base64.getEncoder().encodeToString(ByteArray(n))

    private fun decoded(text: String, field: String): Int =
        Base64.getDecoder()
            .decode(Json.parseToJsonElement(text).jsonObject[field]!!.toString().trim('"')).size

    /** JUnit 4's assertArrayEquals with the message first, spelled once. */
    private fun assertArrayEquals2(message: String, expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(message, expected, actual)
}
