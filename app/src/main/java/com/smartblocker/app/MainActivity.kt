package com.smartblocker.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * The main screen. Everything you see is the web page in assets/www (HTML, CSS, JavaScript).
 * The page talks to Kotlin through the "Android" object below (the Bridge).
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        web = Web.create(this)
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(Web.wrap(this, web))
        web.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.refresh && window.refresh()", null)
    }

    @Deprecated("Back first closes open panels on the page")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.goBack ? window.goBack() : false") { result ->
            if (result != "true") finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        web.evaluateJavascript("window.refresh && window.refresh()", null)
    }

    private fun open(intent: Intent) {
        runOnUiThread {
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e2: Exception) {
                    // nothing else we can do
                }
            }
        }
    }

    private fun accessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), ignoreCase = true) ||
                it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }

    /** Every function marked @JavascriptInterface can be called from app.js as Android.functionName(). */
    inner class Bridge {

        @JavascriptInterface
        fun getSettings(): String = Store.load(this@MainActivity).toString()

        /** Returns "ok", "strict" (Strict mode refused a weaker rule) or "error". */
        @JavascriptInterface
        fun saveSettings(json: String): String {
            val next = try {
                JSONObject(json)
            } catch (e: Exception) {
                return "error"
            }
            val old = Store.load(this@MainActivity)
            if (Store.strictOn(old) && Store.isLooser(old, next)) return "strict"
            Store.save(this@MainActivity, next)
            return "ok"
        }

        /** Today's minutes per app, the whole-day total, and which permissions are switched on. */
        @JavascriptInterface
        fun getStatus(): String {
            val ctx = this@MainActivity
            val usage = Usage.today(ctx, null)
            val skip = Apps.notCounted(ctx)
            val perApp = JSONObject()
            for ((pkg, ms) in usage) perApp.put(pkg, ms / 60_000.0)
            val top = JSONArray()
            usage.filterKeys { it !in skip }.entries
                .sortedByDescending { it.value }
                .take(8)
                .forEach {
                    top.put(
                        JSONObject()
                            .put("pkg", it.key)
                            .put("name", Apps.label(ctx, it.key))
                            .put("min", it.value / 60_000.0)
                    )
                }
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val perms = JSONObject()
                .put("accessibility", accessibilityOn())
                .put("usage", Usage.hasAccess(ctx))
                .put("notifications", Notifier.canPost(ctx))
                .put("battery", pm.isIgnoringBatteryOptimizations(packageName))
            return JSONObject()
                .put("perms", perms)
                .put("usage", perApp)
                .put("total", Usage.totalMinutes(ctx, usage))
                .put("top", top)
                .put("snoozesLeft", DayState.snoozesLeft(ctx))
                .put("android", Build.VERSION.SDK_INT)
                .toString()
        }

        /** All apps in the app drawer: [{"pkg": "...", "name": "..."}] */
        @JavascriptInterface
        fun getApps(): String {
            val arr = JSONArray()
            for ((pkg, name) in Apps.launchable(this@MainActivity)) {
                arr.put(JSONObject().put("pkg", pkg).put("name", name))
            }
            return arr.toString()
        }

        /** Opens the Android settings page the setup steps need. */
        @JavascriptInterface
        fun openSetting(which: String) {
            val uri = Uri.parse("package:$packageName")
            when (which) {
                "accessibility" -> open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "usage" -> open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                "battery" -> open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
                "appinfo" -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                "notifications" -> runOnUiThread {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
                    } else {
                        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    }
                }
            }
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 7
    }
}
