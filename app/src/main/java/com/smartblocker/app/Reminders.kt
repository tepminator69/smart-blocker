package com.smartblocker.app

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.ceil

/** Runs every 20 seconds (from BlockerService) and sends the reminders the user switched on. */
object Reminders {

    fun tick(ctx: Context, foreground: String?) {
        val s = Store.load(ctx)
        val r = s.optJSONObject("reminders") ?: JSONObject()
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val minute = Time.minutesNow(cal)
        val today = Time.dayIndex(cal)
        val schedules = Store.schedules(s)

        // 1. A block starts in 10 minutes or less.
        if (r.optBoolean("block", true)) {
            for (sc in schedules) {
                if (!sc.optBoolean("on", true) || !sc.optBoolean("remind", true)) continue
                val start = sc.optInt("start", 0)
                val wait = (start - minute + 1440) % 1440
                val day = if (start >= minute) today else (today + 1) % 7
                if (wait in 1..10 && Time.days(sc, day) && !Time.isActive(sc, cal) &&
                    DayState.once(ctx, "pre:" + sc.optString("id") + ":" + start)
                ) {
                    Notifier.remind(
                        ctx, "pre:" + sc.optString("id"),
                        "${sc.optString("name", "Block")} starts in $wait min",
                        "${appNames(ctx, s, sc)} pause at ${Time.hhmm(start)}."
                    )
                }
            }
        }

        // 2. While a block runs, keep a quiet notification with the time left.
        val running = schedules.firstOrNull { Time.isActive(it, cal) }
        if (running != null) {
            val end = Time.endMillis(running, cal)
            var length = (running.optInt("end") - running.optInt("start") + 1440) % 1440
            if (length == 0) length = 1440
            val leftMin = ((end - now) / 60_000L).toInt()
            val progress = (100 - leftMin * 100 / length).coerceIn(0, 100)
            Notifier.showActive(
                ctx,
                "${running.optString("name", "Block")} block is on",
                "${appNames(ctx, s, running)} paused · ${Time.left(end - now)} left",
                progress
            )
        } else {
            Notifier.hideActive(ctx)
        }

        if (!Usage.hasAccess(ctx)) return
        val usage = Usage.today(ctx, foreground)

        // 3. Limits that are almost used up.
        if (foreground != null) {
            val app = Store.findApp(s, foreground)
            if (app != null) {
                limitReminder(
                    ctx, r, "app:$foreground", app.optString("name", "this app"),
                    Usage.minutes(usage, foreground), app.optInt("limit", 0), false
                )
            }
        }
        val daily = s.optInt("dailyLimit", 0)
        if (daily > 0) limitReminder(ctx, r, "total", "", Usage.totalMinutes(ctx, usage), daily, true)

        // 4. A 5-minute snooze is about to end.
        if (foreground != null) {
            val until = DayState.snoozeUntil(ctx, foreground)
            if (until - now in 1L..65_000L && DayState.once(ctx, "snooze:$foreground:$until")) {
                val name = Store.findApp(s, foreground)?.optString("name") ?: Apps.label(ctx, foreground)
                Notifier.remind(ctx, "snooze:$foreground", "1 minute left", "$name pauses again in a minute.")
            }
        }

        // 5. Evening summary at 21:00.
        if (r.optBoolean("summary", true) && minute >= 21 * 60 && DayState.once(ctx, "summary")) {
            val total = Usage.totalMinutes(ctx, usage).toInt()
            val skip = Apps.notCounted(ctx)
            val top = usage.filterKeys { it !in skip }.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(", ") { "${Apps.label(ctx, it.key)} ${Time.fmt((it.value / 60_000L).toInt())}" }
            Notifier.remind(
                ctx, "summary",
                "Today: ${Time.fmt(total)} on your phone",
                if (top.isEmpty()) "A quiet day." else "Most used: $top."
            )
        }
    }

    private fun limitReminder(
        ctx: Context, r: JSONObject, key: String, name: String,
        used: Double, limit: Int, isTotal: Boolean
    ) {
        if (limit <= 0 || used >= limit) return
        val left = limit - used
        val share = used / limit
        if (left <= 5.0 && r.optBoolean("five", true)) {
            if (DayState.once(ctx, "five:$key")) {
                val m = ceil(left).toInt()
                if (isTotal) Notifier.remind(
                    ctx, "five:$key", "$m min of screen time left today",
                    "Your whole-day limit is ${Time.fmt(limit)}. Your apps pause when it runs out."
                )
                else Notifier.remind(
                    ctx, "five:$key", "$m min left on $name",
                    "$name pauses when your ${Time.fmt(limit)} limit runs out."
                )
            }
        } else if (share >= 0.8 && r.optBoolean("eighty", true)) {
            if (DayState.once(ctx, "80:$key")) {
                val m = ceil(left).toInt()
                if (isTotal) Notifier.remind(
                    ctx, "80:$key", "80% of today's screen time used",
                    "${Time.fmt(m)} left of your ${Time.fmt(limit)} whole-day limit."
                )
                else Notifier.remind(
                    ctx, "80:$key", "${Time.fmt(m)} left on $name",
                    "You have used 80% of your ${Time.fmt(limit)} limit."
                )
            }
        } else if (share >= 0.5 && r.optBoolean("half", false)) {
            if (DayState.once(ctx, "50:$key")) {
                if (isTotal) Notifier.remind(
                    ctx, "50:$key", "Half of today's screen time used",
                    "${Time.fmt(used.toInt())} of ${Time.fmt(limit)}."
                )
                else Notifier.remind(
                    ctx, "50:$key", "Halfway through $name",
                    "${Time.fmt(used.toInt())} of your ${Time.fmt(limit)} limit."
                )
            }
        }
    }

    private fun appNames(ctx: Context, s: JSONObject, sc: JSONObject): String {
        val apps = sc.optJSONArray("apps") ?: return "Your apps"
        val names = (0 until apps.length()).map { i ->
            val pkg = apps.optString(i)
            Store.findApp(s, pkg)?.optString("name")?.takeIf { it.isNotBlank() } ?: Apps.label(ctx, pkg)
        }
        return when {
            names.isEmpty() -> "Your apps"
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
        }
    }
}
