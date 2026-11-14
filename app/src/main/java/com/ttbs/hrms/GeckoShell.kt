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
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .userAgentOverride("")
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build()
        return GeckoSession(settings).also { it.open(runtime(context)) }
    }
}
