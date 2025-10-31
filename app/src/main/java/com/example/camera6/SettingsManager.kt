package com.example.camera6

import android.content.Context
import org.json.JSONObject

data class CameraSettings(
    val preferredFormat: String = "MJPEG",
    val preferredResolution: String = "1280x720",
    val preferredFps: Int = 30,
    val rotate: Int = 0,
)

/**
 * Simple SharedPreferences-backed settings manager for per-device camera settings.
 * Keys:
 *  - "camera_settings:<deviceKey>" -> JSON string with settings
 *  - "camera_name:<deviceKey>" -> human readable name assigned to the device
 */
object SettingsManager {
    private const val PREFS_NAME = "camera_settings_prefs"
    private const val KEY_PREFIX_SETTINGS = "camera_settings:"
    private const val KEY_PREFIX_NAME = "camera_name:"

    fun loadSettings(context: Context, deviceKey: String): CameraSettings? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PREFIX_SETTINGS + deviceKey, null) ?: return null
        return try {
            val obj = JSONObject(json)
            CameraSettings(
                preferredFormat = obj.optString("format", "MJPEG"),
                preferredResolution = obj.optString("resolution", "1280x720"),
                preferredFps = obj.optInt("fps", 30),
                rotate = obj.optInt("rotate", 0)
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun saveSettings(context: Context, deviceKey: String, settings: CameraSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("format", settings.preferredFormat)
            put("resolution", settings.preferredResolution)
            put("fps", settings.preferredFps)
            put("rotate", settings.rotate)
        }.toString()
        prefs.edit().putString(KEY_PREFIX_SETTINGS + deviceKey, json).apply()
    }

    fun hasSettings(context: Context, deviceKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PREFIX_SETTINGS + deviceKey)
    }

    fun getDeviceName(context: Context, deviceKey: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFIX_NAME + deviceKey, null)
    }

    fun setDeviceName(context: Context, deviceKey: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFIX_NAME + deviceKey, name).apply()
    }

    /**
     * Create and persist a default setting for a newly found camera.
     * Returns the default CameraSettings instance stored.
     */
    fun createDefaultForDevice(context: Context, deviceKey: String, humanName: String? = null): CameraSettings {
        val default = CameraSettings() // adjust defaults here if you like
        saveSettings(context, deviceKey, default)
        if (!humanName.isNullOrEmpty()) setDeviceName(context, deviceKey, humanName)
        return default
    }
}