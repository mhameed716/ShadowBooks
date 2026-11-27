package com.shadowbooks.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shadowbooks.MainActivity
import java.util.Calendar

/** Daily "حان وقت المذاكرة" reminder: one inexact alarm, re-armed each day. */
object StudyReminder {
    private const val PREFS = "study_reminder"
    private const val CHANNEL_ID = "study_reminder"
    private const val NOTIFICATION_ID = 77
    private const val REQUEST_CODE = 7701

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean("enabled", false)

    fun hour(context: Context): Int = prefs(context).getInt("hour", 19)

    fun minute(context: Context): Int = prefs(context).getInt("minute", 0)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        if (enabled) scheduleNext(context) else cancel(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt("hour", hour).putInt("minute", minute).apply()
        if (isEnabled(context)) scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        if (!isEnabled(context)) return
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(context))
            set(Calendar.MINUTE, minute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            alarmManager(context).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                pendingIntent(context),
            )
        } catch (e: Exception) {
            // A refused alarm only silences the reminder; nothing else breaks.
        }
    }

    fun cancel(context: Context) {
        try {
            alarmManager(context).cancel(pendingIntent(context))
        } catch (e: Exception) {
            // Already cancelled.
        }
    }

    internal fun fire(context: Context) {
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            5,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("حان وقت المذاكرة 📚")
            .setContentText("جلسة تركيز قصيرة اليوم تصنع فرقًا كبيرًا غدًا. افتح ShadowBooks وتابع.")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Permission denied: the reminder stays silent rather than crashing.
        }
        scheduleNext(context)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تذكير المذاكرة",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, StudyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        StudyReminder.fire(context)
    }
}

/** Re-arms the daily reminder after a reboot. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            StudyReminder.scheduleNext(context)
        }
    }
}
