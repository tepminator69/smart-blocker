package com.smartblocker.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout

/** Shared setup for the two screens that show our HTML pages. */
object Web {
    val BG: Int = Color.rgb(8, 8, 10)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(activity: Activity): WebView {
        val web = WebView(activity)
        web.setBackgroundColor(BG)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.overScrollMode = View.OVER_SCROLL_NEVER
        return web
    }

    /** Draws behind the status and navigation bars, but keeps the page clear of them and of the keyboard. */
    fun wrap(activity: Activity, web: WebView): View {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.window.setDecorFitsSystemWindows(false)
        } else {
            legacyFullScreenLayout(activity)
        }
        transparentBars(activity)
        val root = FrameLayout(activity)
        root.setBackgroundColor(BG)
        root.addView(
            web,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        root.setOnApplyWindowInsetsListener { v, insets ->
            pad(v, insets)
            insets
        }
        return root
    }

    private fun pad(v: View, insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
        } else {
            legacyPad(v, insets)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyPad(v: View, insets: WindowInsets) {
        v.setPadding(
            insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyFullScreenLayout(activity: Activity) {
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    @Suppress("DEPRECATION")
    private fun transparentBars(activity: Activity) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
    }
}
