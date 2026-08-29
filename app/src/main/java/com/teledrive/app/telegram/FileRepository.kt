package com.teledrive.app.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
    val localPath: String?
)

class FileRepository(
    private val tdLibManager: TdLibManager
) {
    suspend fun uploadFile(
        localPath: String,
        virtualPath: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        chatId: Long
    ): Result<TdMessageInfo> {
        return try {
            val caption = MetadataParser.generateCaption(virtualPath, fileName, fileSize, mimeType)
            val messageInfo = tdLibManager.sendFile(chatId, localPath, caption)
            Result.success(messageInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(fileId: Int): Flow<DownloadProgress> = flow {
        try {
            // Trigger the download asynchronously
            tdLibManager.downloadFile(fileId, 1)
        } catch (e: Exception) {
            // If starting the download fails, we can't emit progress.
            return@flow
        }

        // Collect updates for the specific file
        tdLibManager.fileUpdates
            .filter { it.fileId == fileId }
            .takeWhile { !it.isDownloadingCompleted }
            .collect { update ->
                emit(
                    DownloadProgress(
                        bytesDownloaded = update.downloadedSize,
                        totalBytes = update.expectedSize.takeIf { it > 0 } ?: update.size,
                        isComplete = false,
                        localPath = null
                    )
                )
            }

        // Final emit when complete
        tdLibManager.fileUpdates
            .filter { it.fileId == fileId && it.isDownloadingCompleted }
            .map {
                DownloadProgress(
                    bytesDownloaded = it.size,
                    totalBytes = it.size,
                    isComplete = true,
                    localPath = it.localPath
                )
            }
            .collect { emit(it) }
    }

    suspend fun deleteFile(chatId: Long, messageId: Long): Result<Unit> {
        return try {
            tdLibManager.deleteMessages(chatId, longArrayOf(messageId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(
        chatId: Long,
        messageId: Long,
        currentCaption: String,
        newName: String,
        newPath: String
    ): Result<Unit> {
        return try {
            val currentMetadata = MetadataParser.parseCaption(currentCaption)
                ?: throw IllegalArgumentException("Invalid file metadata caption")

            val newCaption = MetadataParser.generateCaption(
                virtualPath = newPath,
                fileName = newName,
                fileSize = currentMetadata.size,
                mimeType = currentMetadata.mime
            )

            tdLibManager.editMessageCaption(chatId, messageId, newCaption)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFileUpdates(): SharedFlow<TdFileUpdate> {
        return tdLibManager.fileUpdates
    }
}
