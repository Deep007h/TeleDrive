package com.teledrive.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["virtual_path", "telegram_chat_id"], unique = true),
        Index(value = ["parent_folder_id"])
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "folder_id") val folderId: Long = 0,
    @ColumnInfo(name = "virtual_path") val virtualPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: Long? = null,
    @ColumnInfo(name = "telegram_chat_id") val telegramChatId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
