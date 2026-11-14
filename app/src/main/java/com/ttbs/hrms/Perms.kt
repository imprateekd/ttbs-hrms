package com.ttbs.hrms

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** The four things this app needs before it can sign you in or remind you. */
object Perms {

    fun accessibility(ctx: Context): Boolean = ReaderHelp.enabled(ctx)

    fun notifications(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    fun exactAlarms(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun all(ctx: Context): Boolean =
        accessibility(ctx) && notifications(ctx) && exactAlarms(ctx) && overlay(ctx)

    fun count(ctx: Context): Int =
        listOf(accessibility(ctx), notifications(ctx), exactAlarms(ctx), overlay(ctx)).count { it }
}
