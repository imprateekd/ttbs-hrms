package com.ttbs.hrms

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Test build: the portal inside GeckoView (Firefox's engine) instead of Android's WebView.
 * Cloudflare refuses its challenge in a WebView; this asks whether it accepts a real engine.
 */
class GeckoBrowserActivity : AppCompatActivity() {

    private var runtime: GeckoRuntime? = null
    private var session: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            TextView(this).apply {
                text = "build 5 - GeckoView test"
                textSize = 11f
                setPadding(28, 28, 28, 20)
            }
        )

        val gecko = GeckoView(this)
        root.addView(
            gecko,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        runtime = GeckoShell.runtime(this)
        val s = GeckoShell.newSession(this)

        s.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                uri: String?,
                type: Int,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                if (type == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION) {
                    callback.grant()
                } else {
                    callback.reject()
                }
            }
        }

        gecko.setSession(s)
        s.loadUri(Cfg.url(this))
        session = s
    }

    override fun onDestroy() {
        runCatching { session?.close() }
        super.onDestroy()
    }
}
