package com.sloth.carwidget

import android.content.Context

/** User settings + last known car state, all in one SharedPreferences file. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("car_widget", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", "") ?: ""
        set(v) = sp.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()

    var lockEntity: String
        get() = sp.getString("lock_entity", DEFAULT_LOCK) ?: DEFAULT_LOCK
        set(v) = sp.edit().putString("lock_entity", v.trim()).apply()

    var fuelEntity: String
        get() = sp.getString("fuel_entity", DEFAULT_FUEL) ?: DEFAULT_FUEL
        set(v) = sp.edit().putString("fuel_entity", v.trim()).apply()

    var rangeEntity: String
        get() = sp.getString("range_entity", DEFAULT_RANGE) ?: DEFAULT_RANGE
        set(v) = sp.edit().putString("range_entity", v.trim()).apply()

    /** binary_sensors where "on" means open / unlocked. One per line. */
    var alertEntities: List<String>
        get() = (sp.getString("alert_entities", null) ?: DEFAULT_ALERTS.joinToString("\n"))
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("alert_entities", v.joinToString("\n")).apply()

    var confirmUnlock: Boolean
        get() = sp.getBoolean("confirm_unlock", true)
        set(v) = sp.edit().putBoolean("confirm_unlock", v).apply()

    val isConfigured get() = baseUrl.isNotEmpty() && token.isNotEmpty()

    // ---- cached state shown on the widget ----
    var lockState: String
        get() = sp.getString("s_lock", "") ?: ""
        set(v) = sp.edit().putString("s_lock", v).apply()
    var fuel: String
        get() = sp.getString("s_fuel", "") ?: ""
        set(v) = sp.edit().putString("s_fuel", v).apply()
    var range: String
        get() = sp.getString("s_range", "") ?: ""
        set(v) = sp.edit().putString("s_range", v).apply()
    var alerts: List<String>
        get() = (sp.getString("s_alerts", "") ?: "").split("|").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("s_alerts", v.joinToString("|")).apply()
    var updatedAt: Long
        get() = sp.getLong("s_updated", 0L)
        set(v) = sp.edit().putLong("s_updated", v).apply()
    var error: String
        get() = sp.getString("s_error", "") ?: ""
        set(v) = sp.edit().putString("s_error", v).apply()
    /** Text shown while a lock/unlock command is in flight ("Locking…"). */
    var pending: String
        get() = sp.getString("s_pending", "") ?: ""
        set(v) = sp.edit().putString("s_pending", v).apply()

    companion object {
        private const val CAR = "speedy_gonyaris"
        const val DEFAULT_LOCK = "lock.${CAR}_door_lock"
        const val DEFAULT_FUEL = "sensor.${CAR}_fuel_level"
        const val DEFAULT_RANGE = "sensor.${CAR}_fuel_range"
        val DEFAULT_ALERTS = listOf(
            "binary_sensor.${CAR}_driverseat_door",
            "binary_sensor.${CAR}_passengerseat_door",
            "binary_sensor.${CAR}_leftrearseat_door",
            "binary_sensor.${CAR}_rightrearseat_door",
            "binary_sensor.${CAR}_driverseat_window",
            "binary_sensor.${CAR}_passengerseat_window",
            "binary_sensor.${CAR}_leftrearseat_window",
            "binary_sensor.${CAR}_rightrearseat_window",
            "binary_sensor.${CAR}_trunk",
            "binary_sensor.${CAR}_trunk_lock",
            "binary_sensor.${CAR}_hood",
        )
    }
}
