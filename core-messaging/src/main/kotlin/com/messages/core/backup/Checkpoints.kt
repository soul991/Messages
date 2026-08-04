package com.messages.core.backup

import java.util.Calendar
import java.util.TimeZone

/**
 * §8.3 checkpoint model (WhatsApp-style): a backup never contains "messages
 * up to whenever the job ran" — it contains messages up to the most recent
 * 6:00 AM device-local checkpoint for the chosen frequency. Deterministic
 * content regardless of when WorkManager actually gets to run; one snapshot
 * per missed window when the device was off.
 */
object Checkpoints {

    const val CHECKPOINT_HOUR = 6 // 6:00 AM device-local

    enum class Frequency { DAILY, WEEKLY, MONTHLY, MANUAL }

    /**
     * The most recent passed checkpoint for [freq] at [now].
     * DAILY → last 6:00 AM; WEEKLY → last Monday 6:00 AM; MONTHLY → last
     * 1st-of-month 6:00 AM. MANUAL has no schedule — checkpoint is `now`
     * (the user explicitly asked for "everything up to right now").
     */
    fun lastCheckpoint(
        now: Long,
        freq: Frequency,
        zone: TimeZone = TimeZone.getDefault(),
    ): Long {
        if (freq == Frequency.MANUAL) return now
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, CHECKPOINT_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (freq) {
            Frequency.DAILY -> {
                if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            Frequency.WEEKLY -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                if (cal.timeInMillis > now) cal.add(Calendar.WEEK_OF_YEAR, -1)
            }
            Frequency.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                if (cal.timeInMillis > now) cal.add(Calendar.MONTH, -1)
            }
            Frequency.MANUAL -> Unit
        }
        return cal.timeInMillis
    }
}
