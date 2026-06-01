package com.example.goattracker.ui.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestTimerManager.ACTION_CANCEL -> {
                // "Passer" pressed — from notification or anywhere
                // acknowledge() stops vibration, cancels notification, clears persistence, sets Idle
                RestTimerManager.acknowledge(context)
                RestTimerService.stop(context)
            }

            RestTimerManager.ACTION_ALARM_FIRED -> {
                // Backup path: the in-process service timer normally reaches zero first and
                // precisely. reachZero() is idempotent (no-op if already Finished), drives the
                // Finished state + vibration + "Repos terminé!" notification, and cancels the
                // alarm. Only this AlarmManager path is used if the process was killed.
                RestTimerManager.reachZero(context)
                RestTimerService.stop(context)
            }
        }
    }
}
