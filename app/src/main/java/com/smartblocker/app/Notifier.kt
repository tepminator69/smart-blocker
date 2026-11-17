package com.smartblocker.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Sends the reminders and the quiet "block is on" notification. */
object Notifier {
    private const val CH_REMIND = "reminders"
    private const val CH_ACTIVE = "active_block"
    private const val ID_ACTIVE = 1
    private var lastActive: String? = null

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Android 8+ needs "channels"; the user can mute each one in the app's notification settings. */
    fun ensureChannels(ctx: Context) {
        val remind = NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        remind.description = "Limits that are almost used up and blocks that are about to start"
        val active = NotificationChannel(CH_ACTIVE, "Active block", NotificationManager.IMPORTANCE_LOW)
        active.description = "Shows while a block schedule is running"
        nm(ctx).createNotificationChannel(remind)
        nm(ctx).createNotificationChannel(active)
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return nm(ctx).areNotificationsEnabled()
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun remind(ctx: Context, key: String, title: String, text: String) {
        if (!canPost(ctx)) return
        val n = Notification.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_blocker)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        nm(ctx).notify((key.hashCode() and 0x7fffffff) or 0x100, n)
    }

    fun showActive(ctx: Context, title: String, text: String, progress: Int) {
        if (!canPost(ctx)) return
        val key = "$title|$text|$progress"
        if (key == lastActive) return
        lastActive = key
        val n = Notification.Builder(ctx, CH_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_blocker)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setContentIntent(openApp(ctx))
            .build()
        nm(ctx).notify(ID_ACTIVE, n)
    }

    fun hideActive(ctx: Context) {
        if (lastActive == null) return
        lastActive = null
        nm(ctx).cancel(ID_ACTIVE)
    }
}
