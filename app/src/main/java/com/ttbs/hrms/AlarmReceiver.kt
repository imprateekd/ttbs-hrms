package com.ttbs.hrms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.ttbs.hrms.CHECK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Cfg.enabled(context)) return

        // Starting an activity from the background is only allowed when the app may draw overlays.
        val canStart = Settings.canDrawOverlays(context)
        if (canStart) {
            val ok = runCatching {
                context.startActivity(
                    Intent(context, CheckActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }.isSuccess
            if (ok) return
        }

        // Fallback: a full-screen-intent notification.
        Notify.checkPrompt(
            context,
            "TTBS-HRMS — checking your punch-out",
            "Tap to check whether you are still punched in.",
            CheckActivity::class.java
        )
    }
}
