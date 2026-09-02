package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-51 guard: what the card is allowed to claim.
 *
 * A summary card is a small feature with an outsized failure mode — it takes
 * the app's own voice and puts it around a number lifted out of a stranger's
 * SMS. So most of what is pinned here is refusal: the messages that get no card
 * at all, the numbers that are not amounts, and the digit runs that are not
 * references. The positive tests only check that the obvious cases still work.
 */
class CardExtractorTest {

    private fun kinds(body: String) =
        CardExtractor.extract(body)?.fields?.map { it.kind } ?: emptyList()

    private fun field(body: String, kind: CardExtractor.FieldKind) =
        CardExtractor.extract(body)?.fields?.firstOrNull { it.kind == kind }

    // ---- eligibility ---------------------------------------------------

    @Test
    fun `a routine bank message is summarised`() {
        assertTrue(
            CardExtractor.eligible(
                Category.TRANSACTIONS, ProtectedLabel.BANK,
                dangerous = false, fraudWarning = false,
            )
        )
        assertTrue(
            CardExtractor.eligible(
                Category.INBOX, ProtectedLabel.DELIVERY,
                dangerous = false, fraudWarning = false,
            )
        )
    }

    @Test
    fun `a message the engine distrusts never gets a card`() {
        // This is the whole safety argument. A scam that arrives with a tidy
        // "₹49,999 — due today" card has borrowed the app's credibility, and
        // the app is the only party in the exchange the user trusts.
        assertFalse(
            "spam must never be summarised",
            CardExtractor.eligible(
                Category.SPAM, ProtectedLabel.BANK,
                dangerous = false, fraudWarning = false,
            )
        )
        assertFalse(
            "a dangerous message must never be summarised, whatever its category",
            CardExtractor.eligible(
                Category.TRANSACTIONS, ProtectedLabel.BANK,
                dangerous = true, fraudWarning = false,
            )
        )
        assertFalse(
            "nor one carrying a fraud warning",
            CardExtractor.eligible(
                Category.TRANSACTIONS, ProtectedLabel.BANK,
                dangerous = false, fraudWarning = true,
            )
        )
        for (category in listOf(Category.BLOCKED, Category.PROMOTIONS, Category.REVIEW)) {
            assertFalse(
                "$category must never be summarised",
                CardExtractor.eligible(
                    category, ProtectedLabel.BANK,
                    dangerous = false, fraudWarning = false,
                )
            )
        }
    }

    @Test
    fun `an ordinary conversation gets no card`() {
        assertFalse(
            CardExtractor.eligible(
                Category.INBOX, ProtectedLabel.NONE,
                dangerous = false, fraudWarning = false,
            )
        )
        // An OTP already has its own copy chip; a card would duplicate it.
        assertFalse(
            CardExtractor.eligible(
                Category.INBOX, ProtectedLabel.OTP,
                dangerous = false, fraudWarning = false,
            )
        )
    }

    // ---- amounts -------------------------------------------------------

    @Test
    fun `a debit names the amount and the direction`() {
        val card = CardExtractor.extract(
            "Rs.1,250.00 debited from A/c XX4321 on 02-08-26. Avl Bal Rs.9,870.55"
        )
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals("1250.00", amount.normalized)
        assertEquals(CardExtractor.Confidence.HIGH, amount.confidence)
        assertEquals(CardExtractor.Direction.DEBIT, amount.direction)
        assertEquals(CardExtractor.Direction.DEBIT, card.direction)
        assertTrue(amount.explanation.contains("out"))
        // The balance is a different fact and must not be reported as a
        // transaction — "you spent ₹9,870" is a distinct and alarming claim.
        val balance = card.fields.first { it.kind == CardExtractor.FieldKind.BALANCE }
        assertEquals("9870.55", balance.normalized)
        assertEquals(CardExtractor.Direction.NEUTRAL, balance.direction)
    }

    @Test
    fun `a credit is not reported as money out`() {
        val card = CardExtractor.extract("INR 500 credited to your account")
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertNotNull(amount)
        assertEquals(CardExtractor.Direction.CREDIT, amount.direction)
        assertEquals(CardExtractor.Direction.CREDIT, card.direction)
        assertTrue(amount.explanation.contains("in"))
    }

    @Test
    fun `a credit message with txn id is correctly identified as credit`() {
        val body = "Airtel Payments Bank a/c is credited with Rs.100.00. Txn ID: 688846141173. Call 180023400 for help"
        val card = CardExtractor.extract(body)
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals("100.00", amount.normalized)
        assertEquals(CardExtractor.Confidence.HIGH, amount.confidence)
        assertEquals(CardExtractor.Direction.CREDIT, amount.direction)
        assertEquals(CardExtractor.Direction.CREDIT, card.direction)
        assertTrue("Expected credit explanation to contain 'in', got: ${amount.explanation}", amount.explanation.contains("in"))

        val ref = card.fields.first { it.kind == CardExtractor.FieldKind.REFERENCE }
        assertEquals("688846141173", ref.normalized)
    }

