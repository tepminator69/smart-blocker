package com.smartblocker.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** The full-screen "this app is paused" page. Its look lives in assets/www/block.html. */
class BlockActivity : Activity() {

    private lateinit var web: WebView
    private var pkg: String = ""
    private var data: String = "{}"
    private var pageReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = Web.create(this)
        web.addJavascriptInterface(BlockBridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                sendData()
            }
        }
        setContentView(Web.wrap(this, web))
        readIntent(intent)
        web.loadUrl("file:///android_asset/www/block.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        if (pageReady) sendData()
    }

    private fun readIntent(i: Intent) {
        pkg = i.getStringExtra("pkg") ?: ""
        data = JSONObject()
            .put("pkg", pkg)
            .put("name", i.getStringExtra("name") ?: "")
            .put("title", i.getStringExtra("title") ?: "")
            .put("detail", i.getStringExtra("detail") ?: "")
            .put("label", i.getStringExtra("label") ?: "")
            .put("until", i.getLongExtra("until", 0L))
            .put("snooze", i.getBooleanExtra("snooze", false))
            .put("left", i.getIntExtra("left", 0))
            .toString()
    }

    private fun sendData() {
        web.evaluateJavascript("window.setData && window.setData($data)", null)
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: Exception) {
            // ignore
        }
        finish()
    }

    @Deprecated("Back always leaves to the home screen")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        goHome()
    }

    inner class BlockBridge {

        @JavascriptInterface
        fun close() {
            runOnUiThread { goHome() }
        }

        /** Opens the app for 5 minutes. Returns false when snoozing is not allowed or none are left. */
        @JavascriptInterface
        fun snooze(): Boolean {
            val target = pkg
            if (target.isEmpty()) return false
            val verdict = Engine.verdictFor(this@BlockActivity, target)
            if (verdict != null && !verdict.canSnooze) return false
            if (!DayState.useSnooze(this@BlockActivity, target)) return false
            val launch = packageManager.getLaunchIntentForPackage(target)
            runOnUiThread {
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(launch)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                finish()
            }
            return true
        }

        @JavascriptInterface
        fun openApp() {
            runOnUiThread {
                startActivity(Intent(this@BlockActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }
    }
}
