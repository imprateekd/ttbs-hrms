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
    private lateinit var btnBreak: Button
    private lateinit var breakFooter: View
    private lateinit var dotActive: View
    private lateinit var dotBreak: View
    private lateinit var lblBreak: TextView
    private var pulse: android.animation.ObjectAnimator? = null

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
        btnBreak = findViewById(R.id.btnBreak)
        breakFooter = findViewById(R.id.breakFooter)
        dotActive = findViewById(R.id.dotActive)
        dotBreak = findViewById(R.id.dotBreak)
        lblBreak = findViewById(R.id.lblBreak)

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
        pulse?.pause()
    }

    override fun onStart() {
        super.onStart()
        pulse?.resume()
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
        val punchedOut = pout.isNotBlank()
        val limit = Cfg.breakLimitMin(this)
        val used = Day.breakTodaySecs(this)

        tvBreak.text = Fmt.durationTicking(used) + " / " + limit + "m"
        tvBreak.setTextColor(if (onBreak) 0xFFFFB020.toInt() else 0xFFB9CBEA.toInt())
        lblBreak.setTextColor(if (onBreak) 0xFFFFB020.toInt() else 0xFF7F93B3.toInt())

        // while on a break the whole row reads as active; otherwise it stays quiet
        breakFooter.setBackgroundResource(
            if (onBreak) R.drawable.bg_break_active else R.drawable.bg_break_idle
        )
        btnBreak.text = if (onBreak) "End break" else "Start break"
        btnBreak.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (onBreak) 0xFF0F766E.toInt() else 0xFFFFB020.toInt()
        )
        btnBreak.setTextColor(if (onBreak) 0xFFFFFFFF.toInt() else 0xFF3A2600.toInt())

        // one pulse, on whatever is actually running: the break, or the working clock
        dotBreak.visibility = if (onBreak) View.VISIBLE else View.GONE
        dotActive.visibility = if (!onBreak && has && !punchedOut) View.VISIBLE else View.GONE
        updatePulse()
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

    /** The pulse follows the live thing, and stops when nothing is running. */
    private fun updatePulse() {
        val target = when {
            dotBreak.visibility == View.VISIBLE -> dotBreak
            dotActive.visibility == View.VISIBLE -> dotActive
            else -> null
        }
        if (target == null) {
            pulse?.cancel()
            pulse = null
            dotBreak.alpha = 1f
            dotActive.alpha = 1f
            return
        }
        if (pulse?.isRunning == true) return
        pulse = android.animation.ObjectAnimator.ofFloat(target, "alpha", 1f, 0.3f).apply {
            duration = 800
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
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

        v.findViewById<View>(R.id.openA11y).setOnClickListener { openAccessibility() }
        v.findViewById<View>(R.id.openNotif).setOnClickListener { openNotifications() }
        v.findViewById<View>(R.id.openExact).setOnClickListener { openExactAlarms() }
        v.findViewById<View>(R.id.openOverlay).setOnClickListener { openOverlay() }

        v.findViewById<View>(R.id.rowA11y).setOnClickListener { openAccessibility() }
        v.findViewById<View>(R.id.rowNotif).setOnClickListener { openNotifications() }
        v.findViewById<View>(R.id.rowExact).setOnClickListener { openExactAlarms() }
        v.findViewById<View>(R.id.rowOverlay).setOnClickListener { openOverlay() }
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

    /** Repaints the list, the bar and the button from live permission state. */
    private fun paintPermSheet() {
        val v = permSheetView ?: return
        val all = Perms.all(this)
        val count = Perms.count(this)

        v.findViewById<TextView>(R.id.tvPermTitle).text = if (all) "All set" else "Permissions"
        v.findViewById<TextView>(R.id.tvPermCount).text = count.toString() + " of 4"
        v.findViewById<ProgressBar>(R.id.pbPerm).progress = count

        fun row(tick: Int, name: Int, open: Int, granted: Boolean) {
            v.findViewById<TextView>(tick).text = if (granted) "✓" else "○"
            v.findViewById<TextView>(tick).setTextColor(
                if (granted) 0xFF35D07F.toInt() else 0x66FFFFFF
            )
            v.findViewById<TextView>(name).setTypeface(
                null,
                if (granted) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD
            )
            v.findViewById<View>(open).visibility = if (granted) View.GONE else View.VISIBLE
        }
        row(R.id.tickA11y, R.id.nameA11y, R.id.openA11y, Perms.accessibility(this))
        row(R.id.tickNotif, R.id.nameNotif, R.id.openNotif, Perms.notifications(this))
        row(R.id.tickExact, R.id.nameExact, R.id.openExact, Perms.exactAlarms(this))
        row(R.id.tickOverlay, R.id.nameOverlay, R.id.openOverlay, Perms.overlay(this))

        val go = v.findViewById<Button>(R.id.btnPermContinue)
        go.text = if (all) "Continue" else "Open what is missing"
        go.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (all) 0xFF2F7DFF.toInt() else 0xFF16263F.toInt()
        )
        go.setTextColor(if (all) 0xFFFFFFFF.toInt() else 0xFF5F7D9E.toInt())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        paintPermSheet()
    }

    private fun openAccessibility() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun openNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        } else {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
    }

    private fun openExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + packageName)))
            }
        }
    }

    private fun openOverlay() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)))
        }
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
