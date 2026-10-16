package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserPreferences(
    val deviceName: String,
    val autoStopDiscoveryMinutes: Int, // 5, 10, 15, 0 (never)
    val requireConfirmation: Boolean,
    val autoAcceptKnownPeers: Boolean,
    val darkTheme: Boolean?, // null = system, true = dark, false = light
    val transferNotifications: Boolean,
    val vibrateOnComplete: Boolean,
    val serverPort: Int
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_transfer_prefs", Context.MODE_PRIVATE)

    private val defaultDeviceName: String =
        if (Build.MODEL.isNullOrBlank()) "Android Device" else Build.MODEL

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    val preferencesFlow: StateFlow<UserPreferences> = _preferencesFlow.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        val themeVal = prefs.getInt("theme_mode", 0) // 0: system, 1: light, 2: dark
        val theme = when (themeVal) {
            1 -> false
            2 -> true
            else -> null
        }
        return UserPreferences(
            deviceName = prefs.getString("device_name", defaultDeviceName) ?: defaultDeviceName,
            autoStopDiscoveryMinutes = prefs.getInt("auto_stop_minutes", 10),
            requireConfirmation = prefs.getBoolean("require_confirmation", true),
            autoAcceptKnownPeers = prefs.getBoolean("auto_accept_known", false),
            darkTheme = theme,
            transferNotifications = prefs.getBoolean("notifications_enabled", true),
            vibrateOnComplete = prefs.getBoolean("vibrate_on_complete", true),
            serverPort = prefs.getInt("server_port", 8888)
        )
    }

    fun updateDeviceName(name: String) {
        val clean = name.trim().ifBlank { defaultDeviceName }
        prefs.edit().putString("device_name", clean).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateAutoStopMinutes(minutes: Int) {
        prefs.edit().putInt("auto_stop_minutes", minutes).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateRequireConfirmation(require: Boolean) {
        prefs.edit().putBoolean("require_confirmation", require).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateAutoAccept(autoAccept: Boolean) {
        prefs.edit().putBoolean("auto_accept_known", autoAccept).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateTheme(dark: Boolean?) {
        val v = when (dark) {
            null -> 0
            false -> 1
            true -> 2
        }
        prefs.edit().putInt("theme_mode", v).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _preferencesFlow.value = loadPreferences()
    }

    fun updateVibrate(vibrate: Boolean) {
        prefs.edit().putBoolean("vibrate_on_complete", vibrate).apply()
        _preferencesFlow.value = loadPreferences()
    }
}
