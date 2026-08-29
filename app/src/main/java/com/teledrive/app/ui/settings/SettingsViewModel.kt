package com.teledrive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.data.repository.StorageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val phoneNumber: String = "",
    val username: String = "",
    val themeMode: String = "system",
    val storageStats: StorageStats = StorageStats(0, 0L, 0, 0, 0, 0, 0),
    val appVersion: String = "1.0.0"
)

class SettingsViewModel : ViewModel() {

    private val tdLibManager = TeleDriveApplication.instance.tdLibManager
    private val localRepository = TeleDriveApplication.instance.localRepository
    private val preferences = TeleDriveApplication.instance.preferences

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val savedTheme = preferences.themeMode.first()
            _uiState.update { it.copy(themeMode = savedTheme) }

            try {
                val (phone, user) = tdLibManager.getMe()
                _uiState.update {
                    it.copy(
                        phoneNumber = phone,
                        username = user ?: ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phoneNumber = "Logged in")
                }
            }

            localRepository.getStorageStats().collect { stats ->
                _uiState.update { it.copy(storageStats = stats) }
            }
        }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    private val otaUpdateManager = TeleDriveApplication.instance.otaUpdateManager
    val otaUpdateState: StateFlow<com.teledrive.app.core.ota.OtaUpdateState> = otaUpdateManager.updateState
    val otaUpdateUrl: Flow<String> = preferences.otaUpdateUrl
    val autoCheckUpdates: Flow<Boolean> = preferences.autoCheckUpdates

    val currentVersionName: String get() = otaUpdateManager.currentVersionName
    val currentVersionCode: Int get() = otaUpdateManager.currentVersionCode

    fun checkForUpdates() {
        otaUpdateManager.checkForUpdates(force = true)
    }

    fun startDownload(info: com.teledrive.app.core.ota.UpdateInfo) {
        otaUpdateManager.startDownload(info)
    }

    fun cancelDownload() {
        otaUpdateManager.cancelDownload()
    }

    fun dismissUpdate() {
        otaUpdateManager.dismiss()
    }

    fun installUpdate(apkFile: java.io.File) {
        otaUpdateManager.promptInstall(apkFile)
    }

    fun setOtaUpdateUrl(url: String) {
        viewModelScope.launch {
            preferences.setOtaUpdateUrl(url.trim())
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoCheckUpdates(enabled)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                tdLibManager.logout()
                preferences.clear()
            } catch (ignored: Exception) {}
            onLoggedOut()
        }
    }
}
