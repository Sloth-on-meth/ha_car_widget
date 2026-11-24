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

    var trackerEntity: String
        get() = sp.getString("tracker_entity", DEFAULT_TRACKER) ?: DEFAULT_TRACKER
        set(v) = sp.edit().putString("tracker_entity", v.trim()).apply()
    var warningEntity: String
        get() = sp.getString("warning_entity", DEFAULT_WARNING) ?: DEFAULT_WARNING
        set(v) = sp.edit().putString("warning_entity", v.trim()).apply()
    var healthEntity: String
        get() = sp.getString("health_entity", DEFAULT_HEALTH) ?: DEFAULT_HEALTH
        set(v) = sp.edit().putString("health_entity", v.trim()).apply()
    var drivingEntity: String
        get() = sp.getString("driving_entity", DEFAULT_DRIVING) ?: DEFAULT_DRIVING
        set(v) = sp.edit().putString("driving_entity", v.trim()).apply()

    /** Switch that buzzes the downstairs door open. Blank = hide the button. */
    var doorEntity: String
        get() = sp.getString("door_entity", DEFAULT_DOOR) ?: DEFAULT_DOOR
        set(v) = sp.edit().putString("door_entity", v.trim()).apply()

    var nagEnabled: Boolean
        get() = sp.getBoolean("nag", true)
        set(v) = sp.edit().putBoolean("nag", v).apply()
    var nagMinutes: Int
        get() = sp.getInt("nag_minutes", 10)
        set(v) = sp.edit().putInt("nag_minutes", v).apply()

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
    /** Target state of an in-flight command ("locked"/"unlocked"), or "" when idle. */
    var pending: String
        get() = sp.getString("s_pending", "") ?: ""
        set(v) = sp.edit().putString("s_pending", v).apply()
    /** True while a door-open command waits for the switch to confirm. */
    var doorPending: Boolean
        // Auto-expires after 30 s so a killed worker can't leave the spinner stuck.
        get() = System.currentTimeMillis() - sp.getLong("s_door_pending_at", 0L) < 30_000
        set(v) = sp.edit().putLong("s_door_pending_at", if (v) System.currentTimeMillis() else 0L).apply()
    /** Short result message after a command ("Locked ✓"), cleared a few seconds later. */
    var result: String
        get() = sp.getString("s_result", "") ?: ""
        set(v) = sp.edit().putString("s_result", v).apply()
    var resultOk: Boolean
        get() = sp.getBoolean("s_result_ok", true)
        set(v) = sp.edit().putBoolean("s_result_ok", v).apply()

    /** Car location from the device_tracker, "" if unknown. */
    var lat: String
        get() = sp.getString("s_lat", "") ?: ""
        set(v) = sp.edit().putString("s_lat", v).apply()
    var lon: String
        get() = sp.getString("s_lon", "") ?: ""
        set(v) = sp.edit().putString("s_lon", v).apply()
    /** Dashboard warning text ("2 warning lights"), "" when all fine. */
    var warning: String
        get() = sp.getString("s_warning", "") ?: ""
        set(v) = sp.edit().putString("s_warning", v).apply()
    /** When we first saw the car unlocked & parked (0 = not), and whether we already notified. */
    var unlockedSince: Long
        get() = sp.getLong("s_unlocked_since", 0L)
        set(v) = sp.edit().putLong("s_unlocked_since", v).apply()
    var nagged: Boolean
        get() = sp.getBoolean("s_nagged", false)
        set(v) = sp.edit().putBoolean("s_nagged", v).apply()

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()
    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    companion object {
        private const val CAR = "speedy_gonyaris"
        const val DEFAULT_LOCK = "lock.${CAR}_door_lock"
        const val DEFAULT_FUEL = "sensor.${CAR}_fuel_level"
        const val DEFAULT_RANGE = "sensor.${CAR}_fuel_range"
        const val DEFAULT_DOOR = "switch.dooropener_zigbee"
        const val DEFAULT_TRACKER = "device_tracker.${CAR}_parking_location"
        const val DEFAULT_WARNING = "sensor.${CAR}_warning_lights"
        const val DEFAULT_HEALTH = "binary_sensor.${CAR}_vehicle_health"
        const val DEFAULT_DRIVING = "binary_sensor.${CAR}_driving"
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
