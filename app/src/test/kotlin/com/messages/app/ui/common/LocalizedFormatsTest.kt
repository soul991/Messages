package com.messages.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * V2-45. The bug was a cache that never invalidated: formatters built into
 * top-level `val`s at class-init, holding the locale and time zone that were
 * current when the class happened to load.
 *
 * These tests drive the invalidation directly by moving the locale and zone the
 * cache reads, which is exactly what the platform does under a language change
 * or a flight. The pattern resolver is injected so nothing here needs Android.
 */
class LocalizedFormatsTest {

    /** 2024-01-05 14:30:15 UTC. */
    private val ts = 1_704_465_015_000L

    private var locale = Locale.US
    private var zone: TimeZone = TimeZone.getTimeZone("UTC")
    private var patternCalls = 0

    /**
     * Stands in for `DateFormat.getBestDateTimePattern`: concrete enough to
     * produce different visible output per locale, so a stale cache is
     * observable rather than merely countable.
     */
    private val formats = LocalizedFormats(
        localeOf = { locale },
        zoneOf = { zone },
        patternOf = { skeleton, forLocale ->
            patternCalls++
            when {
                skeleton == DateSkeletons.CLOCK && forLocale == Locale.US -> "h:mm a"
                skeleton == DateSkeletons.CLOCK -> "HH:mm"
                forLocale == Locale.US -> "MMM d"
                else -> "d MMM"
            }
        },
    )

    @Test
    fun `a formatter is built once and then reused`() {
        repeat(5) { formats.format(DateSkeletons.CLOCK, ts) }
        assertEquals(1, patternCalls)
        assertEquals(1, formats.cachedCount())
    }

    @Test
    fun `distinct skeletons get distinct cache entries`() {
        formats.format(DateSkeletons.CLOCK, ts)
        formats.format(DateSkeletons.DAY_MONTH, ts)
        assertEquals(2, formats.cachedCount())
    }

    @Test
    fun `a locale change re-renders in the new locale`() {
        val before = formats.format(DateSkeletons.DAY_MONTH, ts)
        assertEquals("Jan 5", before)

        locale = Locale.UK
        val after = formats.format(DateSkeletons.DAY_MONTH, ts)

        // The whole finding in one assertion: same timestamp, new language.
        assertEquals("5 Jan", after)
        assertNotEquals(before, after)
        assertEquals(Locale.UK, formats.cachedFor().first)
    }

    @Test
    fun `a locale change drops every cached formatter, not just the one asked for`() {
        formats.format(DateSkeletons.CLOCK, ts)
        formats.format(DateSkeletons.DAY_MONTH, ts)
        assertEquals(2, formats.cachedCount())

        locale = Locale.UK
        formats.format(DateSkeletons.CLOCK, ts)

        // Had only the requested entry been evicted, DAY_MONTH would still be
        // sitting there in English, waiting for the next row to draw it.
        assertEquals(1, formats.cachedCount())
        assertEquals("5 Jan", formats.format(DateSkeletons.DAY_MONTH, ts))
    }

    @Test
    fun `a time zone change re-renders in the new zone`() {
        // 14:30 UTC is the previous evening in Los Angeles.
        assertEquals("2:30 PM", formats.format(DateSkeletons.CLOCK, ts))

        zone = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals("6:30 AM", formats.format(DateSkeletons.CLOCK, ts))
        assertEquals("America/Los_Angeles", formats.cachedFor().second)
    }

    @Test
    fun `a zone with the same offset but a different id still invalidates`() {
        // Cheap to be safe here: two ids can agree today and diverge at the
        // next DST boundary, and comparing ids costs nothing per row.
        formats.format(DateSkeletons.CLOCK, ts)
        zone = TimeZone.getTimeZone("Etc/GMT")
        formats.format(DateSkeletons.CLOCK, ts)
        assertEquals("Etc/GMT", formats.cachedFor().second)
    }

    @Test
    fun `nothing is rebuilt when neither locale nor zone moved`() {
        formats.format(DateSkeletons.CLOCK, ts)
        formats.format(DateSkeletons.CLOCK, ts + 60_000)
        formats.format(DateSkeletons.CLOCK, ts + 120_000)
        assertEquals(1, patternCalls)
    }

    @Test
    fun `the shared scratch Date does not leak between calls`() {
        val a = formats.format(DateSkeletons.CLOCK, ts)
        val b = formats.format(DateSkeletons.CLOCK, ts + 3_600_000)
        assertEquals("2:30 PM", a)
        assertEquals("3:30 PM", b)
    }

    @Test
    fun `local day boundaries follow the zone`() {
        val utc = TimeZone.getTimeZone("UTC")
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        // 2024-01-05 02:00 UTC is still 2024-01-04 in Los Angeles.
        val early = 1_704_420_000_000L
        assertFalse(DateSkeletons.localDayOf(early, utc) == DateSkeletons.localDayOf(early, la))
        assertTrue(DateSkeletons.isSameLocalDay(early, early + 3_600_000, utc))
    }

    @Test
    fun `local day is correct before the epoch`() {
        val utc = TimeZone.getTimeZone("UTC")
        // 1969-12-31 23:00 and 1970-01-01 01:00 are different days; plain
        // integer division truncates toward zero and would call both day 0.
        val before = -3_600_000L
        val after = 3_600_000L
        assertEquals(-1L, DateSkeletons.localDayOf(before, utc))
        assertEquals(0L, DateSkeletons.localDayOf(after, utc))
        assertFalse(DateSkeletons.isSameLocalDay(before, after, utc))
    }

    @Test
    fun `every skeleton uses the flexible hour field rather than a fixed cycle`() {
        // `j` is what lets ICU pick 12- or 24-hour per locale. `H`/`h` would
        // reintroduce the hardcoding this change removed.
        val withClock = DateSkeletons.ALL.filter { 'j' in it || 'H' in it || 'h' in it }
        assertTrue(withClock.isNotEmpty())
        assertTrue(
            "skeletons must express the hour as 'j': $withClock",
            withClock.all { 'H' !in it && 'h' !in it },
        )
    }
}
