package com.smartblocker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Things that start fresh every midnight: snoozes used today and reminders already sent. */
object DayState {
    private const val PREFS = "smart_blocker_day"
    const val SNOOZES_PER_DAY = 3
    const val SNOOZE_MINUTES = 5

    private fun today(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    @Synchronized
    private fun read(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", null)
        val o = try {
            if (raw != null) JSONObject(raw) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        if (o.optString("day") != today()) {
            val fresh = JSONObject()
                .put("day", today())
                .put("snooze", JSONObject())
                .put("used", 0)
                .put("sent", JSONArray())
            write(ctx, fresh)
            return fresh
        }
        return o
    }

    private fun write(ctx: Context, o: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", o.toString()).apply()
    }

    @Synchronized
    fun snoozeUntil(ctx: Context, pkg: String): Long =
        read(ctx).optJSONObject("snooze")?.optLong(pkg, 0L) ?: 0L

    @Synchronized
    fun snoozesLeft(ctx: Context): Int =
        (SNOOZES_PER_DAY - read(ctx).optInt("used", 0)).coerceAtLeast(0)

    /** Opens [pkg] for 5 minutes. Returns false when today's snoozes are used up. */
    @Synchronized
    fun useSnooze(ctx: Context, pkg: String): Boolean {
        val o = read(ctx)
        val used = o.optInt("used", 0)
        if (used >= SNOOZES_PER_DAY) return false
        val snooze = o.optJSONObject("snooze") ?: JSONObject().also { o.put("snooze", it) }
        snooze.put(pkg, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
        o.put("used", used + 1)
        write(ctx, o)
        return true
    }

    /** True only the first time it is called with this key today. Stops repeated reminders. */
    @Synchronized
    fun once(ctx: Context, key: String): Boolean {
        val o = read(ctx)
        val sent = o.optJSONArray("sent") ?: JSONArray().also { o.put("sent", it) }
        for (i in 0 until sent.length()) if (sent.optString(i) == key) return false
        sent.put(key)
        write(ctx, o)
        return true
    }
}
