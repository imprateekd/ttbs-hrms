package com.ttbs.hrms

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Design C: native home screen, punch status on top, tiles into the portal. */
class HomeActivity : AppCompatActivity() {

    private lateinit var tvPunchIn: TextView
    private lateinit var tvPunchOut: TextView
    private lateinit var tvStatus: TextView
    private var session: GeckoSession? = null
    private var reading = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvPunchIn = findViewById(R.id.tvPunchIn)
        tvPunchOut = findViewById(R.id.tvPunchOut)
        tvStatus = findViewById(R.id.tvStatus)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.tvGreeting).text = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { readPunchStatus() }
        findViewById<Button>(R.id.btnOpenPortal).setOnClickListener { openPortal() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        intArrayOf(
            R.id.tileDashboard, R.id.tileAttendance, R.id.tileLeaves,
            R.id.tileSalary, R.id.tileProfile, R.id.tileMenu
        ).forEach { id -> findViewById<Button>(id).setOnClickListener { openPortal() } }
    }

    override fun onResume() {
        super.onResume()
        if (tvPunchIn.text == "--:--:--") readPunchStatus()
    }

    private fun openPortal() {
        startActivity(Intent(this, PortalActivity::class.java))
    }

    private fun readPunchStatus() {
        if (reading) return
        reading = true
        tvStatus.text = "Reading your attendance…"

        val s = GeckoShell.newSession(this)
        session?.close()
        session = s

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!success) {
                    finishRead("Could not load the dashboard.")
                    return
                }
                session.evaluateJS("document.body ? document.body.innerText : ''")
                    .accept { value -> runOnUiThread { applyText(value?.toString() ?: "") } }
            }
        }
        s.loadUri(Cfg.url(this))

        handler.postDelayed({
            if (reading) finishRead("Timed out reading the dashboard.")
        }, 40_000)
    }

    private fun applyText(text: String) {
        val pin = timeAfter(text, Regex("Punch\\s*In\\b", RegexOption.IGNORE_CASE),
            Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE))
        val pout = timeAfter(text, Regex("Punch\\s*Out\\b", RegexOption.IGNORE_CASE), null)

        tvPunchIn.text = pin ?: "--:--:--"
        tvPunchOut.text = pout ?: "--:--:--"

        val msg = when {
            text.isBlank() -> "Empty page - are you logged in?"
            pout == null -> "Could not find the Punch Out slot on the page."
            isZero(pout) && pin != null && !isZero(pin) -> "Still punched in - punch out before you leave."
            isZero(pout) -> "No punch-in recorded today."
            else -> "Punched out at $pout."
        }
        finishRead(msg)
    }

    private fun timeAfter(text: String, label: Regex, other: Regex?): String? {
        val start = text.indexOf("WORK TIME", ignoreCase = true)
        val scope = if (start >= 0) text.substring(start, minOf(text.length, start + 900)) else text
        val m = label.find(scope) ?: return null
        var rest = scope.substring(m.range.last + 1, minOf(scope.length, m.range.last + 161))
        if (other != null) {
            val o = other.find(rest)
            if (o != null) rest = rest.substring(0, o.range.first)
        }
        return Regex("\\d{1,2}:\\d{2}(:\\d{2})?").find(rest)?.value
    }

    private fun isZero(t: String): Boolean {
        val d = t.filter { it.isDigit() }
        return d.isNotEmpty() && d.all { it == '0' }
    }

    private fun finishRead(message: String) {
        reading = false
        handler.removeCallbacksAndMessages(null)
        tvStatus.text = message
        session?.close()
        session = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { session?.close() }
        super.onDestroy()
    }
}
