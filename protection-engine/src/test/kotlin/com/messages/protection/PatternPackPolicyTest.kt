package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R-21 — bounds on imported pattern packs, and the standing guarantee that the
 * library we ship satisfies the same bounds we impose on everyone else.
 */
class PatternPackPolicyTest {

    /** Asserts [block] is refused by the pack policy; returns the reason. */
    private fun assertRejected(block: () -> Unit): String {
        try {
            block()
        } catch (e: PatternPackPolicy.Rejected) {
            return e.message.orEmpty()
        }
        fail("expected the pack to be rejected, but it was accepted")
        return ""
    }

    private fun pattern(
        id: String = "p1",
        family: String = Families.PROMO,
        regex: String = "free",
        weight: Int = 5,
        description: String = "d",
        examples: List<String> = emptyList(),
        languages: List<String> = listOf("en"),
    ) = Pattern(id, family, regex, weight, languages, description, examples)

    private fun pack(vararg patterns: Pattern, version: Int = 1) =
        PatternLibrary(version, patterns.toList())

    // ---- the bundled library --------------------------------------------

    /**
     * The bundled pack is loaded WITHOUT validation at runtime (cold-start
     * cost), so this test is what actually holds it to the policy. If it ever
     * fails, either patterns.json regressed or a limit was set too tight —
     * both are release blockers.
     */
    @Test
    fun `bundled pattern library satisfies the import policy`() {
        val text = ProtectionEngine::class.java.getResourceAsStream("/patterns.json")!!
            .bufferedReader().readText()
        assertTrue(
            "bundled patterns.json exceeds MAX_PACK_BYTES",
            text.toByteArray(Charsets.UTF_8).size <= PatternPackPolicy.MAX_PACK_BYTES,
        )
        val matcher = PatternMatcher.fromJson(text, validate = true)
        assertTrue(matcher.library.patterns.isNotEmpty())
    }

    // ---- rejections ------------------------------------------------------

    @Test
    fun `rejects an empty pack`() {
        assertRejected { PatternPackPolicy.validate(pack()) }
    }

    @Test
    fun `rejects too many patterns`() {
        val many = (0..PatternPackPolicy.MAX_PATTERNS).map { pattern(id = "p$it") }
        val message = assertRejected { PatternPackPolicy.validate(PatternLibrary(1, many)) }
        assertTrue("unhelpful message: $message", message.contains("limit is"))
    }

    @Test
    fun `rejects duplicate ids`() {
        val message = assertRejected {
            PatternPackPolicy.validate(pack(pattern(id = "dup"), pattern(id = "dup")))
        }
        assertTrue("unhelpful message: $message", message.contains("Duplicate"))
    }

    @Test
    fun `rejects a catastrophic regex and names the offending pattern`() {
        val message = assertRejected {
            PatternPackPolicy.validate(pack(pattern(id = "evil", regex = "(a+)+")))
        }
        assertTrue("message should name the pattern: $message", message.contains("evil"))
    }

    @Test
    fun `rejects out-of-range weight`() {
        assertRejected {
            PatternPackPolicy.validate(pack(pattern(weight = PatternPackPolicy.MAX_WEIGHT + 1)))
        }
        assertRejected { PatternPackPolicy.validate(pack(pattern(weight = -1))) }
    }

    @Test
    fun `rejects oversized fields`() {
        assertRejected {
            PatternPackPolicy.validate(
                pack(pattern(id = "x".repeat(PatternPackPolicy.MAX_ID_LENGTH + 1)))
            )
        }
        assertRejected {
            PatternPackPolicy.validate(
                pack(pattern(description = "d".repeat(PatternPackPolicy.MAX_DESCRIPTION_LENGTH + 1)))
            )
        }
        assertRejected {
            PatternPackPolicy.validate(
                pack(pattern(examples = List(PatternPackPolicy.MAX_EXAMPLES + 1) { "e" }))
            )
        }
    }

    @Test
    fun `rejects a blank family and a zero version`() {
        assertRejected { PatternPackPolicy.validate(pack(pattern(family = " "))) }
        assertRejected { PatternPackPolicy.validate(pack(pattern(), version = 0)) }
    }

    // ---- acceptance ------------------------------------------------------

    @Test
    fun `accepts a small well-formed pack`() {
        val library = pack(
            pattern(id = "promo-1", regex = "limited time offer"),
            pattern(id = "otp-1", regex = "\\b\\d{4,8}\\b", family = Families.LOTTERY),
        )
        PatternPackPolicy.validate(library)
        assertEquals(2, library.patterns.size)
    }
}
