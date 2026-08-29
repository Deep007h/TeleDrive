package com.teledrive.app.telegram

import com.teledrive.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first

class ChannelRepository(
    private val tdLibManager: TdLibManager,
    private val preferences: AppPreferences
) {
    companion object {
        const val DEFAULT_DRIVE_NAME = "📁 TeleDrive Storage"
    }

    suspend fun getOrCreateStorageChannel(): Long {
        val existingId = getStorageChannelId()
        if (existingId != null && existingId != 0L) {
            try {
                tdLibManager.getChat(existingId)
                return existingId
            } catch (e: Exception) {
                // Channel not accessible or deleted
            }
        }

        val newChannel = tdLibManager.createPrivateChannel(
            title = DEFAULT_DRIVE_NAME,
            description = "Private storage channel for TeleDrive. Do not delete or modify this channel manually."
        )
        preferences.setStorageChannelId(newChannel.chatId)
        return newChannel.chatId
    }

    suspend fun getSavedMessagesChatId(): Long {
        return try {
            tdLibManager.getSavedMessagesChatId()
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getStorageChannelId(): Long? {
        return preferences.storageChannelId.first()
    }
}
