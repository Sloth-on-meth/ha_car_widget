package com.sloth.carwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class CarWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) = CarWorker.schedulePeriodic(context)

    override fun onDisabled(context: Context) = CarWorker.cancelPeriodic(context)

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        redrawAll(context)
        CarWorker.schedulePeriodic(context)
        CarWorker.run(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> CarWorker.run(context)
            ACTION_LOCK -> command(context, CarWorker.ACTION_LOCK, "Locking…")
            ACTION_UNLOCK -> command(context, CarWorker.ACTION_UNLOCK, "Unlocking…")
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.sloth.carwidget.REFRESH"
        const val ACTION_LOCK = "com.sloth.carwidget.LOCK"
        const val ACTION_UNLOCK = "com.sloth.carwidget.UNLOCK"

        private const val GREEN = 0xFF4CAF50.toInt()
        private const val ORANGE = 0xFFFF9800.toInt()
        private const val RED = 0xFFF44336.toInt()
        private const val GREY = 0xFF9E9E9E.toInt()

        fun command(context: Context, action: String, label: String) {
            Prefs(context).pending = label
            redrawAll(context)
            CarWorker.run(context, action)
        }

        fun redrawAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CarWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, CarWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private fun build(context: Context): RemoteViews {
            val p = Prefs(context)
            val v = RemoteViews(context.packageName, R.layout.widget_car)

            // Clicks
            v.setOnClickPendingIntent(R.id.btn_lock, broadcast(context, ACTION_LOCK, 1))
            val unlockIntent = if (p.confirmUnlock) {
                PendingIntent.getActivity(
                    context, 2,
                    Intent(context, ConfirmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else broadcast(context, ACTION_UNLOCK, 2)
            v.setOnClickPendingIntent(R.id.btn_unlock, unlockIntent)

            if (!p.isConfigured) {
                val open = PendingIntent.getActivity(
                    context, 3, Intent(context, SettingsActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
                v.setOnClickPendingIntent(R.id.status_area, open)
                v.setImageViewResource(R.id.status_icon, R.drawable.ic_lock)
                v.setInt(R.id.status_icon, "setColorFilter", GREY)
                v.setTextViewText(R.id.status_sub, "Tap to set up")
                v.setTextColor(R.id.status_sub, GREY)
                v.setTextViewText(R.id.fuel, "–")
                v.setTextViewText(R.id.range, "")
                return v
            }
            v.setOnClickPendingIntent(R.id.status_area, broadcast(context, ACTION_REFRESH, 4))
            v.setOnClickPendingIntent(R.id.fuel_area, broadcast(context, ACTION_REFRESH, 5))

            val locked = p.lockState == "locked"
            val alerts = p.alerts
            val color = when {
                p.error.isNotEmpty() -> GREY
                !locked -> RED
                alerts.isNotEmpty() -> ORANGE
                else -> GREEN
            }
            v.setImageViewResource(R.id.status_icon, if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open)
            v.setInt(R.id.status_icon, "setColorFilter", color)


            val (sub, subColor) = when {
                p.pending.isNotEmpty() -> p.pending to GREY
                p.error.isNotEmpty() -> p.error to RED
                alerts.isNotEmpty() -> alerts.joinToString(", ") to ORANGE
                else -> "" to GREY
            }
            v.setTextViewText(R.id.status_sub, sub)
            v.setTextColor(R.id.status_sub, subColor)
            v.setViewVisibility(R.id.status_sub, if (sub.isEmpty()) View.GONE else View.VISIBLE)

            v.setTextViewText(R.id.fuel, p.fuel.ifEmpty { "–" })
            v.setTextViewText(R.id.range, p.range)
            return v
        }
    }
}
