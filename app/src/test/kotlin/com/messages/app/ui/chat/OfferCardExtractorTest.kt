package com.messages.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfferCardExtractorTest {

    @Test
    fun `extracts discount, validity, and action url from promo message`() {
        val body = "Big Festive Sale! Get 50% off on all items. Valid till 30 Nov. Shop now at https://flipkart.com/sale"
        val offer = OfferExtractor.extract(body)
        assertNotNull(offer)
        assertEquals("50% OFF", offer?.headline)
        assertEquals("50%", offer?.discount)
        assertEquals("Valid till 30 Nov", offer?.validity)
        assertEquals("https://flipkart.com/sale", offer?.actionUrl)
    }

    @Test
    fun `extracts recharge plan and link from airtel offer`() {
        val body = "Recharge with Rs 299 and get 2GB/day with unlimited calls. Click https://airtel.in/recharge"
        val offer = OfferExtractor.extract(body)
        assertNotNull(offer)
        assertEquals("Recharge ₹299 Plan", offer?.headline)
        assertEquals("₹299", offer?.rechargeAmount)
        assertEquals("Recharge Now", offer?.actionLabel)
        assertEquals("https://airtel.in/recharge", offer?.actionUrl)
    }

    @Test
    fun `extracts coupon code from food delivery message`() {
        val body = "Hungry? Use code SWIGGY50 to get flat 50% discount on your dinner order. Valid today only."
        val offer = OfferExtractor.extract(body)
        assertNotNull(offer)
        assertEquals("SWIGGY50", offer?.couponCode)
        assertEquals("50%", offer?.discount)
        assertEquals("Order Now", offer?.actionLabel)
    }

    @Test
    fun `extracts flat currency discount and quoted coupon code`() {
        val body = "Mega Deals! Flat ₹100 off on order above 499. Use coupon \"SAVE100\". Valid through Sunday."
        val offer = OfferExtractor.extract(body)
        assertNotNull(offer)
        assertEquals("₹100", offer?.discount)
        assertEquals("SAVE100", offer?.couponCode)
        assertEquals("₹100 OFF with Code SAVE100", offer?.headline)
        assertEquals("Valid till Sunday", offer?.validity)
    }

    @Test
    fun `extracts validity with expires or ends phrasing`() {
        val body1 = "Flash sale! 40% off on all shoes. Expires in 2 days. Buy now: https://store.com"
        val offer1 = OfferExtractor.extract(body1)
        assertNotNull(offer1)
        assertEquals("Expires in 2 days", offer1?.validity)

        val body2 = "Super deal! Flat 20% discount. Ends tonight."
        val offer2 = OfferExtractor.extract(body2)
        assertNotNull(offer2)
        assertEquals("Ends tonight", offer2?.validity)
    }

    @Test
    fun `extracts tracking action label`() {
        val body = "Your festive special offer parcel is on the way! Track order at https://track.com/123"
        val offer = OfferExtractor.extract(body)
        assertNotNull(offer)
        assertEquals("Track Order", offer?.actionLabel)
        assertEquals("https://track.com/123", offer?.actionUrl)
    }

    @Test
    fun `returns null for non-promotional message`() {
        val body = "Hey, are we still meeting today at the cafe?"
        val offer = OfferExtractor.extract(body)
        assertNull(offer)
    }
}
