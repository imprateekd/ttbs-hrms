package com.ttbs.hrms

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONTokener

/** Visible dashboard: log in here once, and save the two page samples here. */
class BrowserActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        val hint = findViewById<TextView>(R.id.tvHint)

        WebSetup.configure(this, web, Cfg.desktop(this))
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            // The portal needs a location before it lets you punch; hand it the device location.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }
        web.loadUrl(Cfg.url(this))

        findViewById<Button>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSaveIn).setOnClickListener {
            capture(true, hint)
        }
        findViewById<Button>(R.id.btnSaveOut).setOnClickListener {
            capture(false, hint)
        }
    }

    private fun capture(stillIn: Boolean, hint: TextView) {
        web.evaluateJavascript("(document.body?document.body.innerText:'').slice(0,40000)") { raw ->
            val text = runCatching {
                val v = JSONTokener(raw ?: "null").nextValue()
                if (v is String) v else ""
            }.getOrDefault("")
            if (text.length < 30) {
                Toast.makeText(this, "Page not readable yet — let it finish loading.", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            if (stillIn) Cfg.setInSample(this, text) else Cfg.setOutSample(this, text)
            val msg = if (stillIn) "Saved as STILL PUNCHED IN" else "Saved as PUNCHED OUT"
            Toast.makeText(this, msg + " (" + text.length + " chars)", Toast.LENGTH_SHORT).show()
            hint.text = "Sample saved. Save the other one after your status changes."
        }
    }

    override fun onPause() {
        WebSetup.flush()
        super.onPause()
    }
}
