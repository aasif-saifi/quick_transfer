package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.QuickTransferApp
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as QuickTransferApp
    private val preferencesRepo = app.preferencesRepository
    private val transferRepo = app.transferRepository

    val preferences: StateFlow<UserPreferences> = preferencesRepo.preferencesFlow

    fun setDeviceName(name: String) {
        preferencesRepo.updateDeviceName(name)
    }

    fun setAutoStopMinutes(minutes: Int) {
        preferencesRepo.updateAutoStopMinutes(minutes)
    }

    fun setRequireConfirmation(require: Boolean) {
        preferencesRepo.updateRequireConfirmation(require)
    }

    fun setAutoAccept(autoAccept: Boolean) {
        preferencesRepo.updateAutoAccept(autoAccept)
    }

    fun setTheme(dark: Boolean?) {
        preferencesRepo.updateTheme(dark)
    }

    fun setNotifications(enabled: Boolean) {
        preferencesRepo.updateNotifications(enabled)
    }

    fun setVibrate(vibrate: Boolean) {
        preferencesRepo.updateVibrate(vibrate)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            transferRepo.clearHistory()
        }
    }
}
