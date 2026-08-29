package com.teledrive.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.teledrive.app.core.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "teledrive_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val STORAGE_CHANNEL_ID = longPreferencesKey("storage_channel_id")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val VIEW_MODE = stringPreferencesKey("view_mode")
        private val SORT_BY = stringPreferencesKey("sort_by")
        private val API_ID = intPreferencesKey("telegram_api_id")
        private val API_HASH = stringPreferencesKey("telegram_api_hash")
        private val OTA_UPDATE_URL = stringPreferencesKey("ota_update_url")
        private val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
    }

    val storageChannelId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[STORAGE_CHANNEL_ID]
    }

    suspend fun setStorageChannelId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[STORAGE_CHANNEL_ID] = id
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    val viewMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VIEW_MODE] ?: "grid"
    }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[VIEW_MODE] = mode
        }
    }

    val sortBy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SORT_BY] ?: "name_asc"
    }

    suspend fun setSortBy(sort: String) {
        context.dataStore.edit { prefs ->
            prefs[SORT_BY] = sort
        }
    }

    val apiId: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[API_ID] ?: Constants.API_ID
    }

    suspend fun setApiId(id: Int) {
        context.dataStore.edit { prefs ->
            prefs[API_ID] = id
        }
    }

    val apiHash: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_HASH] ?: Constants.API_HASH
    }

    suspend fun setApiHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[API_HASH] = hash
        }
    }

    val otaUpdateUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[OTA_UPDATE_URL] ?: Constants.DEFAULT_OTA_UPDATE_URL
    }

    suspend fun setOtaUpdateUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[OTA_UPDATE_URL] = url
        }
    }

    val autoCheckUpdates: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CHECK_UPDATES] ?: true
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_CHECK_UPDATES] = enabled
        }
    }

    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_UPDATE_CHECK_TIME] ?: 0L
    }

    suspend fun setLastUpdateCheckTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK_TIME] = timestamp
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
