package com.sloth.carwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** Posts a summary notification when the Toyota integration reports a new trip. */
object Trips {
    private const val CHANNEL = "trips"

    fun check(context: Context, prefs: Prefs, attrs: JSONObject) {
        val trip = attrs.optJSONArray("trips")?.optJSONObject(0) ?: return
        val id = trip.optString("id").ifEmpty { trip.optString("end_ts") }
        if (id.isEmpty() || id == prefs.lastTripId) return
        val firstLook = prefs.lastTripId.isEmpty()
        prefs.lastTripId = id
        // Don't spam an old trip the first time the app looks.
        if (firstLook || !prefs.tripNotify) return
        notify(context, trip, id.hashCode())
    }

    private fun notify(context: Context, trip: JSONObject, notifId: Int) {
        val stats = trip.optJSONObject("stats") ?: JSONObject()
        val km = stats.optDouble("distance_m", 0.0) / 1000
        val mins = (stats.optDouble("duration_s", 0.0) / 60).roundToInt()
        val avg = stats.optDouble("average_speed_kmh", Double.NaN)
        val fuelL = stats.optDouble("fuel_consumption_ml", Double.NaN) / 1000
        val score = trip.optJSONObject("scores")?.optInt("global", -1) ?: -1
        val end = trip.optJSONObject("end")
        val lat = end?.optDouble("lat", Double.NaN) ?: Double.NaN
        val lon = end?.optDouble("lon", Double.NaN) ?: Double.NaN
        val place = if (!lat.isNaN() && !lon.isNaN()) placeName(context, lat, lon) else null

        val title = String.format(Locale.getDefault(), "Trip: %.1f km · %d min", km, mins) +
            (place?.let { " → $it" } ?: "")
        val details = buildList {
            if (!avg.isNaN()) add(String.format(Locale.getDefault(), "Avg %.0f km/h", avg))
            if (!fuelL.isNaN() && fuelL > 0) {
                add(String.format(Locale.getDefault(), "%.2f L", fuelL))
                if (km > 0.1) add(String.format(Locale.getDefault(), "%.1f L/100km", fuelL / km * 100))
            }
            if (score >= 0) add("Score $score")
        }.joinToString(" · ")

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Trip summaries", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setAutoCancel(true)
        if (!lat.isNaN() && !lon.isNaN()) {
            val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Car)")
            b.setContentIntent(
                PendingIntent.getActivity(
                    context, 20, Intent(Intent.ACTION_VIEW, geo),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        runCatching { nm.notify(notifId, b.build()) }
    }

    /** "Kerkstraat, Leiden" via the phone's geocoder; null if unavailable. */
    @Suppress("DEPRECATION")
    private fun placeName(context: Context, lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?: return null
        listOfNotNull(a.thoroughfare, a.locality).joinToString(", ").ifEmpty { null }
    }.getOrNull()
}
