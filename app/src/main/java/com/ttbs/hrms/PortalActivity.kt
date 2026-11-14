package com.ttbs.hrms

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** The portal itself, rendered by GeckoView with our own toolbar - no browser chrome. */
class PortalActivity : AppCompatActivity() {

    private var session: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portal)

        val host = findViewById<FrameLayout>(R.id.portalHost)
        val progressBar = findViewById<ProgressBar>(R.id.portalProgress)

        val gecko = GeckoView(this)
        host.addView(
            gecko,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val s = GeckoShell.newSession(this)
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progressBar.visibility = View.GONE
            }
        }

        gecko.setSession(s)
        s.loadUri(Cfg.url(this))
        session = s

        findViewById<Button>(R.id.btnBack).setOnClickListener { runCatching { s.goBack() } }
        findViewById<Button>(R.id.btnReloadPortal).setOnClickListener { runCatching { s.reload() } }
        findViewById<Button>(R.id.btnClosePortal).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        runCatching { session?.close() }
        super.onDestroy()
    }
}
