package com.messages.protection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class CorpusEntry(val label: String, val sender: String, val text: String)

@Serializable
data class Corpus(val entries: List<CorpusEntry>)

/**
 * §7.4 / §11 CI gates over the labeled corpus:
 *  - ZERO protected-family messages filtered (the non-negotiable metric)
 *  - ≥95% of spam/scam caught at default sensitivity
 *  - genuine personal messages stay in the Inbox
 */
class CorpusRegressionTest {

    private val corpus: Corpus by lazy {
        val text = javaClass.getResourceAsStream("/corpus.json")!!.bufferedReader().readText()
        Json { ignoreUnknownKeys = true }.decodeFromString(Corpus.serializer(), text)
    }

    private fun verdict(e: CorpusEntry): Verdict = TestEngine.classify(e.text, e.sender)

    @Test
    fun gate1_zero_protected_messages_filtered() {
        val failures = corpus.entries.filter { it.label == "protected" }.mapNotNull { e ->
            val v = verdict(e)
            if (v.category != Category.INBOX && v.category != Category.TRANSACTIONS)
                "${e.sender}: '${e.text.take(60)}' → ${v.category}" else null
        }
        assertTrue("PROTECTED MESSAGES FILTERED:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun gate2_at_least_95_percent_of_spam_scam_caught() {
        val scamEntries = corpus.entries.filter { it.label == "scam" }
        val missed = scamEntries.filter { e ->
            val v = verdict(e)
            v.category == Category.INBOX || v.category == Category.TRANSACTIONS
        }
        val caught = scamEntries.size - missed.size
        val rate = caught.toDouble() / scamEntries.size
        assertTrue(
            "Catch rate ${"%.1f".format(rate * 100)}% (<95%). Missed:\n" +
                missed.joinToString("\n") { "${it.sender}: ${it.text.take(70)}" },
            rate >= 0.95,
        )
    }

    @Test
    fun gate3_promos_never_reach_inbox_loudly() {
        val failures = corpus.entries.filter { it.label == "promo" }.mapNotNull { e ->
            val v = verdict(e)
            if (v.category == Category.INBOX && v.notify)
                "${e.sender}: '${e.text.take(60)}' → noisy Inbox" else null
        }
        assertTrue("PROMOS NOTIFYING:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun gate4_genuine_messages_stay_in_inbox() {
        val failures = corpus.entries.filter { it.label == "genuine" }.mapNotNull { e ->
            val v = verdict(e)
            // Review is tolerated per §7.7 bias, Spam/Blocked is not
            if (v.category == Category.SPAM || v.category == Category.BLOCKED)
                "${e.sender}: '${e.text.take(60)}' → ${v.category}" else null
        }
        assertTrue("GENUINE MESSAGES SPAMMED:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun gate5_median_classification_under_50ms() {
        val engine = TestEngine.engine()
        // warmup
        corpus.entries.take(20).forEach { e ->
            engine.classify(ProtectionEngine.Input(e.text, SenderAnalyzer.analyze(e.sender, false)))
        }
        val times = corpus.entries.map { e ->
            val start = System.nanoTime()
            engine.classify(ProtectionEngine.Input(e.text, SenderAnalyzer.analyze(e.sender, false)))
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val median = times[times.size / 2]
        assertTrue("Median classification ${median}ms exceeds 50ms", median < 50.0)
    }

    @Test
    fun corpus_is_large_enough() {
        // §7.4 floor; enforce so it only grows
        assertTrue("Corpus shrank to ${corpus.entries.size}", corpus.entries.size >= 500)
    }
}
