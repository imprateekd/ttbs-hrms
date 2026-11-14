package com.ttbs.hrms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Cfg.enabled(context)) return
        Notify.ensure(context)
        AlarmScheduler.scheduleAt(context, AlarmScheduler.nextDeadlineMillis(context))
    }
}
