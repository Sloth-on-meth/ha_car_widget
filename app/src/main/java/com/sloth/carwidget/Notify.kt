package com.sloth.carwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification

/** "Your car is still unlocked" notification with a Lock action. */
object Notify {
    private const val CHANNEL = "unlocked"
    private const val ID = 1

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Car left unlocked", NotificationManager.IMPORTANCE_HIGH)
        )
        return nm
    }

    fun unlocked(context: Context, minutes: Long) {
        val lock = PendingIntent.getBroadcast(
            context, 10,
            Intent(context, CarWidgetProvider::class.java).setAction(CarWidgetProvider.ACTION_LOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock_open)
            .setContentTitle("Car is still unlocked")
            .setContentText("Unlocked for $minutes min and not driving.")
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Lock now", lock).build())
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 11, Intent(context, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        runCatching { manager(context).notify(ID, n) }
    }

    fun cancel(context: Context) = manager(context).cancel(ID)
}
