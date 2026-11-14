package com.ttbs.hrms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * The only screen. Three big timers, a break line beneath them, the login form, and two actions:
 * Check Login Time (read the portal and come straight back) and Sign In (open the portal).
 *
 * The app never authenticates by itself — the server enforces Cloudflare's token — so both actions
 * hand off to Chrome, where the accessibility service fills the form and presses the button.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var saveRow: View
    private lateinit var cbSave: CheckBox
    private lateinit var tvSig: TextView
    private lateinit var sigRule: View
    private lateinit var strip: View
    private lateinit var tvPin: TextView
    private lateinit var tvPout: TextView
    private lateinit var tvActive: TextView
    private lateinit var tvBreak: TextView
    private lateinit var pbBreak: ProgressBar
    private lateinit var btnBreak: Button
    private lateinit var breakFooter: View

    private val handler = Handler(Looper.getMainLooper())
    private var saveRowShown = false
    private var pendingAction: (() -> Unit)? = null
    private var sheet: BottomSheetDialog? = null
    private var permSheetView: View? = null
    private val signature = "by Prateek"

    private val tick = object : Runnable {
        override fun run() {
            refreshStrip()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        saveRow = findViewById(R.id.saveRow)
        cbSave = findViewById(R.id.cbSave)
        tvSig = findViewById(R.id.tvSig)
        sigRule = findViewById(R.id.sigRule)
        strip = findViewById(R.id.strip)
        tvPin = findViewById(R.id.tvPin)
        tvPout = findViewById(R.id.tvPout)
        tvActive = findViewById(R.id.tvActive)
        tvBreak = findViewById(R.id.tvBreak)
        pbBreak = findViewById(R.id.pbBreak)
        btnBreak = findViewById(R.id.btnBreak)
        breakFooter = findViewById(R.id.breakFooter)

        btnBreak.setOnClickListener { toggleBreak() }
        // C3: tapping the times flips the whole app between 12 and 24 hour
        findViewById<View>(R.id.timerRow).setOnClickListener {
            Cfg.setHour24(this, !Cfg.hour24(this))
            refreshStrip()
            Toast.makeText(
                this,
                if (Cfg.hour24(this)) "24-hour time" else "12-hour time",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<Button>(R.id.btnOnboardSet).setOnClickListener { askPunchOutTime() }
        findViewById<TextView>(R.id.btnOnboardClose).setOnClickListener {
            Cfg.setOnboarded(this, true)
            findViewById<View>(R.id.onboardCard).visibility = View.GONE
        }
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkLoginTime() }

        if (Cfg.autoLogin(this) && Cfg.username(this).isNotBlank()) {
            etUser.setText(Cfg.username(this))
            etPass.setText(Cfg.password(this))
            saveRowShown = true
            saveRow.visibility = View.VISIBLE
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!saveRowShown && !s.isNullOrEmpty()) {
                    saveRowShown = true
                    saveRow.alpha = 0f
                    saveRow.visibility = View.VISIBLE
                    saveRow.animate().alpha(1f).setDuration(260).start()
                }
            }
        }
        etUser.addTextChangedListener(watcher)
        etPass.addTextChangedListener(watcher)

        typeSignature()
    }

    override fun onResume() {
        super.onResume()
        Cfg.ensureToday(this)
        AlarmScheduler.scheduleMidnight(this)
        refreshStrip()
        paintPermSheet()
        findViewById<View>(R.id.onboardCard).visibility =
            if (Cfg.onboarded(this)) View.GONE else View.VISIBLE
        handler.removeCallbacks(tick)
        handler.post(tick)

        // came back from a settings screen: if the action was waiting on permissions, run it now
        val pending = pendingAction
        if (pending != null && Perms.all(this)) {
            pendingAction = null
            sheet?.dismiss()
            sheet = null
            pending.invoke()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- timers

    private fun refreshStrip() {
        val pin = Cfg.punchIn(this)
        val pout = Cfg.punchOut(this)
        val has = pin.isNotBlank()
        // the card is always on screen: with no readings yet it shows --:-- rather than vanishing
        strip.visibility = View.VISIBLE

        tvPin.text = if (has) Fmt.time(this, pin) else "--:--"
        tvPout.text = if (pout.isNotBlank()) Fmt.time(this, pout) else "--:--"
        tvActive.text = if (has) Fmt.duration(Day.activeSecs(this)) else "--:--"

        val onBreak = Day.isOnBreak(this)
        val limit = Cfg.breakLimitMin(this)
        val used = Day.breakTodaySecs(this)
        tvBreak.text = Fmt.durationTicking(used) + " / " + limit + "m"
        tvBreak.setTextColor(if (onBreak) 0xFFFFB020.toInt() else 0xFFB9CBEA.toInt())
        pbBreak.progress = ((used * 100) / (limit * 60L)).coerceIn(0L, 100L).toInt()
        // the footer goes amber while a break is running, so the state is visible at a glance
        breakFooter.setBackgroundColor(if (onBreak) 0x1FFFB020 else 0x05FFFFFF)
        btnBreak.text = if (onBreak) "End" else "Start"
        btnBreak.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (onBreak) 0xFF0F766E.toInt() else 0xFFFFB020.toInt()
        )
        btnBreak.setTextColor(if (onBreak) 0xFFFFFFFF.toInt() else 0xFF3A2600.toInt())
    }

    /** First run: the punch-out time the reminder should ring at. */
    private fun askPunchOutTime() {
        val mins = Cfg.deadline(this)
        val picker = android.app.TimePickerDialog(
            this,
            { _, h, m ->
                Cfg.setDeadline(this, h * 60 + m)
                Cfg.setEnabled(this, true)
                AlarmScheduler.scheduleAt(this, AlarmScheduler.nextDeadlineMillis(this))
                Cfg.setOnboarded(this, true)
                findViewById<View>(R.id.onboardCard).visibility = View.GONE
                Toast.makeText(this, "Reminder set for " + Fmt.deadlineLabel(this), Toast.LENGTH_SHORT).show()
            },
            mins / 60, mins % 60, Cfg.hour24(this)
        )
        picker.setTitle("Punch-out time")
        picker.show()
    }

    private fun toggleBreak() {
        if (Day.isOnBreak(this)) {
            Day.endBreak(this)
            AlarmScheduler.cancelBreak(this)
        } else {
            Day.startBreak(this)
            AlarmScheduler.scheduleBreak(this)
        }
        refreshStrip()
    }

    // ---------------------------------------------------------------- actions

    private fun signIn() {
        if (!validateFields()) return
        withPermissions {
            saveOrClear()
            openPortal()
        }
    }

    /** Reads the portal and comes straight back, without leaving you signed in on this screen. */
    private fun checkLoginTime() {
        if (!validateFields()) return
        // The check needs the details stored so the service can type them into Chrome.
        // Save them here rather than demanding a previous Sign In.
        if (!cbSave.isChecked) {
            Toast.makeText(this, "Tick Save details so the app can fill your login", Toast.LENGTH_LONG).show()
            return
        }
        withPermissions {
            saveOrClear()
            openPortal()
        }
    }

    private fun validateFields(): Boolean {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter your employee ID and password", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun withPermissions(action: () -> Unit) {
        if (Perms.all(this)) {
            action()
            return
        }
        pendingAction = action
        showPermissionSheet()
    }

    private fun saveOrClear() {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        if (cbSave.isChecked) {
            Cfg.setUsername(this, user)
            Cfg.setPassword(this, pass)
            Cfg.setAutoLogin(this, true)
        } else {
            Cfg.setUsername(this, "")
            Cfg.setPassword(this, "")
            Cfg.setAutoLogin(this, false)
        }
        Cfg.setPendingReturn(this, true)
    }

    private fun showPermissionSheet() {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_permission, null)
        d.setContentView(v)
        sheet = d
        permSheetView = v
        paintPermSheet()

        v.findViewById<View>(R.id.rowA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        v.findViewById<View>(R.id.rowNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41
                )
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        v.findViewById<View>(R.id.rowExact).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + packageName)))
            }
        }
        v.findViewById<View>(R.id.rowOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)))
        }
        v.findViewById<Button>(R.id.btnPermContinue).setOnClickListener {
            if (Perms.all(this)) {
                val action = pendingAction
                pendingAction = null
                d.dismiss(); sheet = null
                action?.invoke()
            } else {
                paintPermSheet()
                Toast.makeText(this, "Still missing a permission", Toast.LENGTH_SHORT).show()
            }
        }
        v.findViewById<Button>(R.id.btnPermLater).setOnClickListener {
            pendingAction = null
            d.dismiss(); sheet = null; permSheetView = null
        }
        d.setOnDismissListener { if (permSheetView === v) permSheetView = null }
        d.show()
    }

    /** Repaints the tick list from live permission state. */
    private fun paintPermSheet() {
        val v = permSheetView ?: return
        v.findViewById<TextView>(R.id.tvPermTitle).text =
            if (Perms.all(this)) "All set" else "A few permissions first"
        v.findViewById<TextView>(R.id.tvPermCount).text =
            Perms.count(this).toString() + " of 4 enabled"
        v.findViewById<TextView>(R.id.tickA11y).text = if (Perms.accessibility(this)) "✓" else "○"
        v.findViewById<TextView>(R.id.tickNotif).text = if (Perms.notifications(this)) "✓" else "○"
        v.findViewById<TextView>(R.id.tickExact).text = if (Perms.exactAlarms(this)) "✓" else "○"
        v.findViewById<TextView>(R.id.tickOverlay).text = if (Perms.overlay(this)) "✓" else "○"
        v.findViewById<Button>(R.id.btnPermContinue).text =
            if (Perms.all(this)) "Continue" else "Check again"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        paintPermSheet()
    }

    private fun openPortal() {
        val uri = Uri.parse(Cfg.url(this))
        val opened = runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setToolbarColor(android.graphics.Color.parseColor("#0F2A55"))
                .setNavigationBarColor(android.graphics.Color.parseColor("#08132A"))
                .build()
                .launchUrl(this, uri)
        }.isSuccess
        if (!opened) runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    // ---------------------------------------------------------------- signature

    private fun typeSignature() {
        var i = 0
        fun step() {
            i++
            tvSig.text = signature.substring(0, i)
            if (i >= signature.length) {
                handler.postDelayed({ drawRule() }, 140)
                return
            }
            handler.postDelayed({ step() }, (50 + (0..65).random()).toLong())
        }
        handler.postDelayed({ step() }, 420)
    }

    private fun drawRule() {
        val target = (120 * resources.displayMetrics.density).toInt()
        val anim = android.animation.ValueAnimator.ofInt(0, target)
        anim.duration = 900
        anim.addUpdateListener { a ->
            val p = sigRule.layoutParams
            p.width = a.animatedValue as Int
            sigRule.layoutParams = p
        }
        anim.start()
    }
}
