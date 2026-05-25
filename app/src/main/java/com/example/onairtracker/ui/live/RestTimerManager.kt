package com.example.onairtracker.ui.live

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class RestTimerState {
    data object Idle : RestTimerState()
    data class Counting(val targetMillis: Long, val durationSeconds: Int) : RestTimerState()
    data object Finished : RestTimerState()
}

object RestTimerManager {

    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    const val CHANNEL_ID = "rest_timer_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_CANCEL = "com.example.onairtracker.CANCEL_VIBRATION"
    const val ACTION_ALARM_FIRED = "com.example.onairtracker.ALARM_FIRED"
    const val EXTRA_NAVIGATE_TO = "navigate_to"
    const val NAV_LIVE_WORKOUT = "live_workout"
    private const val ALARM_REQUEST_CODE = 1001

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Timer de repos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifie quand le temps de repos est écoulé"
                    enableVibration(false) // We handle vibration manually
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun startTimer(context: Context, durationSeconds: Int) {
        cancelAll(context)
        val targetMillis = System.currentTimeMillis() + durationSeconds * 1000L
        _state.value = RestTimerState.Counting(targetMillis, durationSeconds)
        scheduleAlarm(context, targetMillis)
    }

    fun addTime(context: Context, seconds: Int) {
        val current = _state.value
        if (current !is RestTimerState.Counting) return

        val currentRemaining = ((current.targetMillis - System.currentTimeMillis()) / 1000).toInt()
        val newRemaining = (currentRemaining + seconds).coerceAtLeast(0)

        if (newRemaining <= 0) {
            cancelAlarm(context)
            onAlarmFired()
        } else {
            val newTarget = System.currentTimeMillis() + newRemaining * 1000L
            _state.value = RestTimerState.Counting(newTarget, newRemaining)
            scheduleAlarm(context, newTarget)
        }
    }

    fun onAlarmFired() {
        _state.value = RestTimerState.Finished
    }

    fun acknowledge(context: Context) {
        cancelAlarm(context)
        cancelNotification(context)
        _state.value = RestTimerState.Idle
    }

    fun cancelAll(context: Context) {
        cancelAlarm(context)
        cancelNotification(context)
        _state.value = RestTimerState.Idle
    }

    fun getRemainingSeconds(): Int {
        val current = _state.value
        return when (current) {
            is RestTimerState.Counting -> {
                ((current.targetMillis - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            }
            is RestTimerState.Finished -> 0
            is RestTimerState.Idle -> 0
        }
    }

    private fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun scheduleAlarm(context: Context, targetMillis: Long) {
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
