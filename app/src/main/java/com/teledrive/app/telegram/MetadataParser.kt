package com.teledrive.app.telegram

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TeleDriveMetadata(
    val td: String = "1",
    val path: String,
    val name: String,
    val size: Long,
    val mime: String,
    val ts: Long
)

object MetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun generateCaption(virtualPath: String, fileName: String, fileSize: Long, mimeType: String): String {
        val metadata = TeleDriveMetadata(
            path = virtualPath,
            name = fileName,
            size = fileSize,
            mime = mimeType,
            ts = System.currentTimeMillis()
        )
        return json.encodeToString(metadata)
    }

    fun parseCaption(caption: String?): TeleDriveMetadata? {
        if (caption.isNullOrBlank()) return null
        return try {
            json.decodeFromString<TeleDriveMetadata>(caption)
        } catch (e: Exception) {
            null
        }
    }

    fun isTeleDriveFile(caption: String?): Boolean {
        return parseCaption(caption) != null
    }
}
