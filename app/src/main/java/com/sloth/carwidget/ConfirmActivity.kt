package com.sloth.carwidget

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** Small "are you sure?" dialog so the car doesn't get unlocked by a pocket tap. */
class ConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertDialog.Builder(this)
            .setTitle("Unlock car?")
            .setPositiveButton("Unlock") { _, _ ->
                CarWidgetProvider.command(this, CarWorker.ACTION_UNLOCK, "Unlocking…")
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
