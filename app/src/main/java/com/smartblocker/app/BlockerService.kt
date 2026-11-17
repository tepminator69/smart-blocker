package com.smartblocker.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

/**
 * The heart of the blocker.
 * Android tells an Accessibility service every time a new window opens, so we always know
 * which app is in front. If a rule says "paused", we go to the home screen and show BlockActivity.
 * Every 20 seconds we check again (limits run out while you use an app) and send reminders.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var overlays: Set<String> = emptySet()
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            try {
                if (screenOn()) {
                    current?.let { enforce(it) }
                    Reminders.tick(this@BlockerService, current)
                }
            } catch (e: Exception) {
                // One failed check must never stop the service.
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlays = Apps.overlays(this)
        Notifier.ensureChannels(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in overlays) return // keyboard or notification shade: the app below is still in front
        current = pkg
        if (pkg == packageName) return
        try {
            enforce(pkg)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun enforce(pkg: String) {
        if (pkg == packageName) return
        val verdict = Engine.verdictFor(this, pkg) ?: return
        val now = System.currentTimeMillis()
        if (pkg == lastBlockPkg && now - lastBlockAt < 1500L) return // already showing it
        lastBlockPkg = pkg
        lastBlockAt = now

        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("pkg", verdict.pkg)
            .putExtra("name", verdict.name)
            .putExtra("title", verdict.title)
            .putExtra("detail", verdict.detail)
            .putExtra("label", verdict.label)
            .putExtra("until", verdict.until)
            .putExtra("snooze", verdict.canSnooze)
            .putExtra("left", DayState.snoozesLeft(this))
        startActivity(intent)
    }

    private fun screenOn(): Boolean = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Notifier.hideActive(this)
        super.onDestroy()
    }

    companion object {
        private const val TICK_MS = 20_000L
    }
}
