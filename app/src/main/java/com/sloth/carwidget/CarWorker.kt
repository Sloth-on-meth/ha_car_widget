package com.sloth.carwidget

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Does all network work: optional lock/unlock command, then fetch state and redraw widgets. */
class CarWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val prefs = Prefs(ctx)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (inputData.getBoolean(KEY_CLEAR, false)) {
            prefs.result = ""
            CarWidgetProvider.redrawAll(ctx)
            return Result.success()
        }
        if (!prefs.isConfigured) {
            CarWidgetProvider.redrawAll(ctx)
            return Result.success()
        }
        val client = HaClient(prefs.baseUrl, prefs.token)
        val action = inputData.getString(KEY_ACTION)

        if (action == ACTION_DOOR) {
            try {
                val before = client.getState(prefs.doorEntity).lastChanged
                client.callService("switch", "turn_on", prefs.doorEntity)
                // Opener auto-offs after ~3 s; confirm by seeing it on, or its last_changed move.
                var ok = false
                repeat(20) {
                    if (ok) return@repeat
                    val st = client.getState(prefs.doorEntity)
                    if (st.state == "on" || st.lastChanged != before) ok = true else delay(500)
                }
                prefs.doorPending = false
                if (!ok) {
                    fail("Door didn't confirm")
                    return Result.success()
                }
            } catch (e: Exception) {
                prefs.doorPending = false
                fail("Door failed: ${e.message ?: "error"}")
                return Result.success()
            }
            CarWidgetProvider.redrawAll(ctx)
            Feedback.play(ctx, Feedback.Kind.LOCKED)
            clearLater(ctx, 6)
            return Result.success()
        }

        if (action == ACTION_LOCK || action == ACTION_UNLOCK) {
            val locking = action == ACTION_LOCK
            try {
                client.callService("lock", if (locking) "lock" else "unlock", prefs.lockEntity)
            } catch (e: Exception) {
                runCatching { fetch(client) }
                fail(if (locking) lockBlocked() ?: "Lock failed: ${e.message ?: "error"}"
                     else "Unlock failed: ${e.message ?: "error"}")
                return Result.success()
            }
            // The car takes a while to report back; poll a few times.
            POLLS.forEach { refreshLater(ctx, it) }
        }

        try {
            fetch(client)
            prefs.error = ""
        } catch (e: Exception) {
            prefs.error = e.message ?: e.javaClass.simpleName
        }

        checkPending()
        CarWidgetProvider.redrawAll(ctx)
        return Result.success()
    }

    /** Resolve an in-flight lock/unlock: success once state matches, failure on the last poll. */
    private suspend fun checkPending() {
        val target = prefs.pending
        if (target.isEmpty()) return
        if (prefs.lockState == target) {
            prefs.pending = ""
            cancelPolls(applicationContext)
            CarWidgetProvider.redrawAll(applicationContext)
            Feedback.play(applicationContext, if (target == "locked") Feedback.Kind.LOCKED else Feedback.Kind.UNLOCKED)
            clearLater(applicationContext, 8)
        } else if (inputData.getBoolean(KEY_FINAL, false)) {
            fail(if (target == "locked") lockBlocked() ?: "Lock didn't confirm" else "Unlock didn't confirm")
        }
    }

    /** When locking fails, name whatever is open (that's usually why). */
    private fun lockBlocked(): String? =
        prefs.alerts.filterNot { it.endsWith("unlocked") }.takeIf { it.isNotEmpty() }
            ?.let { "Can't lock: " + it.joinToString(", ") }

    private suspend fun fail(msg: String) {
        if (inputData.getString(KEY_ACTION) != ACTION_DOOR) prefs.pending = ""
        prefs.result = msg
        prefs.resultOk = false
        cancelPolls(applicationContext)
        CarWidgetProvider.redrawAll(applicationContext)
        Feedback.play(applicationContext, Feedback.Kind.FAILED)
        clearLater(applicationContext, 30)
    }

    private fun fetch(client: HaClient) {
        prefs.lockState = client.getState(prefs.lockEntity).state

        val fuel = client.getState(prefs.fuelEntity)
        prefs.fuel = fuel.state.toDoubleOrNull()?.let { "${it.toInt()}${fuel.unit.ifEmpty { "%" }}" } ?: "–"

        val range = client.getState(prefs.rangeEntity)
        prefs.range = range.state.toDoubleOrNull()?.let { "${it.toInt()} ${range.unit}".trim() } ?: "–"

        prefs.alerts = prefs.alertEntities.mapNotNull { id ->
            runCatching { client.getState(id) }.getOrNull()
                ?.takeIf { it.state == "on" }
                ?.let { shortName(id) }
        }
        // Optional extras: each one is skipped silently if its entity is blank or missing.
        prefs.odometer = optional(client, prefs.odometerEntity)?.let { o ->
            o.state.toDoubleOrNull()?.let { String.format("%,d %s", it.toLong(), o.unit).trim() }
        } ?: ""

        optional(client, prefs.trackerEntity)?.let {
            CarImage.update(applicationContext, prefs, client, it.attrs.optString("entity_picture", ""))
            val la = it.attrs.optDouble("latitude", Double.NaN)
            val lo = it.attrs.optDouble("longitude", Double.NaN)
            if (!la.isNaN() && !lo.isNaN()) { prefs.lat = la.toString(); prefs.lon = lo.toString() }
        }
        val lights = optional(client, prefs.warningEntity)?.state?.toDoubleOrNull()?.toInt() ?: 0
        val health = optional(client, prefs.healthEntity)?.state == "on"
        prefs.warning = when {
            lights > 0 -> if (lights == 1) "1 warning light" else "$lights warning lights"
            health -> "Vehicle health issue"
            else -> ""
        }
        optional(client, prefs.tripEntity)?.let { Trips.check(applicationContext, prefs, it.attrs) }

        val driving = optional(client, prefs.drivingEntity)?.state == "on"
        checkUnlockedNag(driving)

        prefs.updatedAt = System.currentTimeMillis()
    }

    private fun optional(client: HaClient, id: String) =
        if (id.isBlank()) null else runCatching { client.getState(id) }.getOrNull()

    /** Notify once if the car sits unlocked (and not driving) for longer than nagMinutes. */
    private fun checkUnlockedNag(driving: Boolean) {
        val now = System.currentTimeMillis()
        if (prefs.lockState != "unlocked" || driving) {
            prefs.unlockedSince = 0
            if (prefs.nagged) Notify.cancel(applicationContext)
            prefs.nagged = false
            return
        }
        if (prefs.unlockedSince == 0L) prefs.unlockedSince = now
        val mins = (now - prefs.unlockedSince) / 60_000
        if (prefs.nagEnabled && !prefs.nagged && prefs.pending.isEmpty() && mins >= prefs.nagMinutes) {
            Notify.unlocked(applicationContext, mins)
            prefs.nagged = true
        }
    }

    /** binary_sensor.speedy_gonyaris_driverseat_window -> "Driver window" */
    private fun shortName(id: String): String {
        val part = id.substringAfter('.').split('_')
        val tail = part.last()
        val nice = when (part.dropLast(1).lastOrNull()) {
            "driverseat" -> "Driver"
            "passengerseat" -> "Passenger"
            "leftrearseat" -> "Rear L"
            "rightrearseat" -> "Rear R"
            "trunk" -> "Trunk"
            else -> ""
        }
        return when (tail) {
            "trunk" -> "Trunk open"
            "hood" -> "Hood open"
            "lock" -> "$nice unlocked".trim()
            "window" -> "$nice window".trim()
            "door" -> "$nice door".trim()
            else -> id.substringAfter('.').replace('_', ' ')
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_FINAL = "final"
        const val KEY_CLEAR = "clear"
        const val ACTION_LOCK = "lock"
        const val ACTION_UNLOCK = "unlock"
        const val ACTION_DOOR = "door"
        private const val PERIODIC = "car_periodic"
        private val POLLS = listOf(10L, 25L, 45L, 90L)

        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun run(context: Context, action: String? = null) {
            val req = OneTimeWorkRequestBuilder<CarWorker>()
                .setConstraints(net)
                .setInputData(if (action != null) workDataOf(KEY_ACTION to action) else Data.EMPTY)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("car_now", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        private fun refreshLater(context: Context, seconds: Long) {
            val req = OneTimeWorkRequestBuilder<CarWorker>()
                .setConstraints(net)
                .setInitialDelay(seconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_FINAL to (seconds == POLLS.last())))
                .addTag("car_poll")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("car_poll_$seconds", ExistingWorkPolicy.REPLACE, req)
        }

        private fun cancelPolls(context: Context) =
            WorkManager.getInstance(context).cancelAllWorkByTag("car_poll")

        private fun clearLater(context: Context, seconds: Long) {
            val req = OneTimeWorkRequestBuilder<CarWorker>()
                .setInitialDelay(seconds, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_CLEAR to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("car_clear", ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<CarWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
