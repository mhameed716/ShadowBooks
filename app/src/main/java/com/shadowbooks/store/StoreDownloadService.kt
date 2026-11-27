package com.shadowbooks.store

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shadowbooks.MainActivity

/** Keeps store downloads alive while the student uses other apps. */
class StoreDownloadService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تنزيلات المتجر",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this,
            5,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("متجر ShadowBooks")
            .setContentText("جارٍ تنزيل الملفات...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        StoreDownloader.onAllIdle = { stopSelf() }
        if (StoreDownloader.activeCount() == 0) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        if (StoreDownloader.onAllIdle != null) StoreDownloader.onAllIdle = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "store_downloads"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StoreDownloadService::class.java),
                )
            } catch (e: Exception) {
                // Background-start restrictions may refuse; downloads still run
                // in-process and resume on the next attempt.
            }
        }
    }
}
