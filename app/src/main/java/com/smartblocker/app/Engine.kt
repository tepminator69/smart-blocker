package com.smartblocker.app

import android.content.Context
import java.util.Calendar

/** What the block screen should show. */
data class Verdict(
    val pkg: String,
    val name: String,
    val title: String,
    val detail: String,
    val label: String,
    val until: Long,
    val canSnooze: Boolean
)

/** The rules. Decides if an app must be paused right now — and why. */
object Engine {

    /** Returns null when the app may stay open. */
    fun verdictFor(ctx: Context, pkg: String): Verdict? {
        val s = Store.load(ctx)
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val entry = Store.findApp(s, pkg)
        val running = Store.schedules(s).filter { Time.isActive(it, cal) && Store.scheduleHasApp(it, pkg) }
        if (entry == null && running.isEmpty()) return null       // not one of "your apps"
        if (DayState.snoozeUntil(ctx, pkg) > now) return null      // snoozed for 5 minutes

        val name = entry?.optString("name")?.takeIf { it.isNotBlank() } ?: Apps.label(ctx, pkg)
        val strict = Store.strictOn(s)
        val hasSnoozes = DayState.snoozesLeft(ctx) > 0

        // 1. A block schedule is running.
        val block = running.firstOrNull()
        if (block != null) {
            val gentle = block.optString("mode", "gentle") != "strict"
            val blockName = block.optString("name", "Focus")
            return Verdict(
                pkg, name,
                "$name is paused",
                "Your $blockName block keeps it closed until ${Time.hhmm(block.optInt("end"))}.",
                blockName,
                Time.endMillis(block, cal),
                gentle && !strict && hasSnoozes
            )
        }
        if (entry == null) return null

        // 2. The app's own daily limit is used up.
        val usage = Usage.today(ctx, pkg)
        val limit = entry.optInt("limit", 0)
        if (limit > 0 && Usage.minutes(usage, pkg) >= limit) {
            return Verdict(
                pkg, name,
                "$name is paused until tomorrow",
                "You reached your ${Time.fmt(limit)} daily limit.",
                "Daily limit",
                Time.nextMidnight(),
                !strict && hasSnoozes
            )
        }

        // 3. The whole-day limit (all apps together) is used up.
        val daily = s.optInt("dailyLimit", 0)
        if (daily > 0 && Usage.totalMinutes(ctx, usage) >= daily) {
            return Verdict(
                pkg, name,
                "$name is paused until tomorrow",
                "You used your whole-day limit of ${Time.fmt(daily)}.",
                "Whole-day limit",
                Time.nextMidnight(),
                !strict && hasSnoozes
            )
        }
        return null
    }
}