    @Test
    fun `a debit message with txn id and balance is correctly identified as debit`() {
        val body = "Rs. 500.00 debited from Airtel Payments Bank a/c Txn ID 661101059117 Bal:912.48 Call 180023400 for help"
        val card = CardExtractor.extract(body)
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals("500.00", amount.normalized)
        assertEquals(CardExtractor.Confidence.HIGH, amount.confidence)
        assertEquals(CardExtractor.Direction.DEBIT, amount.direction)
        assertEquals(CardExtractor.Direction.DEBIT, card.direction)
        assertTrue(amount.explanation.contains("out"))

        val ref = card.fields.first { it.kind == CardExtractor.FieldKind.REFERENCE }
        assertEquals("661101059117", ref.normalized)
    }

    @Test
    fun `credit card debit does not trigger credit direction`() {
        val card = CardExtractor.extract("Rs.1,500.00 debited towards SBI credit card bill")
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals(CardExtractor.Direction.DEBIT, amount.direction)
        assertTrue(amount.explanation.contains("out"))
    }

    @Test
    fun `refund for purchase is reported as credit`() {
        val card = CardExtractor.extract("Refund of Rs.500.00 for your purchase")
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals(CardExtractor.Direction.CREDIT, amount.direction)
        assertTrue(amount.explanation.contains("in"))
    }

    @Test
    fun `cashback on bill payment is reported as credit`() {
        val card = CardExtractor.extract("Cashback of Rs.50.00 on electricity bill payment")
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals(CardExtractor.Direction.CREDIT, amount.direction)
        assertTrue(amount.explanation.contains("in"))
    }

    @Test
    fun `payment received is reported as credit`() {
        val card = CardExtractor.extract("Payment of Rs.250.00 received from Alex")
        assertNotNull(card)
        val amount = card!!.fields.first { it.kind == CardExtractor.FieldKind.AMOUNT }
        assertEquals(CardExtractor.Direction.CREDIT, amount.direction)
        assertTrue(amount.explanation.contains("in"))
    }

    @Test
    fun `an amount with no context is admitted as uncertain rather than guessed`() {
        val amount = field("Your plan includes $45.99 of value", CardExtractor.FieldKind.AMOUNT)
        assertNotNull(amount)
        assertEquals(CardExtractor.Confidence.MEDIUM, amount!!.confidence)
        assertEquals("45.99", amount.normalized)
    }

    @Test
    fun `a bare number is never an amount`() {
        // The single most damaging thing this could do is call an OTP, a PIN or
        // a reference number a sum of money. Currency is mandatory.
        assertFalse(kinds("Your code is 4321, valid 10 min")
            .contains(CardExtractor.FieldKind.AMOUNT))
        assertFalse(kinds("Order 998877 confirmed").contains(CardExtractor.FieldKind.AMOUNT))
    }

    @Test
    fun `a currency abbreviation inside a word is not a currency`() {
        // Without a boundary, "cars 500" reads as five hundred rupees.
        assertFalse(kinds("We have cars 500 in stock").contains(CardExtractor.FieldKind.AMOUNT))
        assertFalse(kinds("Winners 200 announced").contains(CardExtractor.FieldKind.AMOUNT))
    }

    @Test
    fun `malformed digit groups are rejected rather than silently reflowed`() {
        assertEquals("1250", CardExtractor.groupedNumber("1,250"))
        assertEquals("123456", CardExtractor.groupedNumber("1,23,456"))
        assertEquals("1234567", CardExtractor.groupedNumber("1,234,567"))
        assertEquals("500", CardExtractor.groupedNumber("500"))
        assertEquals("500.75", CardExtractor.groupedNumber("500.75"))
        // A group of the wrong width is a version string or a serial, not money.
        assertNull(CardExtractor.groupedNumber("1,23456"))
        assertNull(CardExtractor.groupedNumber("12,3"))
        assertNull(CardExtractor.groupedNumber("1,,2"))
        assertNull(CardExtractor.groupedNumber("500,"))
    }

    // ---- account tails --------------------------------------------------

