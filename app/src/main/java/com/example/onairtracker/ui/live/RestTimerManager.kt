package com.example.onairtracker.ui.live

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.onairtracker.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class RestTimerState {
    data object Idle : RestTimerState()
    data class Counting(val targetMillis: Long, val durationSeconds: Int) : RestTimerState()
    data object Finished : RestTimerState()
}

object RestTimerManager {

    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    // --- Notification channels ---
    const val COUNTDOWN_CHANNEL_ID = "rest_timer_countdown_channel"
    // v2: vibration disabled on this channel so it no longer overrides our manual repeating
    // vibration. Channel settings are immutable after creation, hence a new id (audit B2).
    const val ALERT_CHANNEL_ID = "rest_timer_alert_channel_v2"

    const val NOTIFICATION_ID = 1001
    const val ACTION_CANCEL = "com.example.onairtracker.CANCEL_VIBRATION"
    const val ACTION_ALARM_FIRED = "com.example.onairtracker.ALARM_FIRED"
    const val EXTRA_NAVIGATE_TO = "navigate_to"
    const val NAV_LIVE_WORKOUT = "live_workout"
    private const val ALARM_REQUEST_CODE = 1001

    // --- Persistence ---
    private const val PREFS_NAME = "rest_timer_prefs"
    private const val KEY_TARGET_MILLIS = "target_millis"
    private const val KEY_DURATION_SECONDS = "duration_seconds"

    // --- Foreground tracking ---
    @Volatile
    var isAppInForeground = false

    // ========================================================================
    // Initialization — restore persisted timer state on app startup
    // ========================================================================

