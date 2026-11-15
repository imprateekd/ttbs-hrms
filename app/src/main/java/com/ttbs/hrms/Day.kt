package com.ttbs.hrms

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All the clock arithmetic in one place.
 *
 *   Active Hours = (now - punch in) - every break taken today - the break running right now
 *
 * Nothing is accumulated in a counter that could drift: every value is derived from the punch-in
 * time stored for today, so ending a break shows the true total rather than "where it paused".
 */
object Day {

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** "09:12:33" -> seconds since midnight, or null when it is not a real time. */
    fun secsOfDay(hms: String?): Long? {
        if (hms.isNullOrBlank()) return null
        val parts = hms.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
        // The portal shows 00:00:00 for "not punched out yet" - that is not midnight.
        if (h == 0 && m == 0 && s == 0) return null
        return (h * 3600 + m * 60 + s).toLong()
    }

    private fun nowSecs(): Long {
        val c = java.util.Calendar.getInstance()
        return (c.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
            c.get(java.util.Calendar.MINUTE) * 60 +
            c.get(java.util.Calendar.SECOND)).toLong()
    }

    fun isOnBreak(c: Context): Boolean = Cfg.breakStartMs(c) > 0L

    fun currentBreakSecs(c: Context): Long {
        val start = Cfg.breakStartMs(c)
        if (start <= 0L) return 0L
        val d = (System.currentTimeMillis() - start) / 1000L
        return if (d < 0) 0L else d
    }

    /** Everything spent on breaks today, including the one running. */
    fun breakTodaySecs(c: Context): Long = Cfg.breakTotalSec(c) + currentBreakSecs(c)

    /** Total time since punch in, breaks included. */
    fun inOfficeSecs(c: Context): Long {
        val pinRaw = Cfg.punchIn(c)
        val pin = secsOfDay(pinRaw) ?: return 0L

        // The portal sometimes repeats the punch-in value in the punch-out field; that is
        // not a punch-out, so the clock must keep running.
        val outRaw = Cfg.punchOut(c)
        val out = if (outRaw.isNotBlank() && outRaw != pinRaw) secsOfDay(outRaw) else null

        val end = out ?: nowSecs()
        var d = end - pin
        // Punched in before midnight and still on the clock: the day-of-time went backwards,
        // which used to clamp to zero and freeze the counter at 00:00:00.
        if (d < 0) d += 86_400L
        return d
    }

    /** What the strip shows: time at work with breaks taken out. */
    fun activeSecs(c: Context): Long {
        val v = inOfficeSecs(c) - breakTodaySecs(c)
        return if (v < 0) 0L else v
    }

    fun hms(total: Long): String {
        val t = if (total < 0) 0 else total
        return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }

    /** True when today's break allowance is used up (limit is per day, not per break). */
    fun overBreakLimit(c: Context): Boolean =
        breakTodaySecs(c) >= Cfg.breakLimitMin(c) * 60L

    fun startBreak(c: Context) {
        if (isOnBreak(c)) return
        Cfg.setBreakStartMs(c, System.currentTimeMillis())
    }

    fun endBreak(c: Context) {
        val start = Cfg.breakStartMs(c)
        if (start <= 0L) return
        Cfg.setBreakTotalSec(c, Cfg.breakTotalSec(c) + currentBreakSecs(c))
        Cfg.setBreakStartMs(c, 0L)
    }
}
