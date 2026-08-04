package com.messages.core.backup

import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.AEADBadTagException

/**
 * Backup serializer round-trip regression tests (pure JVM, synthetic
 * fixtures, no device): known input strings must never appear as raw bytes
 * anywhere in a sealed backup; the correct key must return every input
 * exactly; a wrong key must fail hard with nothing partial; and the locked
 * space's sub-envelope (separate PBKDF2 KEK derivation, wrapWithKek →
 * openWithPassword) must satisfy all of the same properties.
 *
 * Scope: DriveBackup (:app) itself is bound to GoogleSignIn/HTTP/Keystore
 * and can't run on plain JVM, but its entire serialization contract is
 * BackupCrypto.seal + wrapWithMasterKey over BackupManager's JSON — exactly
 * what these tests pin. The nested locked path mirrors
 * BackupManager.lockedEnvelope.
 */

// Markers are ≥16 UTF-8 bytes so a chance collision with ciphertext or
// base64 text is negligible, and adversarial for JSON/gzip/pipe handling.
private const val MARKER_BODY_QUOTES = """MARKER_BODY_QUOTES_"double"_'single'_7f3a9c"""
private const val MARKER_BODY_UNICODE = "MARKER_BODY_UNICODE_π漢字🚀🔐_b4e1d2"
private const val MARKER_BODY_NEWLINE = "MARKER_NEWLINE_line1\nline2\ttab_9a01"
private const val MARKER_ADDRESS = "+911234509876MKR"
private const val MARKER_RULE_PATTERN = """MARKER_RULE_(?i)free\s+prize_c3d4"""
private const val MARKER_PATTERN_PACK = "MARKER_PACK_IMPORTED_PATTERNS_e5f6"
private const val MARKER_MEDIA_VALUE = "MARKER_MEDIA_BYTES_CONTENT_a7b8"

// Locked-space markers are disjoint from the outer set so a leak assertion
// failure is attributable to the level it escaped from.
private const val LOCKED_BODY = "LOCKED_BODY_secret💀affair_d9e0"
private const val LOCKED_BODY_PIPES = "LOCKED_PIPES_a|b|c_pipes_survive_f1a2"
private const val LOCKED_ADDRESS = "+919999888877LKD"

private val OUTER_MARKERS = listOf(
    MARKER_BODY_QUOTES, MARKER_BODY_UNICODE, MARKER_BODY_NEWLINE,
    MARKER_ADDRESS, MARKER_RULE_PATTERN, MARKER_PATTERN_PACK, MARKER_MEDIA_VALUE,
)
private val LOCKED_MARKERS = listOf(LOCKED_BODY, LOCKED_BODY_PIPES, LOCKED_ADDRESS)

// wrapWithKek carries its iteration count in the header and
// unwrapWithPassword honors it, so the genuine restore path runs at a
// test-friendly cost (same convention as SecretCredentialTest). Production
// seals the locked envelope through this same KEK path.
// R-19 places a hard 100k floor under any iteration count read back from a
// backup, so the "fast" fixture value sits exactly at that floor rather than
// below it. Keep in sync with SecretSpace.PendingAuth.MIN_ITERATIONS.
private const val FAST_ITERS = 100_000

/** Naive byte-subsequence scan — blobs here are tiny. Needed because a
 *  String(ISO_8859_1) search only works for ASCII markers; unicode markers
 *  must be searched as their UTF-8 byte encodings. */
private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}

private fun assertNoMarkerBytes(blob: ByteArray, markers: List<String>, where: String) {
    for (m in markers) {
        assertFalse(
            "input string leaked as raw bytes into $where: $m",
            blob.containsBytes(m.toByteArray(Charsets.UTF_8)),
        )
    }
}

private fun message(body: String, address: String = MARKER_ADDRESS) = BackupManager.BackupMessage(
    address = address, body = body, timestamp = 1_700_000_100_000, isOutgoing = false,
    read = true, category = "INBOX", dangerous = false, fraudWarning = false,
    protectedLabel = "NONE", score = 0, matchedPatternIds = "", matchedComboIds = "",
    explanations = "", starred = false,
)

private fun fixtureBackupFile(
    lockedEnvelope: String? = null,
    lockedAuth: String? = null,
) = BackupManager.BackupFile(
    formatVersion = BackupManager.FORMAT_VERSION,
    exportedAtMillis = 1_700_000_200_000,
    sensitivity = "DEFAULT",
    otpAutoDelete = false,
    hidePreviews = true,
    patternLibraryVersion = 1,
    importedPatternPack = MARKER_PATTERN_PACK,
    rules = listOf(BackupManager.BackupRule(0, "BLOCK", "SENDER", MARKER_RULE_PATTERN, "SPAM")),
    reputations = listOf(BackupManager.BackupReputation(MARKER_ADDRESS, -3, 1, 0)),
    conversationPrefs = listOf(
        BackupManager.BackupConversationPrefs(MARKER_ADDRESS, pinned = true, archived = false, muted = true, locked = false),
    ),
    messages = listOf(
        message(MARKER_BODY_QUOTES),
        message(MARKER_BODY_UNICODE).copy(starred = true),
        // Non-default trash/media fields so a field-dropping regression
        // can't hide behind re-filled defaults on decode.
        message(MARKER_BODY_NEWLINE).copy(
            trashed = true, trashedAt = 1_700_000_150_000,
            mediaFileName = "m1.jpg", mediaMimeType = "image/jpeg",
        ),
    ),
    media = mapOf("m1.jpg" to MARKER_MEDIA_VALUE),
    lockedEnvelope = lockedEnvelope,
    lockedAuth = lockedAuth,
)