    fun initialize(context: Context) {
        ensureNotificationChannel(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTarget = prefs.getLong(KEY_TARGET_MILLIS, -1L)
        if (savedTarget == -1L) return

        val now = System.currentTimeMillis()
        if (savedTarget > now) {
            // Timer is still counting — restore and re-schedule alarm defensively
            val duration = prefs.getInt(KEY_DURATION_SECONDS, 0)
            _state.update { RestTimerState.Counting(savedTarget, duration) }
            scheduleAlarm(context, savedTarget)
        } else {
            // Timer expired while we were dead — show finished state
            _state.update { RestTimerState.Finished }
        }
    }

    // ========================================================================
    // Notification channels
    // ========================================================================

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up legacy channels from previous versions
            nm.deleteNotificationChannel("rest_timer_channel")
            nm.deleteNotificationChannel("rest_timer_alert_channel")

            // Countdown channel — silent, low importance, for the ongoing timer
            if (nm.getNotificationChannel(COUNTDOWN_CHANNEL_ID) == null) {
                val countdownChannel = NotificationChannel(
                    COUNTDOWN_CHANNEL_ID,
                    "Timer de repos",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Affiche le compte à rebours du repos"
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(countdownChannel)
            }

            // Alert channel — high importance, vibration + sound, for "Repos terminé!"
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val alertChannel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alerte fin de repos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifie quand le temps de repos est écoulé"
                    // Vibration is driven manually (repeating, until acknowledged); disabling it
                    // here prevents the channel's one-shot vibration from cancelling ours.
                    enableVibration(false)
                    // Default notification sound kept (HIGH importance → heads-up + sound).
                }
                nm.createNotificationChannel(alertChannel)
            }
        }
    }

    // ========================================================================
    // Timer lifecycle
    // ========================================================================

    fun startTimer(context: Context, durationSeconds: Int) {
        // Cancel pending effects (alarm/notif/vibration) WITHOUT emitting Idle.
        // Going through cancelAll() here would push state Counting → Idle → Counting,
        // which makes the foreground service stopSelf() and the UI rest bar flicker
        // every time a new set is validated while a timer is already running (audit B1).
        resetEffects(context)
        val targetMillis = System.currentTimeMillis() + durationSeconds * 1000L
        _state.update { RestTimerState.Counting(targetMillis, durationSeconds) }
        persistTimer(context, targetMillis, durationSeconds)
        scheduleAlarm(context, targetMillis)
    }

    fun startTimerForTesting(durationSeconds: Int) {
        val targetMillis = System.currentTimeMillis() + durationSeconds * 1000L
        _state.update { RestTimerState.Counting(targetMillis, durationSeconds) }
    }

    fun acknowledgeForTesting() {
        _state.update { RestTimerState.Idle }
    }


    /**
     * Drives the Counting → Finished transition (red UI, vibration, alert notification).
     * Idempotent and safe to call from BOTH the in-process service timer and the AlarmManager
     * receiver — whichever reaches zero first wins, the second call is a no-op. This is what
     * makes the finish fire on time instead of waiting on a batched/inexact alarm.
     */
    fun reachZero(context: Context) {
        if (_state.value !is RestTimerState.Counting) return
        cancelAlarm(context)
        // Persist a past target so a process restart restores Finished, not a stale Counting.
        persistTimer(context, System.currentTimeMillis() - 1000L, 0)
        _state.update { RestTimerState.Finished }
        startVibration(context)
        postFinishedNotification(context, silent = isAppInForeground)
    }

    /** User skipped the rest ("Passer") or dismissed the alert: full reset to Idle. */
    fun acknowledge(context: Context) {
        resetEffects(context)
        clearPersistedTimer(context)
        _state.update { RestTimerState.Idle }
    }

    /** Session ended/started/discarded: cancel everything and return to Idle. */
    fun cancelAll(context: Context) {
        resetEffects(context)
        clearPersistedTimer(context)
        _state.update { RestTimerState.Idle }
    }

    /**
     * Cancels the alarm, the notification and the vibration but DOES NOT touch [_state]
     * nor the persisted target. Used as the common teardown for both [acknowledge]/[cancelAll]
     * (which then go Idle) and [startTimer] (which then goes straight to a new Counting).
     */
    private fun resetEffects(context: Context) {
        cancelAlarm(context)
        cancelNotification(context)
        stopVibration(context)
    }

    fun getRemainingSeconds(): Int {
        val current = _state.value
        return when (current) {
            is RestTimerState.Counting -> {
                // Round UP so a 90s rest shows "90" on the first tick and never displays a
                // value below the real remaining time (integer division truncated it — audit B3).
                val remainingMillis = current.targetMillis - System.currentTimeMillis()
                if (remainingMillis <= 0L) 0 else ((remainingMillis + 999L) / 1000L).toInt()
            }
            is RestTimerState.Finished -> 0
            is RestTimerState.Idle -> 0
        }
    }

    // ========================================================================
    // Vibration — centralized so it works from Receiver, ViewModel, or Screen
    // ========================================================================

    fun startVibration(context: Context) {
        val vibrator = getVibrator(context)
        // Repeating alarm-style pattern (vibrate 800ms / pause 400ms) that loops until the user
        // stops it themselves — by "Passer", by tapping/swiping the alert, or by starting the
        // next set. This can't get stuck forever: every dismissal path calls stopVibration(),
        // the notification's deleteIntent covers the swipe case, and the alert channel's own
        // vibration is disabled so it never overrides this pattern (see audit B2).
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    fun stopVibration(context: Context) {
        getVibrator(context).cancel()
    }

    private fun getVibrator(context: Context): Vibrator {
        // Always resolve the vibrator from the APPLICATION context. The vibration is started by
        // the (short-lived) foreground service but stopped from the ViewModel or the Receiver —
        // i.e. via different Context instances. On several OEM ROMs the vibrator service is scoped
        // to the Context, so cancel() from a different/destroyed Context fails to reach the running
        // vibration. Pinning both start and stop to the single, process-lived application Context
        // guarantees stopVibration() always cancels the right one.
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ========================================================================
    // Finished notification — posted by the Receiver when the alarm fires
    // ========================================================================

    fun postFinishedNotification(context: Context, silent: Boolean = false) {
        ensureNotificationChannel(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Content intent: open the app and navigate to the live workout screen
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_LIVE_WORKOUT)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel/skip action intent
        val cancelIntent = Intent(context, RestTimerReceiver::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Repos terminé !")
            .setContentText("C'est l'heure de votre prochaine série ! 💪")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Swiping the alert away also acknowledges (stops vibration + clears state).
            .setDeleteIntent(cancelPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Passer",
                cancelPendingIntent
            )

        if (silent) {
            builder.setSilent(true)
        }

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    // ========================================================================
    // Persistence — SharedPreferences to survive process death
    // ========================================================================

    private fun persistTimer(context: Context, targetMillis: Long, durationSeconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TARGET_MILLIS, targetMillis)
            .putInt(KEY_DURATION_SECONDS, durationSeconds)
            .apply()
    }

    private fun clearPersistedTimer(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    // ========================================================================
    // Notification management
    // ========================================================================

    private fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    // ========================================================================
    // AlarmManager
    // ========================================================================

    private fun scheduleAlarm(context: Context, targetMillis: Long) {
        cancelAlarm(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent
                    )
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RestTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
