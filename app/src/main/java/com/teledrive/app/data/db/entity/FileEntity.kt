package com.teledrive.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["telegram_chat_id", "telegram_message_id"], unique = true),
        Index(value = ["virtual_path"]),
        Index(value = ["parent_folder_id"]),
        Index(value = ["file_name"]),
        Index(value = ["mime_type"]),
        Index(value = ["upload_timestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["folder_id"],
            childColumns = ["parent_folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "file_id") val fileId: Long = 0,
    @ColumnInfo(name = "telegram_message_id") val telegramMessageId: Long,
    @ColumnInfo(name = "telegram_chat_id") val telegramChatId: Long,
    @ColumnInfo(name = "virtual_path") val virtualPath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "mime_type") val mimeType: String = "application/octet-stream",
    @ColumnInfo(name = "telegram_file_id") val telegramFileId: Int = 0,
    @ColumnInfo(name = "thumbnail_file_id") val thumbnailFileId: Int? = null,
    @ColumnInfo(name = "upload_timestamp") val uploadTimestamp: Long = 0,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: Long? = null,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = true
)
