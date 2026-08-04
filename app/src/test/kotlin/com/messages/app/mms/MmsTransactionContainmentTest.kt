package com.messages.app.mms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * V2-15: the containment primitives that decide whether an MMS callback is
 * allowed to touch a file. These are the checks standing between a rewritten
 * transaction record and a delete outside `cache/mms`, so they are tested
 * directly rather than through the receivers.
 */
class MmsTransactionContainmentTest {

    @get:Rule
    val temp = TemporaryFolder()

    // --- name safety ---------------------------------------------------------

    @Test
    fun `plain pdu names are accepted`() {
        assertTrue(MmsTransactions.isSafeName("send_42.pdu"))
        assertTrue(MmsTransactions.isSafeName("mms_1730000000000_-12345.pdu"))
    }

    @Test
    fun `traversal and separators are rejected`() {
        assertFalse(MmsTransactions.isSafeName("../secrets.pdu"))
        assertFalse(MmsTransactions.isSafeName("..\\secrets.pdu"))
        assertFalse(MmsTransactions.isSafeName("sub/dir.pdu"))
        assertFalse(MmsTransactions.isSafeName("a/../../b.pdu"))
        assertFalse(MmsTransactions.isSafeName("."))
    }

    @Test
    fun `empty and overlong names are rejected`() {
        assertFalse(MmsTransactions.isSafeName(""))
        assertFalse(MmsTransactions.isSafeName("x".repeat(129) + ".pdu"))
    }

    // --- structural containment ---------------------------------------------

    @Test
    fun `a direct child is contained`() {
        val dir = temp.newFolder("mms")
        assertTrue(MmsTransactions.isContained(dir, File(dir, "send_1.pdu")))
    }

    @Test
    fun `a sibling outside the directory is not contained`() {
        val dir = temp.newFolder("mms")
        val outside = File(temp.root, "other.pdu")
        assertFalse(MmsTransactions.isContained(dir, outside))
    }

    @Test
    fun `a traversal path is not contained after canonicalisation`() {
        val dir = temp.newFolder("mms")
        assertFalse(MmsTransactions.isContained(dir, File(dir, "../escaped.pdu")))
        assertFalse(MmsTransactions.isContained(dir, File(dir, "a/../../escaped.pdu")))
    }

    @Test
    fun `the directory itself is not a contained file`() {
        val dir = temp.newFolder("mms")
        assertFalse(MmsTransactions.isContained(dir, dir))
    }

    @Test
    fun `a sibling directory with the same prefix is not contained`() {
        // Guards the classic startsWith bug: "/tmp/mms" vs "/tmp/mms-evil".
        val dir = temp.newFolder("mms")
        val lookalike = temp.newFolder("mms-evil")
        assertFalse(MmsTransactions.isContained(dir, File(lookalike, "send_1.pdu")))
    }
}