private fun fixtureLockedPayload() = BackupManager.LockedPayload(
    messages = listOf(
        message(LOCKED_BODY, LOCKED_ADDRESS),
        message(LOCKED_BODY_PIPES, LOCKED_ADDRESS).copy(isOutgoing = true),
    ),
    conversationPrefs = listOf(
        BackupManager.BackupConversationPrefs(LOCKED_ADDRESS, pinned = false, archived = false, muted = false, locked = false),
    ),
    lockedAddresses = listOf(LOCKED_ADDRESS),
)

private fun seal(payloadJson: String, dataKey: ByteArray, wrap: BackupCrypto.WrappedKey) =
    BackupCrypto.seal(
        payloadJson = payloadJson,
        dataKey = dataKey,
        wrappedKeys = listOf(wrap),
        createdAt = 1_700_000_300_000,
        checkpointAt = 1_700_000_000_000,
        deviceModel = "TestDevice",
        messageCount = 3,
    )

private fun headerLen(blob: ByteArray): Int = ByteBuffer.wrap(blob, 4, 4).int

/** Flip one bit inside the payload ciphertext (past magic+header). */
private fun flipCiphertextBit(blob: ByteArray): ByteArray = blob.copyOf().also {
    it[8 + headerLen(blob) + 20] = (it[8 + headerLen(blob) + 20].toInt() xor 0x01).toByte()
}

/** Outer envelope — the Drive snapshot path (account master key). */
class BackupSerializerRoundTripTest {

    private val json = BackupManager.json // the production serializer config
    private val fixture = fixtureBackupFile()
    private val masterKey = BackupCrypto.newMasterKey()
    private val dataKey = BackupCrypto.newDataKey()
    private val blob = seal(
        json.encodeToString(BackupManager.BackupFile.serializer(), fixture),
        dataKey,
        BackupCrypto.wrapWithMasterKey(dataKey, masterKey),
    )

    @Test
    fun `no input string appears as raw bytes anywhere in the sealed blob`() {
        // Whole blob — magic, plaintext header JSON, and ciphertext alike —
        // so a marker leaking into header metadata is caught too.
        assertNoMarkerBytes(blob, OUTER_MARKERS, "the sealed backup blob")
    }

    @Test
    fun `correct master key returns every input string exactly`() {
        val out = BackupCrypto.openWithMasterKey(blob, masterKey)
        val decoded = json.decodeFromString(BackupManager.BackupFile.serializer(), out)
        assertEquals(fixture, decoded) // full data-class equality, every field
        // Belt-and-braces on the decoded VALUES (not the JSON text — quotes
        // and newlines are JSON-escaped there): every marker must be present.
        val decodedStrings = decoded.messages.flatMap { listOf(it.body, it.address) } +
            decoded.rules.map { it.pattern } +
            decoded.reputations.map { it.address } +
            decoded.conversationPrefs.map { it.address } +
            decoded.media.values +
            listOfNotNull(decoded.importedPatternPack)
        for (m in OUTER_MARKERS) {
            assertTrue("missing after round trip: $m", decodedStrings.any { it.contains(m) })
        }
    }

    @Test
    fun `edge-case strings survive gzip and JSON escaping byte-for-byte`() {
        val decoded = json.decodeFromString(
            BackupManager.BackupFile.serializer(),
            BackupCrypto.openWithMasterKey(blob, masterKey),
        )
        assertEquals(MARKER_BODY_QUOTES, decoded.messages[0].body)
        assertEquals(MARKER_BODY_UNICODE, decoded.messages[1].body)
        assertEquals(MARKER_BODY_NEWLINE, decoded.messages[2].body)
    }

    @Test
    fun `wrong data key fails hard with no partial plaintext`() {
        // GCM authenticates before releasing a single plaintext byte, so the
        // throw IS the nothing-partial guarantee.
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.open(blob, BackupCrypto.newDataKey())
        }
    }

    @Test
    fun `wrong master key is rejected before any decryption`() {
        assertThrows(BackupCrypto.WrongMasterKeyException::class.java) {
            BackupCrypto.openWithMasterKey(blob, BackupCrypto.newMasterKey())
        }
    }

    @Test
    fun `a single flipped ciphertext bit fails cleanly even with the correct key`() {
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.open(flipCiphertextBit(blob), dataKey)
        }
    }
}

