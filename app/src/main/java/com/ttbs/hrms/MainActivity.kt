package com.ttbs.hrms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat

/** Settings, in the grouped-list design: Today, Reminders, Access, Account. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Cfg.ensureToday(this)

        findViewById<Button>(R.id.btn12).setOnClickListener { setFormat(false) }
        findViewById<Button>(R.id.btn24).setOnClickListener { setFormat(true) }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnInstagram).setOnClickListener {
            openLink("https://instagram.com/imprateekd")
        }
        findViewById<View>(R.id.btnWhatsapp).setOnClickListener {
            openLink("https://wa.me/919039882044")
        }

        findViewById<View>(R.id.rowResetBreak).setOnClickListener { confirmResetBreak() }

        // I1: the value itself opens a keypad; the +/- buttons stay for nudges
        findViewById<TextView>(R.id.tvRepeat).setOnClickListener {
            askNumber("Nag again", "Minutes between reminders", Cfg.repeat(this), 1, 60, " min") {
                Cfg.setRepeat(this, it)
                refresh()
            }
        }
        findViewById<TextView>(R.id.tvBreakLimit).setOnClickListener {
            askNumber("Break limit", "Minutes of break allowed per day", Cfg.breakLimitMin(this), 5, 240, " min") {
                Cfg.setBreakLimitMin(this, it)
                refresh()
            }
        }
        findViewById<TextView>(R.id.tvDeadline).setOnClickListener { askDeadline() }
        findViewById<View>(R.id.rowClear).setOnClickListener { confirmClear() }

        findViewById<Button>(R.id.btnDeadlineMinus).setOnClickListener { stepDeadline(-15) }
        findViewById<Button>(R.id.btnDeadlinePlus).setOnClickListener { stepDeadline(15) }
        findViewById<Button>(R.id.btnRepeatMinus).setOnClickListener { stepRepeat(-1) }
        findViewById<Button>(R.id.btnRepeatPlus).setOnClickListener { stepRepeat(1) }
        findViewById<Button>(R.id.btnBreakMinus).setOnClickListener { stepBreakLimit(-5) }
        findViewById<Button>(R.id.btnBreakPlus).setOnClickListener { stepBreakLimit(5) }

        findViewById<SwitchCompat>(R.id.switchEnabled).setOnCheckedChangeListener { _, checked ->
            Cfg.setEnabled(this, checked)
            if (checked) AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
            else AlarmScheduler.cancel(this)
        }

        findViewById<View>(R.id.rowA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.rowNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        findViewById<View>(R.id.rowExact).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + packageName)))
            }
        }
        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val pin = Cfg.punchIn(this)
        findViewById<TextView>(R.id.tvSetPin).text = Fmt.time(this, pin)
        findViewById<TextView>(R.id.tvSetPout).text = Fmt.time(this, Cfg.punchOut(this))
        findViewById<TextView>(R.id.tvSetActive).text = Fmt.duration(Day.activeSecs(this))
        findViewById<TextView>(R.id.tvSetBreak).text =
            Fmt.duration(Day.breakTodaySecs(this)) + " / " + Cfg.breakLimitMin(this) + "m"

        val mins = Cfg.deadline(this)
        findViewById<TextView>(R.id.tvDeadline).text = Fmt.deadlineLabel(this)
        findViewById<TextView>(R.id.tvRepeat).text = Cfg.repeat(this).toString() + "m"
        findViewById<TextView>(R.id.tvBreakLimit).text = Cfg.breakLimitMin(this).toString() + "m"

        findViewById<SwitchCompat>(R.id.switchEnabled).isChecked = Cfg.enabled(this)
        paintFormat()

        findViewById<TextView>(R.id.tvA11y).text = onOff(Perms.accessibility(this))
        findViewById<TextView>(R.id.tvNotif).text = onOff(Perms.notifications(this))
        findViewById<TextView>(R.id.tvExact).text = onOff(Perms.exactAlarms(this))
        findViewById<TextView>(R.id.tvOverlay).text = onOff(Perms.overlay(this))

        findViewById<TextView>(R.id.tvEmpId).text =
            Cfg.username(this).ifBlank { "not saved" }
    }

    /** A keypad, not just steppers - typing 45 is faster than nine taps. */
    private fun askNumber(
        title: String,
        hint: String,
        current: Int,
        min: Int,
        max: Int,
        unit: String,
        onSet: (Int) -> Unit
    ) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
            setHint(hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(hint + " (" + min + "-" + max + unit + ")")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: current
                onSet(v.coerceIn(min, max))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askDeadline() {
        val mins = Cfg.deadline(this)
        val picker = android.app.TimePickerDialog(
            this,
            { _, h, m ->
                Cfg.setDeadline(this, h * 60 + m)
                if (Cfg.enabled(this)) AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
                refresh()
            },
            mins / 60, mins % 60, Cfg.hour24(this)
        )
        picker.setTitle("Punch-out deadline")
        picker.show()
    }

    private fun setFormat(hour24: Boolean) {
        Cfg.setHour24(this, hour24)
        refresh()
    }

    private fun paintFormat() {
        val h24 = Cfg.hour24(this)
        fun style(id: Int, on: Boolean) {
            val b = findViewById<Button>(id)
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (on) 0xFF2F7DFF.toInt() else 0x1FFFFFFF
            )
            b.setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFF8FA6C8.toInt())
        }
        style(R.id.btn12, !h24)
        style(R.id.btn24, h24)
    }

    private fun openLink(url: String) {
        val ok = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
        if (!ok) Toast.makeText(this, url, Toast.LENGTH_LONG).show()
    }

    private fun onOff(v: Boolean) = if (v) "on" else "off"

    private fun stepDeadline(delta: Int) {
        Cfg.setDeadline(this, ((Cfg.deadline(this) + delta) % 1440 + 1440) % 1440)
        if (Cfg.enabled(this)) AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
        refresh()
    }

    private fun stepRepeat(delta: Int) {
        Cfg.setRepeat(this, Cfg.repeat(this) + delta)
        refresh()
    }

    private fun stepBreakLimit(delta: Int) {
        Cfg.setBreakLimitMin(this, Cfg.breakLimitMin(this) + delta)
        refresh()
    }

    private fun confirmResetBreak() {
        val used = Day.hms(Day.breakTodaySecs(this))
        AlertDialog.Builder(this)
            .setTitle("Reset break counter?")
            .setMessage(
                "This clears the $used of break recorded today and starts the " +
                    Cfg.breakLimitMin(this) + "-minute allowance again. Your punch times and " +
                    "Active Hours are not touched."
            )
            .setPositiveButton("Reset") { _, _ ->
                Day.endBreak(this)
                Cfg.setBreakTotalSec(this, 0L)
                AlarmScheduler.cancelBreak(this)
                Toast.makeText(this, "Break counter reset", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear saved details?")
            .setMessage("Your employee ID and password will be removed from this phone.")
            .setPositiveButton("Clear") { _, _ ->
                Cfg.setUsername(this, "")
                Cfg.setPassword(this, "")
                Cfg.setAutoLogin(this, false)
                Toast.makeText(this, "Details cleared", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
