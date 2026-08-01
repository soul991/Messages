package com.messages.app.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V2-45. Every screen kept its date formatters in a top-level `private val`:
 *
 * ```
 * private val rowTimeFormat = SimpleDateFormat("HH:mm", Locale.US)
 * ```
 *
 * Two things are wrong with that, and only one of them is the lint warning.
 *
 * The one lint names (`ConstantLocale`) is that a formatter built at class-init
 * captures whatever locale was active then. Android updates the process default
 * when the user changes language, but it does not rebuild objects that already
 * read it — so a locale change with the process still alive leaves timestamps
 * rendered in the previous language until the app is killed. The same is true
 * of the time zone, which `SimpleDateFormat` also snapshots at construction: fly
 * somewhere, and every row keeps reporting the departure zone's clock.
 *
 * The one lint cannot see is that the patterns were hardcoded to a single
 * language's conventions. `"dd MMM"` is not the way most of the world writes a
 * date, and `"HH:mm"` forces a 24-hour clock on locales that read 12-hour.
 *
 * So this holds *skeletons* rather than patterns — an unordered set of the
 * fields the UI wants — and asks the platform for the best concrete pattern in
 * the locale that is current at render time. `MMMd` becomes "Jan 5" in en-US,
 * "5 Jan" in en-GB, and "1月5日" in ja. The `j` field is the important one for
 * clocks: ICU resolves it to whichever hour cycle the locale actually uses,
 * which is the thing `HH` was overriding.
 *
 * Formatters are still cached and reused — allocating one per row per frame
 * showed up in the fling profile — but the cache is now keyed on the locale and
 * zone it was built for, and drops everything when either moves.
 *
 * This class is deliberately free of Android types: the platform call that turns
 * a skeleton into a pattern is injected, so the invalidation logic is unit
 * testable on the JVM. [AppDateFormat] is the wired-up instance the UI uses.
 */
internal class LocalizedFormats(
    private val localeOf: () -> Locale = { Locale.getDefault() },
    private val zoneOf: () -> TimeZone = { TimeZone.getDefault() },
    private val patternOf: (skeleton: String, locale: Locale) -> String,
) {
    private val lock = Any()
    private val cache = HashMap<String, SimpleDateFormat>()
    private val scratch = Date(0)
    private var locale: Locale? = null
    private var zoneId: String? = null

    /** Renders [timestamp] using the locale's own arrangement of [skeleton]'s fields. */
    fun format(skeleton: String, timestamp: Long): String = synchronized(lock) {
        val currentLocale = localeOf()
        val currentZone = zoneOf()
        if (currentLocale != locale || currentZone.id != zoneId) {
            locale = currentLocale
            zoneId = currentZone.id
            cache.clear()
        }
        val formatter = cache.getOrPut(skeleton) {
            SimpleDateFormat(patternOf(skeleton, currentLocale), currentLocale)
                .apply { timeZone = currentZone }
        }
        scratch.time = timestamp
        formatter.format(scratch)
    }

    /** Test seam: how many formatters survive in the cache right now. */
    fun cachedCount(): Int = synchronized(lock) { cache.size }

    /** Test seam: the configuration the live cache was built for. */
    fun cachedFor(): Pair<Locale?, String?> = synchronized(lock) { locale to zoneId }
}

/**
 * The field sets the UI asks for, named by what they mean rather than by how any
 * one language writes them. Shared so two screens showing the same kind of
 * timestamp cannot drift apart.
 */
internal object DateSkeletons {
    /** Hour and minute, in the locale's own hour cycle. */
    const val CLOCK = "jm"

    /** "Jan 5" / "5 Jan" — a date inside the current year. */
    const val DAY_MONTH = "MMMd"

    /** "Jan 5, 2024" — a date that needs its year. */
    const val DAY_MONTH_YEAR = "MMMdy"

    /** "Jan 5, 2024, 14:30" — a full stamp for lists of records. */
    const val DAY_MONTH_YEAR_CLOCK = "MMMdyjm"

    /** "Fri, Jan 5" — day pills inside a conversation. */
    const val WEEKDAY_DAY_MONTH = "EMMMd"

    /** "Fri, Jan 5, 2:30 PM" — a scheduled send. */
    const val WEEKDAY_DAY_MONTH_CLOCK = "EMMMdjm"

    /** "Fri, Jan 5, 2024, 2:30:15 PM" — message details, where seconds matter. */
    const val WEEKDAY_DAY_MONTH_YEAR_SECONDS = "EMMMdyjms"

    /** Every skeleton above, for the guard test that pins them. */
    val ALL = listOf(
        CLOCK, DAY_MONTH, DAY_MONTH_YEAR, DAY_MONTH_YEAR_CLOCK,
        WEEKDAY_DAY_MONTH, WEEKDAY_DAY_MONTH_CLOCK, WEEKDAY_DAY_MONTH_YEAR_SECONDS,
    )

    /**
     * The local calendar day [timestamp] falls in, as a day number.
     *
     * Comparing these is how the UI decides "today, so show a clock" — cheaper
     * and clearer than building a Calendar, and correct across the zone offset.
     * `floorDiv`, not `/`: integer division truncates toward zero, which puts
     * everything in the last day before the epoch into day 0 along with the
     * first day after it.
     */
    fun localDayOf(timestamp: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(timestamp + zone.getOffset(timestamp), 86_400_000L)

    /** Whether [a] and [b] land on the same local calendar day. */
    fun isSameLocalDay(a: Long, b: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        localDayOf(a, zone) == localDayOf(b, zone)
}
