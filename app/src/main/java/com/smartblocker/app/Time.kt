package com.smartblocker.app

import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * Clock helpers. A time of day is stored as "minutes after midnight":
 * 09:30 = 9 * 60 + 30 = 570.
 */
object Time {

    fun minutesNow(c: Calendar): Int = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

    /** Monday = 0 … Sunday = 6. (Java's Calendar counts Sunday = 1, Monday = 2 …) */
    fun dayIndex(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun midnight(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun nextMidnight(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = midnight()
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** 570 -> "09:30" */
    fun hhmm(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    /** 45 -> "45m", 90 -> "1h 30m", 120 -> "2h" */
    fun fmt(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else String.format(Locale.US, "%dh %02dm", h, m)
    }

    /** Milliseconds -> "1h 42m" (rounded up to the next minute). */
    fun left(ms: Long): String = fmt(((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(0))

    /** Is the schedule switched on for this weekday? days = [Mon, Tue, … Sun]. */
    fun days(s: JSONObject, index: Int): Boolean = s.optJSONArray("days")?.optBoolean(index, false) ?: false

    /** Is this schedule blocking right now? Works for blocks that cross midnight (22:30 – 07:00). */
    fun isActive(s: JSONObject, c: Calendar): Boolean {
        if (!s.optBoolean("on", true)) return false
        val start = s.optInt("start", 0)
        val end = s.optInt("end", 0)
        val now = minutesNow(c)
        val today = dayIndex(c)
        val yesterday = (today + 6) % 7
        return when {
            start < end -> days(s, today) && now >= start && now < end
            start > end -> (days(s, today) && now >= start) || (days(s, yesterday) && now < end)
            else -> days(s, today) // same start and end = the whole day
        }
    }

    /** The moment the schedule ends: today, or tomorrow if that time already passed. */
    fun endMillis(s: JSONObject, c: Calendar): Long {
        val end = s.optInt("end", 0)
        val e = Calendar.getInstance()
        e.timeInMillis = c.timeInMillis
        e.set(Calendar.HOUR_OF_DAY, end / 60)
        e.set(Calendar.MINUTE, end % 60)
        e.set(Calendar.SECOND, 0)
        e.set(Calendar.MILLISECOND, 0)
        if (e.timeInMillis <= c.timeInMillis) e.add(Calendar.DAY_OF_MONTH, 1)
        return e.timeInMillis
    }
}
