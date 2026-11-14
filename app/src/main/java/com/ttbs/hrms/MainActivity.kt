package com.ttbs.hrms

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var btnTime: Button
    private lateinit var etRepeat: EditText
    private lateinit var etUrl: EditText
    private lateinit var switchDesktop: SwitchCompat
    private lateinit var switchVerify: SwitchCompat
    private lateinit var switchAutoLogin: SwitchCompat
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etOutRegex: EditText
    private lateinit var etInRegex: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Notify.ensure(this)

        switchEnabled = findViewById(R.id.switchEnabled)
        btnTime = findViewById(R.id.btnTime)
        etRepeat = findViewById(R.id.etRepeat)
        etUrl = findViewById(R.id.etUrl)
        switchDesktop = findViewById(R.id.switchDesktop)
        switchVerify = findViewById(R.id.switchVerify)
        switchAutoLogin = findViewById(R.id.switchAutoLogin)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etOutRegex = findViewById(R.id.etOutRegex)
        etInRegex = findViewById(R.id.etInRegex)
        tvStatus = findViewById(R.id.tvStatus)

        load()

        btnTime.setOnClickListener { pickTime() }

        switchEnabled.setOnCheckedChangeListener { _, isOn ->
            Cfg.setEnabled(this, isOn)
            save()
            if (isOn) {
                AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
                Toast.makeText(this, "Armed for " + Cfg.deadlineLabel(this) + " daily", Toast.LENGTH_SHORT).show()
            } else {
                AlarmScheduler.cancel(this)
                Toast.makeText(this, "Disarmed", Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.btnOpenDashboard).setOnClickListener { openDashboard() }
        findViewById<Button>(R.id.btnSaveInSample).setOnClickListener { openDashboard("Use 'Save as STILL IN' at the bottom of the dashboard screen.") }
        findViewById<Button>(R.id.btnSaveOutSample).setOnClickListener { openDashboard("Use 'Save as PUNCHED OUT' at the bottom of the dashboard screen.") }

        findViewById<Button>(R.id.btnNotif).setOnClickListener { askNotifications() }
        findViewById<Button>(R.id.btnExact).setOnClickListener { askExactAlarm() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { askOverlay() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { askBattery() }

        findViewById<Button>(R.id.btnCheckNow).setOnClickListener {
            save()
            startActivity(Intent(this, CheckActivity::class.java))
        }
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            save()
            Cfg.setEnabled(this, true)
            switchEnabled.isChecked = true
            AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            val when_ = android.text.format.DateFormat.getTimeFormat(this)
                .format(AlarmScheduler.nextDeadlineMillis(this))
            Toast.makeText(this, "Next check at $when_", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            AlarmScheduler.cancel(this)
            Cfg.setEnabled(this, false)
            switchEnabled.isChecked = false
            refreshStatus()
        }

        askPermissionsQuietly()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    private fun load() {
        switchEnabled.isChecked = Cfg.enabled(this)
        btnTime.text = Cfg.deadlineLabel(this)
        etRepeat.setText(Cfg.repeat(this).toString())
        etUrl.setText(Cfg.url(this))
        switchDesktop.isChecked = Cfg.desktop(this)
        switchVerify.isChecked = Cfg.verify(this)
        switchAutoLogin.isChecked = Cfg.autoLogin(this)
        etUser.setText(Cfg.username(this))
        etPass.setText(Cfg.password(this))
        etOutRegex.setText(Cfg.outRegex(this))
        etInRegex.setText(Cfg.inRegex(this))
        refreshStatus()
    }

    private fun save() {
        Cfg.setEnabled(this, switchEnabled.isChecked)
        Cfg.setRepeat(this, etRepeat.text.toString().toIntOrNull() ?: 2)
        Cfg.setUrl(this, etUrl.text.toString())
        Cfg.setDesktop(this, switchDesktop.isChecked)
        Cfg.setVerify(this, switchVerify.isChecked)
        Cfg.setAutoLogin(this, switchAutoLogin.isChecked)
        Cfg.setUsername(this, etUser.text.toString())
        if (etPass.text.toString() != Cfg.password(this)) Cfg.setPassword(this, etPass.text.toString())
        Cfg.setRegex(this, etOutRegex.text.toString(), etInRegex.text.toString())
    }

    private fun pickTime() {
        val current = Cfg.deadline(this)
        TimePickerDialog(this, { _, h, m ->
            Cfg.setDeadline(this, h * 60 + m)
            btnTime.text = Cfg.deadlineLabel(this)
            if (Cfg.enabled(this)) {
                AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            }
            refreshStatus()
        }, current / 60, current % 60, false).show()
    }

    private fun openDashboard(toast: String? = null) {
        save()
        if (toast != null) Toast.makeText(this, toast, Toast.LENGTH_LONG).show()
        // Chrome Custom Tab: Chrome's engine, so Cloudflare's check passes and the portal
        // session is the one you already have. A WebView cannot get past that check.
        val uri = Uri.parse(Cfg.url(this))
        val opened = runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setToolbarColor(android.graphics.Color.parseColor("#0F2A55"))
                .setSecondaryToolbarColor(android.graphics.Color.parseColor("#0B5FFF"))
                .setNavigationBarColor(android.graphics.Color.parseColor("#0F2A55"))
                .setNavigationBarDividerColor(android.graphics.Color.parseColor("#0B5FFF"))
                .build()
                .launchUrl(this, uri)
        }.isSuccess
        if (!opened) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure {
                    Toast.makeText(this, "No browser found to open the portal.", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun refreshStatus() {
        val sb = StringBuilder()
        sb.append(if (Cfg.enabled(this)) "Reminder: ON" else "Reminder: OFF").append("\n")
        sb.append("Daily check at ").append(Cfg.deadlineLabel(this))
            .append(", nagging every ").append(Cfg.repeat(this)).append(" min\n")
        if (Cfg.enabled(this)) {
            val next = android.text.format.DateFormat.getDateFormat(this)
                .format(AlarmScheduler.nextDeadlineMillis(this))
            val time = android.text.format.DateFormat.getTimeFormat(this)
                .format(AlarmScheduler.nextDeadlineMillis(this))
            sb.append("Next alarm: ").append(next).append(" ").append(time).append("\n")
        }
        sb.append("Samples saved: ")
            .append(if (Cfg.inSample(this).isNotBlank()) "still-in ✓ " else "still-in ✗ ")
            .append(if (Cfg.outSample(this).isNotBlank()) "punched-out ✓" else "punched-out ✗")
            .append("\n")
        val last = Cfg.lastResult(this)
        if (last.isNotBlank()) sb.append("Last check: ").append(last)
        tvStatus.text = sb.toString()
    }

    // ---- permissions ----

    private fun askPermissionsQuietly() {
        askNotifications()
        askLocation()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        } else {
            Toast.makeText(this, "Notifications already allowed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                102
            )
        }
    }

    private fun askExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (am.canScheduleExactAlarms()) {
                Toast.makeText(this, "Exact alarms already allowed", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    startActivity(Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
        } else {
            Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askOverlay() {
        if (SysSettings.canDrawOverlays(this)) {
            Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
        } else {
            runCatching {
                startActivity(Intent(SysSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun askBattery() {
        runCatching {
            startActivity(Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
