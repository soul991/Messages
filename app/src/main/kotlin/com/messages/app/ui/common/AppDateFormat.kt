package com.messages.app.ui.common

/**
 * V2-45. The wired-up [LocalizedFormats] the UI calls, and the only place that
 * touches the platform's skeleton-to-pattern resolver.
 *
 * Call these at render time, never into a `private val` — the whole point is
 * that the locale and zone are read when the row is drawn rather than when the
 * class was loaded. The formatters themselves are still cached inside
 * [LocalizedFormats], so calling per row costs a map lookup, not an allocation.
 */
internal object AppDateFormat {

    private val formats = LocalizedFormats { skeleton, locale ->
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    }

    /** "14:30" or "2:30 PM", whichever the locale uses. */
    fun clock(timestamp: Long): String = formats.format(DateSkeletons.CLOCK, timestamp)

    /** "Jan 5" / "5 Jan" — a date within the current year. */
    fun dayMonth(timestamp: Long): String = formats.format(DateSkeletons.DAY_MONTH, timestamp)

    /** "Jan 5, 2024". */
    fun dayMonthYear(timestamp: Long): String =
        formats.format(DateSkeletons.DAY_MONTH_YEAR, timestamp)

    /** "Jan 5, 2024, 14:30". */
    fun dayMonthYearClock(timestamp: Long): String =
        formats.format(DateSkeletons.DAY_MONTH_YEAR_CLOCK, timestamp)

    /** "Fri, Jan 5". */
    fun weekdayDayMonth(timestamp: Long): String =
        formats.format(DateSkeletons.WEEKDAY_DAY_MONTH, timestamp)

    /** "Fri, Jan 5, 2:30 PM". */
    fun weekdayDayMonthClock(timestamp: Long): String =
        formats.format(DateSkeletons.WEEKDAY_DAY_MONTH_CLOCK, timestamp)

    /** "Fri, Jan 5, 2024, 2:30:15 PM" — the only place seconds are shown. */
    fun fullWithSeconds(timestamp: Long): String =
        formats.format(DateSkeletons.WEEKDAY_DAY_MONTH_YEAR_SECONDS, timestamp)

    /**
     * The list-row rule shared by Home, Archived and the locked space: a clock
     * for today, a date for anything older.
     */
    fun listRowStamp(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        return if (DateSkeletons.isSameLocalDay(timestamp, now)) {
            clock(timestamp)
        } else {
            dayMonth(timestamp)
        }
    }
}
