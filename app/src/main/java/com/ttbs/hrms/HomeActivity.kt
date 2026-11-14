package com.ttbs.hrms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Design D2: no top bar. The portal starts under the status bar, and a floating
 * "Menu" pill lifts a bottom sheet with the sections, the punch status and the
 * punch-out action. The portal itself opens in Chrome, because Cloudflare refuses
 * its challenge inside an embedded WebView.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var tvPunchIn: TextView
    private lateinit var tvPunchOut: TextView
    private lateinit var tvStatus: TextView

    private val sections = intArrayOf(
        R.id.s1, R.id.s2, R.id.s3, R.id.s4, R.id.s5, R.id.s6, R.id.s7, R.id.s8
    )

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

        findViewById<Button>(R.id.btnMenu).setOnClickListener { showMenu() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStrip()
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun refreshStrip() {
        // a punch-out recorded on a previous day does not belong to today
        if (Cfg.punchDay(this) != today()) {
            Cfg.setPunchOut(this, "")
            Cfg.setPunchIn(this, "")
        }
        val pin = Cfg.punchIn(this).ifBlank { "--:--:--" }
        val pout = Cfg.punchOut(this).ifBlank { "--:--:--" }
        tvPunchIn.text = pin
        tvPunchOut.text = pout
        findViewById<TextView>(R.id.tvDeadline).text =
            "Punch-out deadline: " + Cfg.deadlineLabel(this) + ", nagging every " + Cfg.repeat(this) + " min"
        tvStatus.text = when {
            pout != "--:--:--" -> "Punched out at $pout. Nothing left to do today."
            pin != "--:--:--" -> "Still punched in. Punch out before you leave."
            else -> "Tap Menu to open the portal or record your punch-out."
        }
        val read = Cfg.lastRead(this)
        val last = Cfg.lastResult(this)
        val parts = mutableListOf<String>()
        if (read.isNotBlank()) parts.add("Read from Chrome at $read")
        if (last.isNotBlank()) parts.add("Last check: $last")
        if (!readerEnabled()) parts.add("Screen reader off — enable it from the Menu")
        findViewById<TextView>(R.id.tvLastCheck).text = parts.joinToString(" · ")
    }

    private fun markPunchedOut() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Cfg.setPunchOut(this, time)
        Cfg.setPunchDay(this, today())
        if (Cfg.punchIn(this).isBlank()) Cfg.setPunchIn(this, "09:12:33")
        refreshStrip()
        Toast.makeText(this, "Punch-out recorded at $time", Toast.LENGTH_SHORT).show()
    }

    private fun readerEnabled(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName, ignoreCase = true)
    }

    private fun showMenu() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_menu, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.sheetPunchIn).text = Cfg.punchIn(this).ifBlank { "--:--:--" }
        view.findViewById<TextView>(R.id.sheetPunchOut).text = Cfg.punchOut(this).ifBlank { "--:--:--" }

        sections.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                sheet.dismiss()
                openPortal()
            }
        }
        view.findViewById<Button>(R.id.sheetOpenPortal).setOnClickListener {
            sheet.dismiss()
            openPortal()
        }
        view.findViewById<Button>(R.id.sheetPunch).setOnClickListener {
            sheet.dismiss()
            markPunchedOut()
        }
        view.findViewById<Button>(R.id.sheetReader).setOnClickListener {
            sheet.dismiss()
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Toast.makeText(this, "Turn on TTBS-HRMS screen reader", Toast.LENGTH_LONG).show()
        }
        view.findViewById<Button>(R.id.sheetLoginTest).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, LoginTestActivity::class.java))
        }
        view.findViewById<Button>(R.id.sheetAlarm).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("unverified", true))
        }
        sheet.show()
    }

    /** Chrome Custom Tab: Chrome's engine, so Cloudflare passes and the session carries over. */
    private fun openPortal() {
        val uri = Uri.parse(Cfg.url(this))
        val opened = runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setToolbarColor(android.graphics.Color.parseColor("#0F2A55"))
                .setNavigationBarColor(android.graphics.Color.parseColor("#0F2A55"))
                .build()
                .launchUrl(this, uri)
        }.isSuccess
        if (!opened) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure { Toast.makeText(this, "No browser found to open the portal.", Toast.LENGTH_LONG).show() }
        }
    }
}
