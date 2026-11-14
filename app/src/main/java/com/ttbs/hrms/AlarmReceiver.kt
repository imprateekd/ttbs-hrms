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

        // Verify = go and read the portal first. Otherwise the alarm rings on time alone, which
        // no captcha, session expiry or portal change can break.
        val verify = Cfg.verify(context)
        val target: Class<*> = if (verify) CheckActivity::class.java else AlarmActivity::class.java

        // Starting an activity from the background is only allowed when the app may draw overlays.
        val canStart = Settings.canDrawOverlays(context)
        if (canStart) {
            val ok = runCatching {
                context.startActivity(
                    Intent(context, target)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("unverified", !verify)
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
