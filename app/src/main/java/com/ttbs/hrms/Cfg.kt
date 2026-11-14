package com.ttbs.hrms

import android.content.Context
import android.content.SharedPreferences

/** All user settings, stored in app-private preferences. */
object Cfg {

    const val DEFAULT_URL = "https://technotask.net/ttbs_hrms/index.php/EMPLOYEE/Dashboard/"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("punchguard", Context.MODE_PRIVATE)

    fun url(c: Context): String = sp(c).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun setUrl(c: Context, v: String) { sp(c).edit().putString("url", v.trim()).apply() }

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) { sp(c).edit().putBoolean("enabled", v).apply() }

    /** Deadline as minutes from midnight. 18*60 = 6:00 PM. */
    fun deadline(c: Context): Int = sp(c).getInt("deadline", 18 * 60)
    fun setDeadline(c: Context, minutes: Int) { sp(c).edit().putInt("deadline", minutes).apply() }

    fun repeat(c: Context): Int = sp(c).getInt("repeat", 2)
    fun setRepeat(c: Context, minutes: Int) {
        sp(c).edit().putInt("repeat", minutes.coerceIn(1, 180)).apply()
    }

    /** true = open the portal and read the Punch In/Out card; false = ring on time alone. */
    fun verify(c: Context): Boolean = sp(c).getBoolean("verify", false)
    fun setVerify(c: Context, v: Boolean) { sp(c).edit().putBoolean("verify", v).apply() }

    fun desktop(c: Context): Boolean = sp(c).getBoolean("desktop", false)
    fun setDesktop(c: Context, v: Boolean) { sp(c).edit().putBoolean("desktop", v).apply() }

    fun outRegex(c: Context): String = sp(c).getString("outRegex", "") ?: ""
    fun inRegex(c: Context): String = sp(c).getString("inRegex", "") ?: ""
    fun setRegex(c: Context, out: String, inn: String) {
        sp(c).edit().putString("outRegex", out.trim()).putString("inRegex", inn.trim()).apply()
    }

    fun outSample(c: Context): String = sp(c).getString("outSample", "") ?: ""
    fun inSample(c: Context): String = sp(c).getString("inSample", "") ?: ""
    fun setOutSample(c: Context, v: String) { sp(c).edit().putString("outSample", v).apply() }
    fun setInSample(c: Context, v: String) { sp(c).edit().putString("inSample", v).apply() }

    fun autoLogin(c: Context): Boolean = sp(c).getBoolean("autoLogin", false)
    fun setAutoLogin(c: Context, v: Boolean) { sp(c).edit().putBoolean("autoLogin", v).apply() }

    fun username(c: Context): String = sp(c).getString("user", "") ?: ""
    fun setUsername(c: Context, v: String) { sp(c).edit().putString("user", v.trim()).apply() }

    fun password(c: Context): String {
        val enc = sp(c).getString("pass", "") ?: ""
        if (enc.isEmpty()) return ""
        return runCatching { Crypto.decrypt(enc) }.getOrDefault("")
    }

    fun setPassword(c: Context, v: String) {
        val enc = if (v.isEmpty()) "" else runCatching { Crypto.encrypt(v) }.getOrDefault("")
        sp(c).edit().putString("pass", enc).apply()
    }

    fun punchIn(c: Context): String = sp(c).getString("punchIn", "") ?: ""
    fun setPunchIn(c: Context, v: String) { sp(c).edit().putString("punchIn", v).apply() }

    fun punchOut(c: Context): String = sp(c).getString("punchOut", "") ?: ""
    fun setPunchOut(c: Context, v: String) { sp(c).edit().putString("punchOut", v).apply() }

    /** Clears yesterday's punch-out when a new day starts. */
    fun punchDay(c: Context): String = sp(c).getString("punchDay", "") ?: ""
    fun setPunchDay(c: Context, v: String) { sp(c).edit().putString("punchDay", v).apply() }

    fun loginUrl(c: Context): String = sp(c).getString("loginUrl", "https://technotask.net/ttbs_hrms/") ?: ""
    fun setLoginUrl(c: Context, v: String) { sp(c).edit().putString("loginUrl", v.trim()).apply() }

    fun pendingReturn(c: Context): Boolean = sp(c).getBoolean("pendingReturn", false)
    fun setPendingReturn(c: Context, v: Boolean) { sp(c).edit().putBoolean("pendingReturn", v).apply() }

    fun lastRead(c: Context): String = sp(c).getString("lastRead", "") ?: ""
    fun setLastRead(c: Context, v: String) { sp(c).edit().putString("lastRead", v).apply() }

    fun lastResult(c: Context): String = sp(c).getString("lastResult", "") ?: ""
    fun setLastResult(c: Context, v: String) {
        val stamp = java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        sp(c).edit().putString("lastResult", "$stamp — $v").apply()
    }

    /** false = 12-hour clock with AM/PM, true = 24-hour. Applies to clock times only. */
    fun hour24(c: Context): Boolean = sp(c).getBoolean("hour24", false)
    fun setHour24(c: Context, v: Boolean) { sp(c).edit().putBoolean("hour24", v).apply() }

    fun onboarded(c: Context): Boolean = sp(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) { sp(c).edit().putBoolean("onboarded", v).apply() }

    fun breakTotalSec(c: Context): Long = sp(c).getLong("breakTotal", 0L)
    fun setBreakTotalSec(c: Context, v: Long) { sp(c).edit().putLong("breakTotal", v).apply() }

    /** 0 means "not on a break". Stored as a wall-clock timestamp so it survives the app closing. */
    fun breakStartMs(c: Context): Long = sp(c).getLong("breakStart", 0L)
    fun setBreakStartMs(c: Context, v: Long) { sp(c).edit().putLong("breakStart", v).apply() }

    fun breakLimitMin(c: Context): Int = sp(c).getInt("breakLimit", 45)
    fun setBreakLimitMin(c: Context, v: Int) { sp(c).edit().putInt("breakLimit", v.coerceIn(5, 240)).apply() }

    fun dayKey(c: Context): String = sp(c).getString("dayKey", "") ?: ""
    fun setDayKey(c: Context, v: String) { sp(c).edit().putString("dayKey", v).apply() }

    /** Wipes every counter for the day. Called at midnight and on the first open of a new date. */
    fun resetDay(c: Context) {
        sp(c).edit()
            .putString("punchIn", "")
            .putString("punchOut", "")
            .putLong("breakTotal", 0L)
            .putLong("breakStart", 0L)
            .putString("dayKey", Day.todayKey())
            .apply()
    }

    /** Returns true when the stored day was stale and has just been cleared. */
    fun ensureToday(c: Context): Boolean {
        if (dayKey(c) == Day.todayKey()) return false
        resetDay(c)
        return true
    }

    fun deadlineLabel(c: Context): String {
        val m = deadline(c)
        val h = m / 60
        val mm = m % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(java.util.Locale.getDefault(), "%d:%02d %s", h12, mm, ampm)
    }
}
