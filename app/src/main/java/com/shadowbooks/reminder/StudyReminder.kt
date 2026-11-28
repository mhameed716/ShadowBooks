package com.shadowbooks.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shadowbooks.MainActivity
import java.util.Calendar

/**
 * Daily "حان وقت المذاكرة" reminder that behaves like an alarm clock:
 * the alarm sound loops and the notification stays on screen until the
 * student dismisses it (or taps it to open the app).
 */
object StudyReminder {
    private const val PREFS = "study_reminder"
    private const val CHANNEL_ID = "study_reminder_alarm"
    private const val NOTIFICATION_ID = 77
    private const val REQUEST_CODE = 7701
    const val ACTION_STOP = "com.shadowbooks.reminder.STOP"

    /** Settings display: 12-hour clock with ص/م, like every clock on the phone. */
    fun formatTime(hour: Int, minute: Int): String {
        val suffix = if (hour < 12) "ص" else "م"
        val h12 = ((hour + 11) % 12) + 1
        return "%d:%02d %s".format(h12, minute, suffix)
    }

    /** Stops the ringing reminder (called from the notification action). */
    fun stopRinging(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Nothing to stop.
        }
    }

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
        val stop = PendingIntent.getBroadcast(
            context,
            6,
            Intent(context, StudyReminderReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("حان وقت المذاكرة 📚")
            .setContentText("جلسة تركيز قصيرة اليوم تصنع فرقًا كبيرًا غدًا. افتح ShadowBooks وتابع.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp)
            .setFullScreenIntent(openApp, true)
            .addAction(0, "إيقاف التذكير", stop)
            .build()
        // Ring the alarm sound again and again until the student acts,
        // exactly like the phone's own alarm clock.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
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
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تذكير المذاكرة (منبّه)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(alarmSound, attributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 900, 400, 900, 400, 900)
            }
            manager?.createNotificationChannel(channel)
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
        if (intent?.action == StudyReminder.ACTION_STOP) {
            StudyReminder.stopRinging(context)
        } else {
            StudyReminder.fire(context)
        }
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
