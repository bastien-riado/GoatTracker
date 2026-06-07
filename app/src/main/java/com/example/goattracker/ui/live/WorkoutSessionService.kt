package com.example.goattracker.ui.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.goattracker.MainActivity
import com.example.goattracker.domain.model.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the live session visible OUTSIDE the app as an ongoing notification — the Android analog of
 * Deezer's "now playing" bar (and, where the OS supports it, of an iOS Live Activity / Dynamic
 * Island; see [promoteToLiveUpdate]). It mirrors [RestTimerService]: a short foreground service that
 * observes a single source of truth ([ActiveSessionController.session]) and self-stops the moment the
 * session ends. Elapsed time is rendered by the system's native chronometer, so the notification
 * stays accurate with the screen off and across Doze without us pushing per-second updates.
 */
class WorkoutSessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1003
        const val CHANNEL_ID = "workout_session_channel"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutSessionService::class.java))
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Séance en cours",
                        // LOW: ongoing and silent — it must never buzz; the rest-timer alert channel
                        // owns the "it's time for your next set" interruption.
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Affiche la séance d'entraînement en cours"
                        enableVibration(false)
                        setSound(null, null)
                    }
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = ActiveSessionController.getInstance(this)

        // startForeground must be called promptly. Build from whatever the controller already holds;
        // if there is somehow no session, leave foreground and stop immediately.
        val initial = controller.session.value
        startInForeground(buildNotification(initial))
        if (initial == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        observeJob?.cancel()
        observeJob = serviceScope.launch {
            // collectLatest: a fresh session object (any edit re-emits the draft) replaces the
            // notification content; a null means the session ended -> tear the service down.
            controller.session.collectLatest { session ->
                if (session == null) {
                    stopSelf()
                } else {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(session))
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(session: WorkoutSession?): Notification {
        // Tap → open the app on the live workout screen (reuses the existing deep-link plumbing in
        // MainActivity / MainNavigation, the same one the rest-timer notification uses).
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RestTimerManager.EXTRA_NAVIGATE_TO, RestTimerManager.NAV_LIVE_WORKOUT)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // "Terminer" → finish & save from outside the app (handled by WorkoutSessionReceiver).
        val finishIntent = Intent(this, WorkoutSessionReceiver::class.java).apply {
            action = WorkoutSessionReceiver.ACTION_FINISH_SESSION
        }
        val finishPendingIntent = PendingIntent.getBroadcast(
            this, 0, finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completedSets = session?.exercises?.sumOf { ex -> ex.sets.count { it.isCompleted } } ?: 0
        val completedExercises = session?.exercises?.count { ex -> ex.sets.any { it.isCompleted } } ?: 0
        val startTime = session?.startTime ?: System.currentTimeMillis()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(session?.name ?: "Séance en cours")
            .setContentText("$completedExercises exercice(s) • $completedSets série(s)")
            // Native count-UP chronometer from the session start: real-time on the lock screen,
            // no per-second wakeups from us.
            .setWhen(startTime)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            // Android 16+ "Live Updates": ask the system to promote this ongoing notification to a
            // status-bar chip / prominent lock-screen treatment — the closest platform analog to an
            // iOS Live Activity / Dynamic Island. Version-safe: on older platforms (and OEMs without
            // the treatment) it is just an ignored extra, so the ordinary ongoing notification stands.
            .setRequestPromotedOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Terminer", finishPendingIntent)

        return builder.build()
    }
}
