package com.example.goattracker.ui.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles actions fired from the ongoing session notification. Kept tiny: it forwards to the
 * process-scoped [ActiveSessionController], whose work runs on its own long-lived scope (not this
 * receiver's short onReceive window). Clearing the draft makes the session end, which makes
 * [WorkoutSessionService] self-stop — so we don't stop the service here.
 */
class WorkoutSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FINISH_SESSION -> ActiveSessionController.getInstance(context).finishActiveSession()
        }
    }

    companion object {
        const val ACTION_FINISH_SESSION = "com.example.goattracker.FINISH_SESSION"
    }
}
