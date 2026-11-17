package com.smartblocker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the rules the user made (apps, limits, schedules, reminders).
 * They are saved as one JSON text inside SharedPreferences — a small key/value file
 * that Android gives every app.
 *
 * Shape of the JSON:
 * {
 *   "dailyLimit": 240,                                   minutes for all apps together, 0 = off
 *   "apps": [ {"pkg": "com.instagram.android", "name": "Instagram", "limit": 45} ],
 *   "schedules": [ {"id": "s1", "name": "Study", "start": 540, "end": 780,
 *                   "days": [true,true,true,true,true,false,false],
 *                   "apps": ["com.instagram.android"], "mode": "gentle", "remind": true, "on": true} ],
 *   "reminders": {"half": false, "eighty": true, "five": true, "block": true, "summary": true},
 *   "strictUntil": 0                                      time in ms until Strict mode ends
 * }
 */
object Store {
    private const val PREFS = "smart_blocker"
    private const val KEY = "settings"

    @Volatile
    private var cache: JSONObject? = null

    /** Always returns a fresh copy, so callers can change it safely. */
    @Synchronized
    fun load(ctx: Context): JSONObject {
        val cached = cache
        if (cached != null) return JSONObject(cached.toString())
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val json = try {
            if (raw != null) JSONObject(raw) else defaults()
        } catch (e: Exception) {
            defaults()
        }
        cache = json
        return JSONObject(json.toString())
    }

    @Synchronized
    fun save(ctx: Context, json: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
        cache = JSONObject(json.toString())
    }

    fun defaults(): JSONObject {
        val reminders = JSONObject()
            .put("half", false)
            .put("eighty", true)
            .put("five", true)
            .put("block", true)
            .put("summary", true)
        return JSONObject()
            .put("dailyLimit", 240)
            .put("apps", JSONArray())
            .put("schedules", JSONArray())
            .put("reminders", reminders)
            .put("strictUntil", 0L)
    }

    fun strictOn(s: JSONObject): Boolean = s.optLong("strictUntil", 0L) > System.currentTimeMillis()

    fun findApp(s: JSONObject, pkg: String): JSONObject? {
        val apps = s.optJSONArray("apps") ?: return null
        for (i in 0 until apps.length()) {
            val a = apps.optJSONObject(i) ?: continue
            if (a.optString("pkg") == pkg) return a
        }
        return null
    }

    fun schedules(s: JSONObject): List<JSONObject> {
        val arr = s.optJSONArray("schedules") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    fun scheduleHasApp(sc: JSONObject, pkg: String): Boolean {
        val apps = sc.optJSONArray("apps") ?: return false
        for (i in 0 until apps.length()) if (apps.optString(i) == pkg) return true
        return false
    }

    /**
     * Strict mode check: true if [next] is weaker than [old]
     * (a higher limit, a removed limit, a block switched off or made shorter, Strict mode ended early).
     */
    fun isLooser(old: JSONObject, next: JSONObject): Boolean {
        val oldDaily = old.optInt("dailyLimit", 0)
        val newDaily = next.optInt("dailyLimit", 0)
        if (oldDaily > 0 && (newDaily == 0 || newDaily > oldDaily)) return true
        if (next.optLong("strictUntil", 0L) < old.optLong("strictUntil", 0L)) return true

        val oldApps = old.optJSONArray("apps") ?: JSONArray()
        for (i in 0 until oldApps.length()) {
            val a = oldApps.optJSONObject(i) ?: continue
            val limit = a.optInt("limit", 0)
            if (limit <= 0) continue
            val b = findApp(next, a.optString("pkg")) ?: return true
            val newLimit = b.optInt("limit", 0)
            if (newLimit == 0 || newLimit > limit) return true
        }

        val newSchedules = schedules(next).associateBy { it.optString("id") }
        for (sc in schedules(old)) {
            if (!sc.optBoolean("on", true)) continue
            val n = newSchedules[sc.optString("id")] ?: return true
            if (!n.optBoolean("on", true)) return true
            if (n.optInt("start") != sc.optInt("start") || n.optInt("end") != sc.optInt("end")) return true
            for (d in 0 until 7) if (Time.days(sc, d) && !Time.days(n, d)) return true
            val apps = sc.optJSONArray("apps") ?: JSONArray()
            for (i in 0 until apps.length()) if (!scheduleHasApp(n, apps.optString(i))) return true
            if (sc.optString("mode") == "strict" && n.optString("mode") != "strict") return true
        }
        return false
    }
}
