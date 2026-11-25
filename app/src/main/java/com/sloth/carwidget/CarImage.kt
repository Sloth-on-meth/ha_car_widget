package com.sloth.carwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Caches the car picture HA exposes (entity_picture) as a small PNG. */
object CarImage {
    private const val MAX_PX = 192

    private fun file(context: Context) = File(context.filesDir, "car.png")

    fun update(context: Context, prefs: Prefs, client: HaClient, url: String) {
        if (url.isBlank() || (url == prefs.pictureUrl && file(context).exists())) return
        runCatching {
            val bytes = client.downloadBytes(url)
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val scale = MAX_PX.toFloat() / maxOf(src.width, src.height)
            val bmp = if (scale < 1f)
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            else src
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            prefs.pictureUrl = url
        }
    }

    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}