    @Test
    fun `only the printed tail is taken`() {
        val tail = field("Card ending 5678 used at ACME", CardExtractor.FieldKind.ACCOUNT_TAIL)
        assertNotNull(tail)
        assertEquals("5678", tail!!.normalized)
        // Whatever the message printed, the stored value is the tail alone —
        // nothing here ever reconstructs or joins a full account number.
        assertTrue(tail.normalized!!.length <= 6)
    }

    // ---- due dates ------------------------------------------------------

    @Test
    fun `a date only counts when a deadline word points at it`() {
        assertTrue(kinds("Bill of Rs.999 due by 15-08-2026")
            .contains(CardExtractor.FieldKind.DUE_DATE))
        // A receipt's transaction date is not a deadline, and putting it on a
        // card under "Due" would invent an obligation.
        assertFalse(kinds("Rs.250 debited on 02-08-2026")
            .contains(CardExtractor.FieldKind.DUE_DATE))
    }

    @Test
    fun `a due date is never turned into a timestamp`() {
        val due = field("Payment due by 5th Aug", CardExtractor.FieldKind.DUE_DATE)
        assertNotNull(due)
        assertEquals("5th Aug", due!!.raw)
        assertNull(
            "the year is not in the message, so the card must not invent one",
            due.normalized,
        )
    }

    // ---- references and tracking ----------------------------------------

    @Test
    fun `a reference must be labelled by the message`() {
        val ref = field("Txn ID 884422019 successful", CardExtractor.FieldKind.REFERENCE)
        assertNotNull(ref)
        assertEquals("884422019", ref!!.normalized)
        // An unlabelled digit run is not a reference. Every transactional SMS
        // has several, and picking one is a coin flip presented as a fact.
        assertFalse(kinds("Thanks. 884422019")
            .contains(CardExtractor.FieldKind.REFERENCE))
    }

    @Test
    fun `a label followed by a word is not an identifier`() {
        assertFalse(kinds("Your order shipped safely")
            .contains(CardExtractor.FieldKind.REFERENCE))
    }

    @Test
    fun `delivery status is quoted rather than mapped`() {
        val status = field(
            "Your shipment AWB 7712345678 is out for delivery today",
            CardExtractor.FieldKind.DELIVERY_STATUS,
        )
        assertNotNull(status)
        assertEquals("out for delivery", status!!.normalized)
        assertEquals("7712345678", field(
            "Your shipment AWB 7712345678 is out for delivery today",
            CardExtractor.FieldKind.TRACKING,
        )?.normalized)
    }

    // ---- structure ------------------------------------------------------

    @Test
    fun `fields point back at the text they came from`() {
        val body = "Rs.1,250.00 debited from A/c XX4321"
        val card = CardExtractor.extract(body)!!
        for (f in card.fields) {
            assertTrue("${f.kind} span out of range", f.start >= 0 && f.end <= body.length)
            assertTrue("${f.kind} span inverted", f.start < f.end)
            assertEquals(
                "${f.kind} raw text must be exactly what the span covers",
                f.raw, body.substring(f.start, f.end).trim(),
            )
        }
        // Reading order matches the message's, so the card and the body agree.
        assertEquals(card.fields.map { it.start }.sorted(), card.fields.map { it.start })
    }

    @Test
    fun `overlapping claims resolve to one field`() {
        val card = CardExtractor.extract("Ref 12345678 A/c XX1234 debited Rs.10")!!
        for (a in card.fields) {
            for (b in card.fields) {
                if (a === b) continue
                assertFalse(
                    "${a.kind} and ${b.kind} both claim the same characters",
                    a.start < b.end && b.start < a.end,
                )
            }
        }
    }

    @Test
    fun `a message with nothing to summarise gets no card`() {
        assertNull(CardExtractor.extract("See you at 8"))
        assertNull(CardExtractor.extract(""))
    }

    @Test
    fun `an oversized body is refused rather than scanned`() {
        val huge = "Rs.100 debited. ".repeat(400)
        assertTrue(huge.length > CardExtractor.MAX_BODY_CHARS)
        assertNull(
            "past a plausible SMS length this is a newsletter, and a card of " +
                "coincidences is worse than none",
            CardExtractor.extract(huge),
        )
    }

    @Test
    fun `extraction survives a hostile body without hanging`() {
        // Not a timing assertion — just that a pathological body returns
        // instead of throwing, so one bad SMS cannot take the chat down.
        val nasty = "Rs." + "1,".repeat(300) + "000 " + "(".repeat(200) + "due by 01-01-2027"
        CardExtractor.extract(nasty.take(CardExtractor.MAX_BODY_CHARS))
    }

    @Test
    fun `the version is recorded on every card`() {
        val card = CardExtractor.extract("Rs.10 debited")!!
        assertEquals(CardExtractor.VERSION, card.version)
    }
}
