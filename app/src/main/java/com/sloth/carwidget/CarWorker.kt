package com.sloth.carwidget

import android.content.Context
import androidx.work.*
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

        if (action == ACTION_LOCK || action == ACTION_UNLOCK) {
            val locking = action == ACTION_LOCK
            try {
                client.callService("lock", if (locking) "lock" else "unlock", prefs.lockEntity)
            } catch (e: Exception) {
                fail("${if (locking) "Lock" else "Unlock"} failed: ${e.message ?: "error"}")
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
            prefs.result = if (target == "locked") "Locked ✓" else "Unlocked ✓"
            prefs.resultOk = true
            cancelPolls(applicationContext)
            CarWidgetProvider.redrawAll(applicationContext)
            Feedback.play(applicationContext, if (target == "locked") Feedback.Kind.LOCKED else Feedback.Kind.UNLOCKED)
            clearLater(applicationContext, 8)
        } else if (inputData.getBoolean(KEY_FINAL, false)) {
            fail(if (target == "locked") "Lock didn't confirm" else "Unlock didn't confirm")
        }
    }

    private suspend fun fail(msg: String) {
        prefs.pending = ""
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
        prefs.updatedAt = System.currentTimeMillis()
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
