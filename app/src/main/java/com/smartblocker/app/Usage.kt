package com.smartblocker.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Counts how long each app was on screen today. Android records an event every time an app
 * comes to the front or goes away; we add up the time between those two events.
 * This is the same data Digital Wellbeing uses. It needs "Usage access".
 */
object Usage {
    private var cacheAt = 0L
    private var cacheFg: String? = null
    private var cache: Map<String, Long> = emptyMap()

    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Milliseconds on screen since midnight, per package.
     * [foreground] = the app open right now; its time keeps running until this moment.
     */
    @Synchronized
    @Suppress("DEPRECATION")
    fun today(ctx: Context, foreground: String?): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (now - cacheAt < 10_000L && foreground == cacheFg) return cache

        val result = HashMap<String, Long>()
        if (hasAccess(ctx)) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(Time.midnight(), now)
            val openSince = HashMap<String, Long>() // "package/screen" -> when it came to the front
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                val key = pkg + "/" + (ev.className ?: "")
                when (ev.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> openSince[key] = ev.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = openSince.remove(key)
                        if (start != null) result[pkg] = (result[pkg] ?: 0L) + (ev.timeStamp - start)
                    }
                }
            }
            if (foreground != null) {
                val start = openSince.filterKeys { it.startsWith("$foreground/") }.values.maxOrNull()
                if (start != null) result[foreground] = (result[foreground] ?: 0L) + (now - start)
            }
        }
        cacheAt = now
        cacheFg = foreground
        cache = result
        return result
    }

    fun minutes(usage: Map<String, Long>, pkg: String): Double = (usage[pkg] ?: 0L) / 60_000.0

    /** All apps together, without the home screen, keyboards and this app. */
    fun totalMinutes(ctx: Context, usage: Map<String, Long>): Double {
        val skip = Apps.notCounted(ctx)
        var sum = 0L
        for ((pkg, ms) in usage) if (pkg !in skip) sum += ms
        return sum / 60_000.0
    }
}
