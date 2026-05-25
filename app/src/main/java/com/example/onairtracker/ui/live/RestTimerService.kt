package com.example.onairtracker.ui.live

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.onairtracker.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class RestTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, RestTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RestTimerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        RestTimerManager.ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildCountdownNotification(RestTimerManager.getRemainingSeconds())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            RestTimerManager.state.collectLatest { timerState ->
                when (timerState) {
                    is RestTimerState.Counting -> {
                        // Update notification periodically
                        while (isActive) {
                            val remaining = RestTimerManager.getRemainingSeconds()
                            if (remaining <= 0) break
                            val updatedNotification = buildCountdownNotification(remaining)
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            nm.notify(FOREGROUND_NOTIFICATION_ID, updatedNotification)
                            delay(1000)
                        }
                    }
                    is RestTimerState.Finished -> {
                        stopSelf()
                    }
                    is RestTimerState.Idle -> {
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildCountdownNotification(remainingSeconds: Int): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RestTimerManager.EXTRA_NAVIGATE_TO, RestTimerManager.NAV_LIVE_WORKOUT)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, RestTimerReceiver::class.java).apply {
            action = RestTimerManager.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RestTimerManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Repos en cours")
            .setContentText("Temps restant : $timeText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Passer", cancelPendingIntent)
            .build()
    }
}
