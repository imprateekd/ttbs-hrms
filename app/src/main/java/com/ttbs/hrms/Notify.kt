package com.ttbs.hrms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notify {

    const val CH_ALARM = "alarm"
    const val CH_STATUS = "status"

    fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        val alarm = NotificationChannel(CH_ALARM, "Punch-out alarm", NotificationManager.IMPORTANCE_HIGH)
        alarm.description = "Loud, full-screen alerts when you have not punched out"
        alarm.enableVibration(true)
        alarm.enableLights(true)
        alarm.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(alarm)

        val status = NotificationChannel(CH_STATUS, "Status", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(status)
    }

    private fun pending(ctx: Context, cls: Class<*>, req: Int, extra: String? = null): PendingIntent {
        val i = Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra != null) i.putExtra("reason", extra)
        return PendingIntent.getActivity(
            ctx, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Notification that can pull a screen up over the lock screen. */
    fun checkPrompt(ctx: Context, title: String, text: String, target: Class<*>) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_guard)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending(ctx, target, 22))
            .setFullScreenIntent(pending(ctx, target, 21), true)
            .build()
        runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(21, n) }
    }

    fun simple(ctx: Context, title: String, text: String) {
        ensure(ctx)
        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_guard)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending(ctx, MainActivity::class.java, 31))
            .build()
        runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(31, n) }
    }
}
