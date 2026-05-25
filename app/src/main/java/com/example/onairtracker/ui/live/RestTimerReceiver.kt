package com.example.onairtracker.ui.live

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import com.example.onairtracker.MainActivity

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action

        when (intentAction) {
            RestTimerManager.ACTION_CANCEL -> {
                // Acknowledge the timer globally (sets state to Idle)
                RestTimerManager.acknowledge(context)

                // Also stop the foreground service
                RestTimerService.stop(context)
            }

            RestTimerManager.ACTION_ALARM_FIRED -> {
                // Notify the singleton that alarm has fired
                RestTimerManager.onAlarmFired()

                // Stop the foreground countdown service
                RestTimerService.stop(context)

                // Post the "finished" notification
                postFinishedNotification(context)
            }
        }
    }

    private fun postFinishedNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        RestTimerManager.ensureNotificationChannel(context)

        // Content intent: open the app and navigate to the live workout screen
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RestTimerManager.EXTRA_NAVIGATE_TO, RestTimerManager.NAV_LIVE_WORKOUT)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel/skip action intent
        val cancelIntent = Intent(context, RestTimerReceiver::class.java).apply {
            action = RestTimerManager.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RestTimerManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Repos terminé !")
            .setContentText("C'est l'heure de votre prochaine série ! 💪")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Passer",
                cancelPendingIntent
            )
            .build()

        notificationManager.notify(RestTimerManager.NOTIFICATION_ID, notification)
    }
}
