package com.ttbs.hrms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notify.ensure(context)
        // the day still has to roll over even if reminders are switched off
        Cfg.ensureToday(context)
        AlarmScheduler.scheduleMidnight(context)
        if (Cfg.enabled(context)) {
            AlarmScheduler.scheduleAt(context, AlarmScheduler.nextDeadlineMillis(context))
        }
    }
}
