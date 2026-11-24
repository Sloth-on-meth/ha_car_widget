package com.sloth.carwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If launched as widget config and user backs out, don't add the widget.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) setResult(RESULT_CANCELED, result())

        val url = findViewById<EditText>(R.id.url)
        val token = findViewById<EditText>(R.id.token)
        val lock = findViewById<EditText>(R.id.lock_entity)
        val fuel = findViewById<EditText>(R.id.fuel_entity)
        val range = findViewById<EditText>(R.id.range_entity)
        val alerts = findViewById<EditText>(R.id.alert_entities)
        val confirm = findViewById<CheckBox>(R.id.confirm_unlock)
        val door = findViewById<EditText>(R.id.door_entity)
        val tracker = findViewById<EditText>(R.id.tracker_entity)
        val warning = findViewById<EditText>(R.id.warning_entity)
        val health = findViewById<EditText>(R.id.health_entity)
        val driving = findViewById<EditText>(R.id.driving_entity)
        val nag = findViewById<CheckBox>(R.id.nag)
        val nagMinutes = findViewById<EditText>(R.id.nag_minutes)
        val sound = findViewById<CheckBox>(R.id.sound)
        val vibrate = findViewById<CheckBox>(R.id.vibrate)
        val status = findViewById<TextView>(R.id.status)

        url.setText(prefs.baseUrl)
        token.setText(prefs.token)
        lock.setText(prefs.lockEntity)
        fuel.setText(prefs.fuelEntity)
        range.setText(prefs.rangeEntity)
        alerts.setText(prefs.alertEntities.joinToString("\n"))
        confirm.isChecked = prefs.confirmUnlock
        sound.isChecked = prefs.sound
        door.setText(prefs.doorEntity)
        tracker.setText(prefs.trackerEntity)
        warning.setText(prefs.warningEntity)
        health.setText(prefs.healthEntity)
        driving.setText(prefs.drivingEntity)
        nag.isChecked = prefs.nagEnabled
        nagMinutes.setText(prefs.nagMinutes.toString())
        vibrate.isChecked = prefs.vibrate

        fun save() {
            prefs.baseUrl = url.text.toString()
            prefs.token = token.text.toString()
            prefs.lockEntity = lock.text.toString()
            prefs.fuelEntity = fuel.text.toString()
            prefs.rangeEntity = range.text.toString()
            prefs.alertEntities = alerts.text.lines()
            prefs.confirmUnlock = confirm.isChecked
            prefs.sound = sound.isChecked
            prefs.doorEntity = door.text.toString()
            prefs.trackerEntity = tracker.text.toString()
            prefs.warningEntity = warning.text.toString()
            prefs.healthEntity = health.text.toString()
            prefs.drivingEntity = driving.text.toString()
            prefs.nagEnabled = nag.isChecked
            prefs.nagMinutes = nagMinutes.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 10
            prefs.vibrate = vibrate.isChecked
        }

        findViewById<Button>(R.id.test).setOnClickListener {
            save()
            status.text = "Testing…"
            Thread {
                val msg = try {
                    val c = HaClient(prefs.baseUrl, prefs.token)
                    val l = c.getState(prefs.lockEntity)
                    val f = c.getState(prefs.fuelEntity)
                    val r = c.getState(prefs.rangeEntity)
                    "OK: ${l.state}, fuel ${f.state}${f.unit}, range ${r.state} ${r.unit}"
                } catch (e: Exception) {
                    "Failed: ${e.message ?: e.javaClass.simpleName}"
                }
                runOnUiThread { status.text = msg }
            }.start()
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            save()
            CarWorker.schedulePeriodic(this)
            CarWorker.run(this)
            CarWidgetProvider.redrawAll(this)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) setResult(RESULT_OK, result())
            finish()
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
