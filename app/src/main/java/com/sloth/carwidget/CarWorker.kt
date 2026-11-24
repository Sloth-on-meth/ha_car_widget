package com.sloth.carwidget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Does all network work: optional lock/unlock command, then fetch state and redraw widgets. */
class CarWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) {
            CarWidgetProvider.redrawAll(applicationContext)
            return Result.success()
        }
        val client = HaClient(prefs.baseUrl, prefs.token)
        val action = inputData.getString(KEY_ACTION)

        try {
            if (action == ACTION_LOCK || action == ACTION_UNLOCK) {
                client.callService("lock", if (action == ACTION_LOCK) "lock" else "unlock", prefs.lockEntity)
                // The car takes a while to report back; poll a few times.
                refreshLater(applicationContext, 20)
                refreshLater(applicationContext, 60)
                refreshLater(applicationContext, 120)
            }
            fetch(prefs, client)
            prefs.error = ""
        } catch (e: Exception) {
            prefs.error = e.message ?: e.javaClass.simpleName
            prefs.pending = ""
        }
        CarWidgetProvider.redrawAll(applicationContext)
        return Result.success()
    }

    private fun fetch(prefs: Prefs, client: HaClient) {
        val lock = client.getState(prefs.lockEntity).state
        if (prefs.pending.isNotEmpty() && inputData.getString(KEY_ACTION) == null) {
            // Clear "Locking…/Unlocking…" once the state matches, or on the last poll.
            val target = if (prefs.pending.startsWith("Lock")) "locked" else "unlocked"
            if (lock == target || inputData.getBoolean(KEY_FINAL, false)) prefs.pending = ""
        }
        prefs.lockState = lock

        val fuel = client.getState(prefs.fuelEntity)
        prefs.fuel = fuel.state.toDoubleOrNull()?.let { "${it.toInt()}${fuel.unit.ifEmpty { "%" }}" } ?: "–"

        val range = client.getState(prefs.rangeEntity)
        prefs.range = range.state.toDoubleOrNull()?.let { "${it.toInt()} ${range.unit}".trim() } ?: "–"

        prefs.alerts = prefs.alertEntities.mapNotNull { id ->
            runCatching { client.getState(id) }.getOrNull()
                ?.takeIf { it.state == "on" }
                ?.let { shortName(id) }
        }
        prefs.updatedAt = System.currentTimeMillis()
    }

    /** binary_sensor.speedy_gonyaris_driverseat_window -> "Driver window" */
    private fun shortName(id: String): String {
        val tail = id.substringAfterLast('_', id)
        val part = id.substringAfter('.').split('_')
        val where = part.dropLast(1).lastOrNull() ?: ""
        val nice = when (where) {
            "driverseat" -> "Driver"
            "passengerseat" -> "Passenger"
            "leftrearseat" -> "Rear L"
            "rightrearseat" -> "Rear R"
            "trunk" -> "Trunk"
            else -> ""
        }
        return when {
            tail == "trunk" -> "Trunk open"
            tail == "hood" -> "Hood open"
            tail == "lock" -> "$nice unlocked".trim()
            tail == "window" -> "$nice window".trim()
            tail == "door" -> "$nice door".trim()
            else -> id.substringAfter('.').replace('_', ' ')
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_FINAL = "final"
        const val ACTION_LOCK = "lock"
        const val ACTION_UNLOCK = "unlock"
        private const val PERIODIC = "car_periodic"

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
                .setInputData(workDataOf(KEY_FINAL to (seconds >= 120)))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "car_poll_$seconds", ExistingWorkPolicy.REPLACE, req
            )
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
