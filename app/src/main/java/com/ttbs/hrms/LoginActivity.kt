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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings

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
    private lateinit var sigRule: View
    private lateinit var tvReader: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var saveRowShown = false
    private val signature = "by Prateek"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // already set up: go straight to the status screen
        if (Cfg.username(this).isNotBlank() && Cfg.password(this).isNotBlank()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        saveRow = findViewById(R.id.saveRow)
        cbSave = findViewById(R.id.cbSave)
        tvSig = findViewById(R.id.tvSig)
        sigRule = findViewById(R.id.sigRule)
        tvReader = findViewById(R.id.tvReader)

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

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn() }
        findViewById<Button>(R.id.btnReaderHelp).setOnClickListener { ReaderHelp.show(this) }

        typeSignature()
    }

    override fun onResume() {
        super.onResume()
        tvReader.visibility = if (ReaderHelp.enabled(this)) View.GONE else View.VISIBLE
        tvReader.text = "Screen reader is off. Turn it on so the app can read your punch times " +
            "from Chrome and fill your login. Tap the button below for the steps."
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

        if (cbSave.isChecked) {
            Cfg.setUsername(this, user)
            Cfg.setPassword(this, pass)
            Cfg.setAutoLogin(this, true)
            Cfg.setPunchIn(this, "")
            Cfg.setPunchOut(this, "")
            Toast.makeText(this, "Saved on this phone (encrypted)", Toast.LENGTH_SHORT).show()
        } else {
            // without saving, the auto sign-in has nothing to type, so it stays off
            Cfg.setAutoLogin(this, false)
        }

        Cfg.setPendingReturn(this, true)
        startActivity(Intent(this, HomeActivity::class.java))
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
