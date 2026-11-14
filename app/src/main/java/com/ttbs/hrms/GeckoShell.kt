package com.ttbs.hrms

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Spike only: proves the GeckoView API surface resolves and compiles against this project.
 * Nothing calls this yet.
 */
object GeckoShell {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"

    private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .javaScriptEnabled(true)
            .build()
        val rt = GeckoRuntime.create(context, settings)
        runtime = rt
        return rt
    }

    fun newSession(context: Context): GeckoSession {
        // Desktop mode AND an explicit desktop UA. An empty userAgentOverride here used to
        // cancel the desktop mode and fall back to the mobile string, which is why the portal
        // kept answering "Access denied - please use your computer or laptop".
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .userAgentOverride(DESKTOP_UA)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build()
        return GeckoSession(settings).also { it.open(runtime(context)) }
    }
}
