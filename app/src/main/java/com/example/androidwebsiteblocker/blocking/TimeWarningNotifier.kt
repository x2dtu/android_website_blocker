package com.example.androidwebsiteblocker.blocking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.androidwebsiteblocker.MainActivity
import com.example.androidwebsiteblocker.R
import com.example.androidwebsiteblocker.core.policy.TimeWarning

/** Heads-up "10 minutes left" / "1 minute left" notifications for soft block total time. */
class TimeWarningNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Time warnings", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "10-minute and 1-minute warnings before a soft block's total time runs out"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(warning: TimeWarning, targetLabel: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val minutes = warning.threshold.toMinutes()
        val left = if (minutes == 1L) "1 minute left" else "$minutes minutes left"
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$targetLabel: $left")
            .setContentText("${warning.setName} · total time for this window")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        // One notification per set + target, so the 1-minute warning replaces the 10-minute one.
        NotificationManagerCompat.from(context).notify("${warning.setId}|${warning.target}".hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "time_warnings"
        const val TIMEOUT_MS = 2 * 60_000L
    }
}
