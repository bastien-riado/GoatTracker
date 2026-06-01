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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            // collectLatest: a new Counting (e.g. the next set restarts the rest) cancels the
            // pending finish below and re-arms it on the new target.
            RestTimerManager.state.collectLatest { timerState ->
                when (timerState) {
                    is RestTimerState.Counting -> {
                        // Publish the notification once; the live countdown is rendered natively
                        // by the system via setUsesChronometer/ChronometerCountDown (audit B5).
                        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(
                            FOREGROUND_NOTIFICATION_ID,
                            buildCountdownNotification(RestTimerManager.getRemainingSeconds())
                        )
                        // Drive the finish PRECISELY from the live process. A coroutine delay is
                        // accurate to the millisecond and, because this is a foreground service,
                        // keeps firing even with the screen off — unlike the AlarmManager backup,
                        // which the system batches/delays by ~10s when exact alarms aren't granted.
                        val delayMs = timerState.targetMillis - System.currentTimeMillis()
                        if (delayMs > 0) delay(delayMs)
                        RestTimerManager.reachZero(this@RestTimerService)
                    }
                    is RestTimerState.Finished -> stopSelf()
                    is RestTimerState.Idle -> stopSelf()
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
        // Get the absolute target time for the native countdown chronometer
        val timerState = RestTimerManager.state.value
        val targetMillis = if (timerState is RestTimerState.Counting) {
            timerState.targetMillis
        } else {
            System.currentTimeMillis() + remainingSeconds * 1000L
        }

        // Tap → open the app on the live workout screen
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RestTimerManager.EXTRA_NAVIGATE_TO, RestTimerManager.NAV_LIVE_WORKOUT)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Passer" action → cancel timer via Receiver
        val cancelIntent = Intent(this, RestTimerReceiver::class.java).apply {
            action = RestTimerManager.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RestTimerManager.COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Repos en cours")
            .setContentText("Temps restant avant la prochaine série")
            // Native countdown timer — updates in real-time on lock screen
            .setWhen(targetMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            // Visible on lock screen without unlocking
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Passer", cancelPendingIntent)
            .build()
    }
}
