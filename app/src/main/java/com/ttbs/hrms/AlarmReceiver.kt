package com.ttbs.hrms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.ttbs.hrms.CHECK"
        const val ACTION_BREAK = "com.ttbs.hrms.BREAK"
        const val ACTION_RESET = "com.ttbs.hrms.RESET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESET -> {
                Cfg.resetDay(context)
                AlarmScheduler.scheduleMidnight(context)
                return
            }
            ACTION_BREAK -> {
                if (!Day.isOnBreak(context)) return
                if (!Day.overBreakLimit(context)) return
                ringBreak(context)
                // keep nagging every couple of minutes while the break is still running
                AlarmScheduler.scheduleBreak(context)
                return
            }
        }

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

    private fun ringBreak(context: Context) {
        val canStart = Settings.canDrawOverlays(context)
        if (canStart) {
            val ok = runCatching {
                context.startActivity(
                    Intent(context, AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("break", true)
                )
            }.isSuccess
            if (ok) return
        }
        Notify.checkPrompt(
            context,
            "TTBS-HRMS - break is over",
            "Your break allowance is used up. Tap to end the break.",
            AlarmActivity::class.java
        )
    }
}
