package com.smartblocker.app

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager

/** Questions about the apps installed on the phone. */
object Apps {

    @Volatile
    private var notCountedCache: Set<String>? = null

    /** "com.instagram.android" -> "Instagram" */
    @Suppress("DEPRECATION")
    fun label(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** Every app with an icon in the app drawer, as (package, name), sorted by name. */
    @Suppress("DEPRECATION")
    fun launchable(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .sortedBy { it.second.lowercase() }
    }

    /** The home screen app(s). */
    @Suppress("DEPRECATION")
    private fun homeApps(ctx: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun keyboards(ctx: Context): Set<String> {
        return try {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Keyboards and the notification shade: they pop over an app, they don't replace it. */
    fun overlays(ctx: Context): Set<String> = keyboards(ctx) + setOf("com.android.systemui", "android")

    /** Not counted in the whole-day total: home screen, keyboards, system UI and this app. */
    fun notCounted(ctx: Context): Set<String> {
        val cached = notCountedCache
        if (cached != null) return cached
        val set = homeApps(ctx) + overlays(ctx) + ctx.packageName
        notCountedCache = set
        return set
    }
}
