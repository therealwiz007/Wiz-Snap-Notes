package com.wizlabs.snapnotes

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date-grouped history for Home: Today, Yesterday, weekday names for this week, then dates. */
object Timeline {
    private const val DAY = 24 * 60 * 60 * 1000L

    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Keeps the newest-first order; each visit sits on the day of its latest snap. */
    fun byDay(visits: List<Visit>): List<Pair<Long, List<Visit>>> =
        visits.groupBy { dayStart(it.latestMs) }.toList()

    fun label(day: Long, now: Long = System.currentTimeMillis()): String {
        val today = dayStart(now)
        // Rounded, so a daylight-saving day of 23 or 25 hours still counts as one day.
        val ago = Math.round((today - day).toDouble() / DAY).toInt()
        val l = Locale.getDefault()
        return when {
            ago <= 0 -> "Today"
            ago == 1 -> "Yesterday"
            ago < 7 -> SimpleDateFormat("EEEE", l).format(Date(day))
            sameYear(day, now) -> SimpleDateFormat(DateFormat.getBestDateTimePattern(l, "EEEdMMM"), l).format(Date(day))
            else -> SimpleDateFormat(DateFormat.getBestDateTimePattern(l, "dMMMyyyy"), l).format(Date(day))
        }
    }

    /** Time of day in the phone's own 12/24-hour style. */
    fun time(ms: Long): String {
        val l = Locale.getDefault()
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(l, "jmm"), l).format(Date(ms))
    }

    private fun sameYear(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
    }
}
