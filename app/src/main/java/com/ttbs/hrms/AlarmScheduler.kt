package com.ttbs.hrms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    private const val REQ = 1001
    private const val REQ_BREAK = 1002
    private const val REQ_RESET = 1003

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CHECK)
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAt(ctx: Context, triggerAtMillis: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        try {
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else true
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi) }
        }
    }

    private fun pendingBreak(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_BREAK)
        return PendingIntent.getBroadcast(
            ctx, REQ_BREAK, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingReset(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RESET)
        return PendingIntent.getBroadcast(
            ctx, REQ_RESET, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Fires when today's break allowance runs out - now if it already has, else when it will. */
    fun scheduleBreak(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val remainingMs = (Cfg.breakLimitMin(ctx) * 60L - Day.breakTodaySecs(ctx)) * 1000L
        val at = System.currentTimeMillis() + if (remainingMs > 0) remainingMs else 60_000L
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingBreak(ctx))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingBreak(ctx))
            }
        }
    }

    fun cancelBreak(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingBreak(ctx))
    }

    /** Clears every counter at midnight, whether or not the app is open. */
    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis(), pendingReset(ctx))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis(), pendingReset(ctx))
            }
        }
    }

    fun nextMidnightMillis(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 5)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun scheduleRepeat(ctx: Context) {
        scheduleAt(ctx, System.currentTimeMillis() + Cfg.repeat(ctx) * 60_000L)
    }

    fun nextDeadlineMillis(ctx: Context): Long {
        val mins = Cfg.deadline(ctx)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, mins / 60)
        cal.set(Calendar.MINUTE, mins % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }
}
