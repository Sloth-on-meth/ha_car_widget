package com.sloth.carwidget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Home Assistant REST client. */
class HaClient(private val baseUrl: String, private val token: String) {

    data class EntityState(
        val state: String,
        val unit: String,
        val name: String,
        val attrs: JSONObject = JSONObject(),
        val lastChanged: String = "",
    )

    fun getState(entityId: String): EntityState {
        val json = JSONObject(request("GET", "/api/states/$entityId", null))
        val attrs = json.optJSONObject("attributes") ?: JSONObject()
        return EntityState(
            state = json.optString("state"),
            unit = attrs.optString("unit_of_measurement", ""),
            name = attrs.optString("friendly_name", entityId),
            attrs = attrs,
            lastChanged = json.optString("last_changed"),
        )
    }

    fun callService(domain: String, service: String, entityId: String) {
        request("POST", "/api/services/$domain/$service", JSONObject().put("entity_id", entityId).toString())
    }

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException(
                    when (code) {
                        401 -> "HA: bad token"
                        404 -> "HA: entity not found"
                        else -> "HA: HTTP $code"
                    }
                )
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
