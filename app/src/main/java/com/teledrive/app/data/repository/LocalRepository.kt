package com.teledrive.app.data.repository

import com.teledrive.app.data.db.dao.FileDao
import com.teledrive.app.data.db.dao.FolderDao
import com.teledrive.app.data.db.dao.TransferDao
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.data.db.entity.FolderEntity
import com.teledrive.app.telegram.MetadataParser
import com.teledrive.app.telegram.TdLibManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class StorageStats(
    val totalFiles: Int,
    val totalSize: Long,
    val imageCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val documentCount: Int,
    val otherCount: Int
)

class LocalRepository(
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val transferDao: TransferDao,
    private val tdLibManager: TdLibManager,
    private val metadataParser: MetadataParser
) : AutoCloseable {
    private val job = SupervisorJob()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + job)

    init {
        repositoryScope.launch {
            tdLibManager.newMessages.collect { msg ->
                try {
                    val metadata = metadataParser.parseCaption(msg.caption)
                    val rawName = msg.documentFileName ?: "file_${msg.messageId}"
                    val virtualPath = metadata?.path ?: "/Uncategorized/$rawName"
                    val fileName = metadata?.name ?: rawName
                    val fileSize = if (metadata != null && metadata.size > 0) metadata.size else msg.documentSize
                    val mimeType = metadata?.mime ?: (msg.documentMimeType ?: "application/octet-stream")

                    val parentPath = if (virtualPath.contains('/')) {
                        val parent = virtualPath.substringBeforeLast('/')
                        if (parent.isEmpty()) "/" else parent
                    } else {
                        "/"
                    }

                    ensureFolderExists(parentPath, msg.chatId)
                    val parentFolder = if (parentPath.isNotEmpty() && parentPath != "/") {
                        folderDao.getByPathAndChat(parentPath, msg.chatId)
                    } else null

                    val existingFile = fileDao.getByMessageId(msg.chatId, msg.messageId)
                        ?: (if (fileSize > 0) fileDao.getByChatNameAndSize(msg.chatId, fileName, fileSize) else null)
                        ?: (if (fileSize > 0) fileDao.getByNameAndSize(fileName, fileSize) else null)

                    val fileEntity = FileEntity(
                        fileId = existingFile?.fileId ?: 0,
                        telegramMessageId = msg.messageId,
                        telegramChatId = msg.chatId,
                        virtualPath = virtualPath,
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType,
                        telegramFileId = msg.documentFileId,
                        thumbnailFileId = msg.thumbnailFileId,
                        uploadTimestamp = if (metadata != null && metadata.ts > 0) {
                            if (metadata.ts > 1_000_000_000_000L) metadata.ts else metadata.ts * 1000L
                        } else {
                            msg.date.toLong() * 1000L
                        },
                        parentFolderId = parentFolder?.folderId,
                        isSynced = true
                    )

                    fileDao.upsertByMessageId(fileEntity)
                    com.teledrive.app.TeleDriveApplication.instance.thumbnailCacheManager.preloadThumbnails(listOf(fileEntity))
                    com.teledrive.app.core.AppLogger.logSync("LiveMessageUpsert", "Upserted live message: ${fileEntity.fileName} (${fileEntity.fileSize} bytes)")
                } catch (e: Exception) {
                    com.teledrive.app.core.AppLogger.e("SyncEngine", "Failed to upsert live message: ${e.message}", e)
                }
            }
        }

        repositoryScope.launch {
            tdLibManager.deletedMessages.collect { (chatId, messageIds) ->
                try {
                    for (msgId in messageIds) {
                        fileDao.deleteByMessageId(chatId, msgId)
                        com.teledrive.app.TeleDriveApplication.instance.thumbnailCacheManager.removeThumbnailByMessageId(chatId, msgId)
                    }
                    com.teledrive.app.core.AppLogger.logSync("LiveDelete", "Deleted ${messageIds.size} messages from Room for chatId=$chatId")
                } catch (e: Exception) {
                    com.teledrive.app.core.AppLogger.e("SyncEngine", "Failed to process live deleted messages: ${e.message}", e)
                }
            }
        }
    }

    override fun close() {
        job.cancel()
    }

    suspend fun syncFromTelegram(chatId: Long) {
        if (chatId == 0L) return
        try {
            com.teledrive.app.core.AppLogger.logSync("Start", "Starting sync for chatId=$chatId")
            
            // Open chat stream in TDLib to ensure active synchronization
            tdLibManager.openChat(chatId)

            fileDao.deleteDuplicates()
            fileDao.deleteDuplicatesByNameAndSize()

            val seenKeys = HashSet<String>()
            val activeTelegramMessageIds = HashSet<Long>()
            var fromMessageId = 0L
            var hasMore = true
            var totalSyncedItems = 0
            var retryCount = 0

            while (hasMore) {
                val batch = tdLibManager.getChatHistoryFull(chatId, fromMessageId, 100)
                if (batch.totalRawCount == 0 || batch.lastRawMessageId == 0L) {
                    if (fromMessageId == 0L && retryCount == 0) {
                        retryCount++
                        kotlinx.coroutines.delay(600)
                        continue
                    }
                    break
                }

                com.teledrive.app.core.AppLogger.logSync(
                    "Batch",
                    "Fetched ${batch.totalRawCount} raw messages, ${batch.parsedItems.size} parsed file/media items (fromMessageId=$fromMessageId, lastRawId=${batch.lastRawMessageId})"
                )

                val batchEntities = mutableListOf<FileEntity>()
                for (msg in batch.parsedItems) {
                    activeTelegramMessageIds.add(msg.messageId)

                    val metadata = metadataParser.parseCaption(msg.caption)
                    val rawName = msg.documentFileName ?: "file_${msg.messageId}"
                    val virtualPath = metadata?.path ?: "/Uncategorized/$rawName"
                    val fileName = metadata?.name ?: rawName
                    val fileSize = if (metadata != null && metadata.size > 0) metadata.size else msg.documentSize
                    val mimeType = metadata?.mime ?: (msg.documentMimeType ?: "application/octet-stream")

                    val dedupeKey = if (fileSize > 0) "${fileName.lowercase().trim()}_$fileSize" else "${fileName.lowercase().trim()}_${msg.messageId}"
                    if (seenKeys.contains(dedupeKey)) {
                        continue
                    }
                    seenKeys.add(dedupeKey)

                    val parentPath = if (virtualPath.contains('/')) {
                        val parent = virtualPath.substringBeforeLast('/')
                        if (parent.isEmpty()) "/" else parent
                    } else {
                        "/"
                    }

                    ensureFolderExists(parentPath, chatId)
                    val parentFolder = if (parentPath.isNotEmpty() && parentPath != "/") {
                        folderDao.getByPathAndChat(parentPath, chatId)
                    } else null

                    val existingFile = fileDao.getByMessageId(chatId, msg.messageId)
                        ?: (if (fileSize > 0) fileDao.getByChatNameAndSize(chatId, fileName, fileSize) else null)
                        ?: (if (fileSize > 0) fileDao.getByNameAndSize(fileName, fileSize) else null)

                    val fileEntity = FileEntity(
                        fileId = existingFile?.fileId ?: 0,
                        telegramMessageId = msg.messageId,
                        telegramChatId = chatId,
                        virtualPath = virtualPath,
                        fileName = fileName,
                        fileSize = fileSize,
                        mimeType = mimeType,
                        telegramFileId = msg.documentFileId,
                        thumbnailFileId = msg.thumbnailFileId,
                        uploadTimestamp = if (metadata != null && metadata.ts > 0) {
                            if (metadata.ts > 1_000_000_000_000L) metadata.ts else metadata.ts * 1000L
                        } else {
                            msg.date.toLong() * 1000L
                        },
                        parentFolderId = parentFolder?.folderId,
                        isSynced = true
                    )

                    batchEntities.add(fileEntity)
                }

                if (batchEntities.isNotEmpty()) {
                    fileDao.upsertAll(batchEntities)
                    com.teledrive.app.TeleDriveApplication.instance.thumbnailCacheManager.preloadThumbnails(batchEntities)
                    totalSyncedItems += batchEntities.size
                }

                if (batch.lastRawMessageId == 0L || batch.lastRawMessageId == fromMessageId) {
                    hasMore = false
                } else {
                    fromMessageId = batch.lastRawMessageId
                }
            }

            // Prune any files from Room database that were deleted from Telegram
            // Since we have the complete set of active message IDs from chat history,
            // any local file whose messageId is not in that set was deleted from Telegram
            val localFiles = fileDao.getAllFilesList().filter { it.telegramChatId == chatId }
            val deletedCount = localFiles.count { !activeTelegramMessageIds.contains(it.telegramMessageId) }
            if (deletedCount > 0) {
                localFiles.filter { !activeTelegramMessageIds.contains(it.telegramMessageId) }.forEach { localFile ->
                    fileDao.delete(localFile)
                    com.teledrive.app.TeleDriveApplication.instance.thumbnailCacheManager.removeThumbnail(localFile)
                }
                com.teledrive.app.core.AppLogger.logSync("PruneDeleted", "Pruned $deletedCount deleted messages from Room for chatId=$chatId")
            }

            fileDao.deleteDuplicates()
            fileDao.deleteDuplicatesByNameAndSize()
            com.teledrive.app.core.AppLogger.logSync("Complete", "Sync completed for chatId=$chatId. Synced $totalSyncedItems items.")
        } catch (e: Exception) {
            com.teledrive.app.core.AppLogger.e("SyncEngine", "Error during sync for chatId=$chatId: ${e.message}", e)
        }
    }

    suspend fun refreshFileIds(file: FileEntity): FileEntity {
        val chatId = if (file.telegramChatId != 0L) file.telegramChatId else tdLibManager.getSavedMessagesChatId()
        if (chatId != 0L && file.telegramMessageId != 0L) {
            val freshInfo = tdLibManager.getMessageInfo(chatId, file.telegramMessageId)
            if (freshInfo != null) {
                fileDao.updateFileIds(file.fileId, freshInfo.documentFileId, freshInfo.thumbnailFileId)
                return file.copy(
                    telegramFileId = freshInfo.documentFileId,
                    thumbnailFileId = freshInfo.thumbnailFileId
                )
            } else {
                fileDao.delete(file)
            }
        }
        return file
    }

    fun getAllFiles(chatId: Long): Flow<List<FileEntity>> {
        return fileDao.getAll().map { list ->
            list.filter { file ->
                (file.telegramChatId == chatId || chatId == 0L)
            }.distinctBy { file ->
                if (file.fileSize > 0) "${file.fileName.lowercase().trim()}_${file.fileSize}"
                else if (file.telegramFileId != 0) "tg_${file.telegramFileId}"
                else file.fileName.lowercase().trim()
            }.sortedByDescending { it.uploadTimestamp }
        }
    }

    fun getAllMedia(chatId: Long): Flow<List<FileEntity>> {
        return fileDao.getAll().map { list ->
            list.filter { file ->
                (file.telegramChatId == chatId || chatId == 0L) &&
                (file.mimeType.startsWith("image/") ||
                 file.mimeType.startsWith("video/") ||
                 file.fileName.endsWith(".jpg", true) ||
                 file.fileName.endsWith(".jpeg", true) ||
                 file.fileName.endsWith(".png", true) ||
                 file.fileName.endsWith(".webp", true) ||
                 file.fileName.endsWith(".mp4", true) ||
                 file.fileName.endsWith(".mkv", true))
            }.distinctBy { file ->
                if (file.fileSize > 0) "${file.fileName.lowercase().trim()}_${file.fileSize}"
                else if (file.telegramFileId != 0) "tg_${file.telegramFileId}"
                else file.fileName.lowercase().trim()
            }.sortedByDescending { it.uploadTimestamp }
        }
    }

    fun getFilesInFolder(folderPath: String, chatId: Long): Flow<List<FileEntity>> {
        val normalizedPath = if (folderPath.isEmpty() || folderPath == "/") "/" else folderPath.trimEnd('/')
        return fileDao.getAll().map { list ->
            list.filter { file ->
                val parentOfFile = if (file.virtualPath.contains('/')) {
                    val parent = file.virtualPath.substringBeforeLast('/')
                    if (parent.isEmpty()) "/" else parent
                } else {
                    "/"
                }
                parentOfFile == normalizedPath && (file.telegramChatId == chatId || chatId == 0L)
            }.distinctBy { file ->
                if (file.fileSize > 0) "${file.fileName.lowercase().trim()}_${file.fileSize}"
                else if (file.telegramFileId != 0) "tg_${file.telegramFileId}"
                else file.fileName.lowercase().trim()
            }.sortedByDescending { it.uploadTimestamp }
        }
    }

    fun getFolders(parentPath: String, chatId: Long): Flow<List<FolderEntity>> {
        val normalizedParent = if (parentPath.isEmpty() || parentPath == "/") "/" else parentPath.trimEnd('/')
        return folderDao.getAllFolders().map { list ->
            list.filter { folder ->
                val parentOfFolder = if (folder.virtualPath.contains('/')) {
                    val parent = folder.virtualPath.substringBeforeLast('/')
                    if (parent.isEmpty()) "/" else parent
                } else {
                    "/"
                }
                parentOfFolder == normalizedParent && (folder.telegramChatId == chatId || chatId == 0L)
            }
        }
    }

    suspend fun createFolder(name: String, parentPath: String, chatId: Long): Long {
        val normalizedParent = if (parentPath.isEmpty() || parentPath == "/") "" else parentPath.trimEnd('/')
        val newPath = "$normalizedParent/$name"
        val parentFolder = if (normalizedParent.isNotEmpty()) folderDao.getByPathAndChat(normalizedParent, chatId) else null

        val folder = FolderEntity(
            virtualPath = newPath,
            folderName = name,
            parentFolderId = parentFolder?.folderId,
            telegramChatId = chatId
        )
        return folderDao.insert(folder)
    }

    suspend fun deleteFile(file: FileEntity) {
        try {
            val targetChatId = if (file.telegramChatId != 0L) {
                file.telegramChatId
            } else {
                try { tdLibManager.getSavedMessagesChatId() } catch (e: Exception) { 0L }
            }
            com.teledrive.app.core.AppLogger.i("LocalRepository", "Deleting message from Telegram: chatId=$targetChatId, msgId=${file.telegramMessageId}")
            if (targetChatId != 0L && file.telegramMessageId != 0L) {
                tdLibManager.deleteMessages(targetChatId, longArrayOf(file.telegramMessageId))
            }
        } catch (e: Exception) {
            com.teledrive.app.core.AppLogger.e("LocalRepository", "Failed to delete message from Telegram: ${e.message}", e)
        }
        fileDao.delete(file)
        com.teledrive.app.TeleDriveApplication.instance.thumbnailCacheManager.removeThumbnail(file)
        com.teledrive.app.core.AppLogger.i("LocalRepository", "Deleted file from database: ${file.fileName}")
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        val files = fileDao.getAllFilesList().filter { it.virtualPath.startsWith(folder.virtualPath) && it.telegramChatId == folder.telegramChatId }
        val msgIds = files.map { it.telegramMessageId }.toLongArray()
        if (msgIds.isNotEmpty()) {
            try {
                tdLibManager.deleteMessages(folder.telegramChatId, msgIds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        for (f in files) {
            fileDao.delete(f)
        }
        folderDao.delete(folder)
        folderDao.deleteByPathAndChat(folder.virtualPath, folder.telegramChatId)
    }

    suspend fun moveFile(fileId: Long, newPath: String) {
        val file = fileDao.getAllFilesList().firstOrNull { it.fileId == fileId } ?: return
        val newCaption = metadataParser.generateCaption(
            virtualPath = newPath,
            fileName = file.fileName,
            fileSize = file.fileSize,
            mimeType = file.mimeType
        )
        try {
            tdLibManager.editMessageCaption(file.telegramChatId, file.telegramMessageId, newCaption)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val updated = file.copy(virtualPath = newPath)
        fileDao.update(updated)
    }

    suspend fun renameFile(fileId: Long, newName: String) {
        val file = fileDao.getAllFilesList().firstOrNull { it.fileId == fileId } ?: return
        val parentPath = if (file.virtualPath.contains('/')) file.virtualPath.substringBeforeLast('/') else ""
        val newVirtualPath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"
        val newCaption = metadataParser.generateCaption(
            virtualPath = newVirtualPath,
            fileName = newName,
            fileSize = file.fileSize,
            mimeType = file.mimeType
        )
        try {
            tdLibManager.editMessageCaption(file.telegramChatId, file.telegramMessageId, newCaption)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val updated = file.copy(fileName = newName, virtualPath = newVirtualPath)
        fileDao.update(updated)
    }

    fun searchFiles(query: String): Flow<List<FileEntity>> {
        return fileDao.searchByName(query)
    }

    fun getStorageStats(): Flow<StorageStats> {
        return fileDao.getAll().map { files ->
            var totalSize = 0L
            var imgCount = 0
            var vidCount = 0
            var audioCount = 0
            var docCount = 0
            var otherCount = 0

            for (f in files) {
                totalSize += f.fileSize
                when {
                    f.mimeType.startsWith("image/") -> imgCount++
                    f.mimeType.startsWith("video/") -> vidCount++
                    f.mimeType.startsWith("audio/") -> audioCount++
                    f.mimeType.startsWith("application/") || f.mimeType.startsWith("text/") -> docCount++
                    else -> otherCount++
                }
            }

            StorageStats(
                totalFiles = files.size,
                totalSize = totalSize,
                imageCount = imgCount,
                videoCount = vidCount,
                audioCount = audioCount,
                documentCount = docCount,
                otherCount = otherCount
            )
        }
    }

    suspend fun insertFile(entity: FileEntity): Long {
        return fileDao.insert(entity)
    }

    suspend fun ensureFolderExists(virtualPath: String, chatId: Long) {
        if (virtualPath.isEmpty() || virtualPath == "/") return
        if (folderDao.exists(virtualPath, chatId)) return

        val segments = virtualPath.trim('/').split('/')
        var currentPath = ""
        var parentId: Long? = null

        for (segment in segments) {
            currentPath += "/$segment"
            val existing = folderDao.getByPathAndChat(currentPath, chatId)
            if (existing == null) {
                val newFolder = FolderEntity(
                    virtualPath = currentPath,
                    folderName = segment,
                    parentFolderId = parentId,
                    telegramChatId = chatId
                )
                parentId = folderDao.insert(newFolder)
            } else {
                parentId = existing.folderId
            }
        }
    }
}
