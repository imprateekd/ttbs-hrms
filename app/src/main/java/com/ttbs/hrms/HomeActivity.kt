package com.ttbs.hrms

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Design C: native home screen, punch status on top, tiles into the portal. */
class HomeActivity : AppCompatActivity() {

    private lateinit var tvPunchIn: TextView
    private lateinit var tvPunchOut: TextView
    private lateinit var tvStatus: TextView

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

    private fun openPortal() {
        startActivity(Intent(this, PortalActivity::class.java))
    }

    private fun readPunchStatus() {
        // GeckoView in this build has no JS-evaluation API on GeckoSession, so the punch
        // strip cannot be filled automatically yet. Open the portal instead of guessing.
        tvStatus.text = "Open the portal to see your punch times."
        openPortal()
    }




}
