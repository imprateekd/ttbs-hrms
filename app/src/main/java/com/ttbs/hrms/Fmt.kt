package com.ttbs.hrms

import android.content.Context
import java.util.Locale

/**
 * Two different kinds of number, formatted differently on purpose.
 *
 *  - Clock times (punch in, punch out, the deadline) follow the 12/24-hour setting.
 *  - Durations (Active Hours, break) are elapsed time and never carry AM or PM.
 */
object Fmt {

    /** "09:12:33" -> "9:12 AM" or "09:12". Returns the input unchanged if it is not a time. */
    fun time(ctx: Context, hms: String): String {
        if (hms.isBlank()) return "--:--"
        val parts = hms.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return hms
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return hms
        return if (Cfg.hour24(ctx)) {
            String.format(Locale.US, "%02d:%02d", h, m)
        } else {
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
        }
    }

    fun timeWithSeconds(ctx: Context, hms: String): String {
        if (hms.isBlank()) return "--:--:--"
        val parts = hms.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return hms
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return if (Cfg.hour24(ctx)) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(Locale.US, "%d:%02d:%02d %s", h12, m, s, suffix)
        }
    }

    /** Durations: "06:42:11" - no meridiem, ever, because it is not a time of day. */
    fun duration(seconds: Long): String {
        val t = if (seconds < 0) 0 else seconds
        return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }

    fun durationTicking(seconds: Long): String = duration(seconds)

    fun deadlineLabel(ctx: Context): String {
        val mins = Cfg.deadline(ctx)
        val h = mins / 60
        val m = mins % 60
        return if (Cfg.hour24(ctx)) {
            String.format(Locale.US, "%02d:%02d", h, m)
        } else {
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
        }
    }
}
