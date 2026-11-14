package com.ttbs.hrms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * First screen: the portal's own look — logo, employee ID, password — and the signature
 * "by Prateek" typing itself out underneath, then underlined.
 *
 * The app cannot authenticate on its own (the server enforces Cloudflare's token), so Sign In
 * saves the details locally and hands off to Chrome, where the accessibility service fills the
 * form and presses the button while Chrome passes the captcha by itself.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var saveRow: View
    private lateinit var cbSave: CheckBox
    private lateinit var tvSig: TextView
    private lateinit var strip: View
    private lateinit var tvPin: TextView
    private lateinit var tvPout: TextView
    private lateinit var tvActive: TextView
    private lateinit var tvBreak: TextView
    private lateinit var pbBreak: ProgressBar
    private lateinit var btnBreak: Button
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStrip()
            ticker.postDelayed(this, 1000)
        }
    }
    private lateinit var sigRule: View

    private val handler = Handler(Looper.getMainLooper())
    private var saveRowShown = false
    private var awaitingPermission = false
    private val signature = "by Prateek"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        saveRow = findViewById(R.id.saveRow)
        cbSave = findViewById(R.id.cbSave)
        tvSig = findViewById(R.id.tvSig)
        strip = findViewById(R.id.strip)
        tvPin = findViewById(R.id.tvPin)
        tvPout = findViewById(R.id.tvPout)
        tvActive = findViewById(R.id.tvActive)
        tvBreak = findViewById(R.id.tvBreak)
        pbBreak = findViewById(R.id.pbBreak)
        btnBreak = findViewById(R.id.btnBreak)
        btnBreak.setOnClickListener { toggleBreak() }
        sigRule = findViewById(R.id.sigRule)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!saveRowShown && !s.isNullOrEmpty()) {
                    saveRowShown = true
                    showSaveOption()
                }
            }
        }
        etUser.addTextChangedListener(watcher)
        etPass.addTextChangedListener(watcher)

        if (Cfg.autoLogin(this) && Cfg.username(this).isNotBlank()) {
            etUser.setText(Cfg.username(this))
            etPass.setText(Cfg.password(this))
            saveRowShown = true
            saveRow.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn() }
        findViewById<TextView>(R.id.tvSettingsLink).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        typeSignature()
    }

    override fun onResume() {
        super.onResume()
        Cfg.ensureToday(this)
        AlarmScheduler.scheduleMidnight(this)
        refreshStrip()
        ticker.removeCallbacks(tick)
        ticker.post(tick)
        // came back from the accessibility settings: carry on with the sign-in
        if (awaitingPermission && ReaderHelp.enabled(this)) {
            awaitingPermission = false
            proceed()
        }
    }

    override fun onPause() {
        super.onPause()
        ticker.removeCallbacks(tick)
    }

    /** Three segments above, the break line beneath - all derived, nothing accumulated. */
    private fun refreshStrip() {
        val pin = Cfg.punchIn(this)
        val pout = Cfg.punchOut(this)
        val hasTimes = pin.isNotBlank()
        strip.visibility = if (hasTimes) View.VISIBLE else View.GONE

        tvPin.text = if (hasTimes) pin else "--:--:--"
        tvPout.text = if (pout.isNotBlank()) pout else "--:--:--"
        tvActive.text = if (hasTimes) Day.hms(Day.activeSecs(this)) else "--:--:--"

        val onBreak = Day.isOnBreak(this)
        val limit = Cfg.breakLimitMin(this)
        val used = Day.breakTodaySecs(this)
        tvBreak.text = Day.hms(used) + " / " + limit + "m"
        tvBreak.setTextColor(if (onBreak) 0xFFFFB020.toInt() else 0xFFB9CBEA.toInt())
        pbBreak.progress = ((used * 100) / (limit * 60L)).coerceIn(0L, 100L).toInt()
        btnBreak.text = if (onBreak) "End" else "Start"
        btnBreak.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (onBreak) 0xFF0F766E.toInt() else 0xFFFFB020.toInt()
        )
        btnBreak.setTextColor(if (onBreak) 0xFFFFFFFF.toInt() else 0xFF3A2600.toInt())
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

    /** The save option appears the first time the user types anything. */
    private fun showSaveOption() {
        saveRow.alpha = 0f
        saveRow.visibility = View.VISIBLE
        saveRow.animate().alpha(1f).setDuration(280).start()
    }

    private fun signIn() {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter your employee ID and password", Toast.LENGTH_SHORT).show()
            return
        }

        if (!ReaderHelp.enabled(this)) {
            awaitingPermission = true
            showPermissionSheet()
            return
        }
        proceed()
    }

    /** The "one more step" sheet, shown the moment Sign In is tapped without accessibility. */
    private fun showPermissionSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_permission, null)
        sheet.setContentView(view)
        view.findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            sheet.dismiss()
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        view.findViewById<Button>(R.id.btnNotNow).setOnClickListener {
            awaitingPermission = false
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun proceed() {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()

        if (cbSave.isChecked) {
            Cfg.setUsername(this, user)
            Cfg.setPassword(this, pass)
            Cfg.setAutoLogin(this, true)
            Cfg.setPunchIn(this, "")
            Cfg.setPunchOut(this, "")
            Toast.makeText(this, "Saved on this phone (encrypted)", Toast.LENGTH_SHORT).show()
        } else {
            // Unticked: forget everything, so the next launch starts with empty fields.
            Cfg.setUsername(this, "")
            Cfg.setPassword(this, "")
            Cfg.setAutoLogin(this, false)
        }

        openPortal()
        finish()
    }

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
        }
    }

    /** Types "by Prateek" with uneven delays, then draws the underline. */
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
        val target = (132 * resources.displayMetrics.density).toInt()
        val params = sigRule.layoutParams
        params.width = 0
        sigRule.layoutParams = params
        val anim = android.animation.ValueAnimator.ofInt(0, target)
        anim.duration = 900
        anim.addUpdateListener { a ->
            val p = sigRule.layoutParams
            p.width = a.animatedValue as Int
            sigRule.layoutParams = p
        }
        anim.start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
