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

    fun desktop(c: Context): Boolean = sp(c).getBoolean("desktop", true)
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

    fun lastResult(c: Context): String = sp(c).getString("lastResult", "") ?: ""
    fun setLastResult(c: Context, v: String) {
        val stamp = java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        sp(c).edit().putString("lastResult", "$stamp — $v").apply()
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