/** Nested locked-space sub-envelope — the separate KEK derivation path.
 *  Mirrors BackupManager.lockedEnvelope: derive KEK from the credential with
 *  its own salt, wrapWithKek, base64 into BackupFile.lockedEnvelope. */
class LockedEnvelopeRoundTripTest {

    private val json = BackupManager.json
    private val credential = "4711".toCharArray()
    private val saltV = SecretCrypto.newSalt()
    private val saltK = SecretCrypto.newSalt()
    private val kek = SecretCrypto.derive(credential, saltK, FAST_ITERS)
    private val lockedFixture = fixtureLockedPayload()
    private val innerDataKey = BackupCrypto.newDataKey()
    private val innerBlob = seal(
        json.encodeToString(BackupManager.LockedPayload.serializer(), lockedFixture),
        innerDataKey,
        BackupCrypto.wrapWithKek(innerDataKey, kek, saltK, FAST_ITERS),
    )
    private val pendingAuth = SecretSpace.PendingAuth(
        saltV = Base64.getEncoder().encodeToString(saltV),
        verifier = Base64.getEncoder().encodeToString(SecretCrypto.derive(credential, saltV, FAST_ITERS)),
        saltK = Base64.getEncoder().encodeToString(saltK),
        iterations = FAST_ITERS,
        kind = SecretCrypto.KIND_PIN,
    )
    private val outerJson = json.encodeToString(
        BackupManager.BackupFile.serializer(),
        fixtureBackupFile(
            lockedEnvelope = Base64.getEncoder().encodeToString(innerBlob),
            lockedAuth = pendingAuth.serialize(),
        ),
    )
    private val masterKey = BackupCrypto.newMasterKey()
    private val outerDataKey = BackupCrypto.newDataKey()
    private val outerBlob = seal(
        outerJson, outerDataKey, BackupCrypto.wrapWithMasterKey(outerDataKey, masterKey),
    )

    @Test
    fun `locked strings never appear raw at any nesting level`() {
        assertNoMarkerBytes(innerBlob, LOCKED_MARKERS, "the inner locked envelope")
        // In the outer plaintext JSON the locked content travels ONLY as
        // base64 ciphertext — mere account access must not reveal it.
        assertNoMarkerBytes(outerJson.toByteArray(Charsets.UTF_8), LOCKED_MARKERS, "the outer plaintext BackupFile JSON")
        assertNoMarkerBytes(outerBlob, LOCKED_MARKERS, "the outer sealed blob")
    }

    @Test
    fun `full nested restore returns every locked string exactly`() {
        val outer = json.decodeFromString(
            BackupManager.BackupFile.serializer(),
            BackupCrypto.openWithMasterKey(outerBlob, masterKey),
        )
        val envelope = Base64.getDecoder().decode(outer.lockedEnvelope!!)
        // The production restore leg: the original credential alone opens it.
        val innerOut = BackupCrypto.openWithPassword(envelope, credential)
        val decoded = json.decodeFromString(BackupManager.LockedPayload.serializer(), innerOut)
        assertEquals(lockedFixture, decoded)
        for (m in LOCKED_MARKERS) assertTrue("missing after locked round trip: $m", innerOut.contains(m))
        // The pipe-delimited auth leg round-trips despite pipes in payload bodies.
        assertEquals(pendingAuth, SecretSpace.PendingAuth.parse(outer.lockedAuth!!))
    }

    @Test
    fun `wrong credential fails cleanly and yields nothing partial`() {
        // Account access alone still opens the outer envelope (normal chats
        // restorable) but the locked envelope stays sealed.
        val outer = json.decodeFromString(
            BackupManager.BackupFile.serializer(),
            BackupCrypto.openWithMasterKey(outerBlob, masterKey),
        )
        val envelope = Base64.getDecoder().decode(outer.lockedEnvelope!!)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.openWithPassword(envelope, "9999".toCharArray())
        }
    }

    @Test
    fun `wrapWithKek and openWithPassword are symmetric, and the wrong salt's KEK fails`() {
        val header = BackupCrypto.readHeader(innerBlob)
        // Cached-KEK leg (unattended scheduled backup / same-device restore).
        assertTrue(BackupCrypto.unwrapWithKek(header, kek).contentEquals(innerDataKey))
        // Credential leg (fresh device): re-derives from the header's carried
        // salt + iterations — pins that wrapWithKek records exactly what
        // unwrapWithPassword re-derives with.
        assertTrue(BackupCrypto.unwrapWithPassword(header, credential).contentEquals(innerDataKey))
        // The realistic mix-up: a KEK derived from the VERIFIER salt must not
        // open the envelope (salt independence applied to the envelope itself).
        val wrongSaltKek = SecretCrypto.derive(credential, saltV, FAST_ITERS)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.unwrapWithKek(header, wrongSaltKek)
        }
    }

    @Test
    fun `flipped inner ciphertext bit fails even with the right credential`() {
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.openWithPassword(flipCiphertextBit(innerBlob), credential)
        }
    }
}
