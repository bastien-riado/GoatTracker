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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Keeps the live session visible OUTSIDE the app as an ongoing notification — the Android analog of
 * Deezer's "now playing" presence. It mirrors [RestTimerService]: a foreground service that observes
 * the app-scoped sources of truth ([ActiveSessionController.session] + [RestTimerManager.state]) and
 * self-stops the moment the session ends.
 *
 * On Android 16+ (API 36) the notification opts into **Live Updates** — the status-bar chip that
 * expands on tap (the platform's Dynamic-Island analog): [NotificationCompat.ProgressStyle] renders
 * the planned/completed sets as segments, `setShortCriticalText` feeds the chip label, and
 * `setRequestPromotedOngoing` requests the promoted treatment. This is the OEM-neutral path (AOSP /
 * Pixel chip, Samsung Now Bar, OxygenOS Live Alerts all consume promoted ongoing notifications);
 * on older or non-supporting devices the exact same notification simply shows as a regular ongoing
 * one with the native count-up chronometer, so nothing is lost.
 */
class WorkoutSessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1003
        // v2: importance LOW -> DEFAULT (still silent). Promoted "Live Update" treatment on some
        // OEMs is gated on non-minimal importance; channel settings are immutable after creation,
        // hence the new id.
        const val CHANNEL_ID = "workout_session_channel_v2"
        private const val ACCENT = 0xFFFF3366.toInt()

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
            nm.deleteNotificationChannel("workout_session_channel")
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Séance en cours",
                        // DEFAULT importance for Live-Update eligibility, but silent: it must never
                        // buzz — the rest-timer alert channel owns the "next set" interruption.
                        NotificationManager.IMPORTANCE_DEFAULT,
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
        startInForeground(buildNotification(initial, RestTimerManager.state.value))
        if (initial == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        observeJob?.cancel()
        observeJob = serviceScope.launch {
            // Re-render on: any draft edit, any rest-timer transition (start/finish/skip), and a
            // 1-minute heartbeat that keeps the Live-Update chip's elapsed text fresh (the expanded
            // card's seconds are rendered natively by the system chronometer, not by us).
            val minuteTicker = flow { while (true) { emit(Unit); delay(60_000L) } }
            combine(controller.session, RestTimerManager.state, minuteTicker) { s, rest, _ -> s to rest }
                .collectLatest { (session, rest) ->
                    if (session == null) {
                        stopSelf()
                    } else {
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(session, rest))
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

    private fun buildNotification(session: WorkoutSession?, rest: RestTimerState): Notification {
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
        val plannedSets = session?.exercises?.sumOf { it.sets.size } ?: 0
        val completedExercises = session?.exercises?.count { ex -> ex.sets.any { it.isCompleted } } ?: 0
        val startTime = session?.startTime ?: System.currentTimeMillis()

        val contentText = when (rest) {
            is RestTimerState.Counting -> "Repos en cours • $completedSets série${plural(completedSets)}"
            is RestTimerState.Finished -> "Repos terminé — prochaine série !"
            is RestTimerState.Idle ->
                "$completedExercises exercice${plural(completedExercises)} • $completedSets série${plural(completedSets)}"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(session?.name ?: "Séance en cours")
            .setContentText(contentText)
            // Native count-UP chronometer from the session start: real-time on the lock screen,
            // no per-second wakeups from us.
            .setWhen(startTime)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setColor(ACCENT)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            // Android 16+ "Live Updates": ask the system to promote this ongoing notification to a
            // status-bar chip / prominent lock-screen treatment. Version-safe: a plain extra that
            // older platforms (and OEMs without the treatment) simply ignore.
            .setRequestPromotedOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Terminer", finishPendingIntent)

        // "Valider la série" — check off the next planned set from the wrist: notification actions
        // travel the standard wearable notification bridge (Garmin, COROS, Wear OS… no vendor SDK),
        // which is exactly why this stays a plain NotificationCompat action. Shown only while no
        // rest is running (during a rest the slot belongs to "Passer le repos" — watches display
        // few actions) and only while there is still a set to check.
        val hasPendingSet = session?.exercises?.any { ex -> ex.sets.any { !it.isCompleted } } == true
        if (rest is RestTimerState.Idle && hasPendingSet) {
            val completeSetIntent = Intent(this, WorkoutSessionReceiver::class.java).apply {
                action = WorkoutSessionReceiver.ACTION_COMPLETE_SET
            }
            val completeSetPendingIntent = PendingIntent.getBroadcast(
                this, 2, completeSetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                android.R.drawable.checkbox_on_background,
                "Valider la série",
                completeSetPendingIntent,
            )
        }

        // While a rest is running (or buzzing), surface the skip control out-of-app too. Same
        // receiver/action as the rest notification's "Passer": stops vibration + countdown service
        // and returns the timer to Idle, which re-renders this notification via the combine above.
        if (rest !is RestTimerState.Idle) {
            val skipRestIntent = Intent(this, RestTimerReceiver::class.java).apply {
                action = RestTimerManager.ACTION_CANCEL
            }
            val skipRestPendingIntent = PendingIntent.getBroadcast(
                this, 1, skipRestIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Passer le repos",
                skipRestPendingIntent,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Chip label when the status bar promotes us (kept short per platform guidance).
            val elapsedMin = ((System.currentTimeMillis() - startTime) / 60_000L).toInt().coerceAtLeast(0)
            builder.setShortCriticalText(
                if (elapsedMin < 60) "${elapsedMin}min" else "${elapsedMin / 60}h${"%02d".format(elapsedMin % 60)}"
            )
            // One segment per planned set, filled up to the completed count — the workout's progress
            // bar in the chip's expanded card. Styles only when there is something to show.
            if (plannedSets > 0) {
                builder.setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressSegments(List(plannedSets) {
                            NotificationCompat.ProgressStyle.Segment(1).setColor(ACCENT)
                        })
                        .setProgress(completedSets)
                )
            }
        }

        return builder.build()
    }

    private fun plural(count: Int) = if (count > 1) "s" else ""
}
