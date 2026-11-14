package com.ttbs.hrms

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

/** The instructions people actually need to switch the accessibility service on. */
object ReaderHelp {

    fun enabled(ctx: Context): Boolean {
        val list = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return list.contains(ctx.packageName, ignoreCase = true)
    }

    fun show(ctx: Context) {
        val steps = """
            1.  Tap "Open settings" below.
            2.  Open "Installed apps" (or "Downloaded apps") in the accessibility menu.
            3.  Tap "TTBS-HRMS screen reader".
            4.  Turn it ON and allow the permission.
            5.  Come back to the app — it will show "Screen reader on".

            If the switch is greyed out and says "Restricted setting":
            open the TTBS-HRMS app info page, tap the three dots at the top right,
            choose "Allow restricted settings", then repeat steps 1–4.
        """.trimIndent()

        AlertDialog.Builder(ctx)
            .setTitle("Enable the screen reader")
            .setMessage(steps)
            .setPositiveButton("Open settings") { _, _ ->
                runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            .setNeutralButton("App info") { _, _ ->
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + ctx.packageName))
                    )
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
