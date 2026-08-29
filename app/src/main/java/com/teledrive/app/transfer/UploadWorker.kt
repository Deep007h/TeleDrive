package com.teledrive.app.transfer

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.core.getMimeType
import com.teledrive.app.data.db.entity.FileEntity
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class UploadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val transferId = inputData.getLong("transfer_id", -1L)
        if (transferId == -1L) return Result.failure()

        val app = context.applicationContext as TeleDriveApplication
        val transferDao = app.database.transferDao()
        val fileDao = app.database.fileDao()
        val folderDao = app.database.folderDao()
        val tdLibManager = app.tdLibManager
        val fileRepository = app.fileRepository
        val localRepository = app.localRepository

        val transferEntity = transferDao.getById(transferId) ?: return Result.failure()
        if (transferEntity.status == "COMPLETED") return Result.success()

        transferDao.updateStatus(transferId, "IN_PROGRESS", null, System.currentTimeMillis())
        val notificationManager = TransferNotificationManager()

        try {
            val notification = notificationManager.createNotification(
                context = context,
                fileName = transferEntity.fileName,
                progress = 0,
                isUpload = true
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setForeground(ForegroundInfo(transferId.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                } else {
                    setForeground(ForegroundInfo(transferId.toInt(), notification))
                }
            } catch (ignored: Exception) {}

            val mimeType = transferEntity.fileName.getMimeType()
            val uploadResult = fileRepository.uploadFile(
                localPath = transferEntity.localFilePath,
                virtualPath = transferEntity.virtualPath,
                fileName = transferEntity.fileName,
                fileSize = transferEntity.fileSize,
                mimeType = mimeType,
                chatId = transferEntity.telegramChatId
            )

            if (uploadResult.isFailure) {
                val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                transferDao.updateStatus(transferId, "FAILED", errorMsg, System.currentTimeMillis())
                return Result.failure()
            }

            val msgInfo = uploadResult.getOrThrow()

            // Check if TDLib already completed uploading the file
            var isFinished = false
            try {
                val initialFile = tdLibManager.getFile(msgInfo.documentFileId)
                if (initialFile.remote.isUploadingCompleted) {
                    isFinished = true
                }
            } catch (ignored: Exception) {}

            if (!isFinished) {
                // Observe file updates with a safe timeout
                val timeoutResult = withTimeoutOrNull(180_000L) {
                    tdLibManager.fileUpdates
                        .filter { it.fileId == msgInfo.documentFileId }
                        .collect { update ->
                            val progress = if (update.expectedSize > 0) {
                                ((update.uploadedSize.toFloat() / update.expectedSize) * 100).toInt()
                            } else if (transferEntity.fileSize > 0) {
                                ((update.uploadedSize.toFloat() / transferEntity.fileSize) * 100).toInt()
                            } else 0

                            transferDao.updateProgress(transferId, update.uploadedSize, System.currentTimeMillis())

                            val progressNotification = notificationManager.createNotification(
                                context = context,
                                fileName = transferEntity.fileName,
                                progress = progress.coerceIn(0, 100),
                                isUpload = true
                            )
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    setForeground(ForegroundInfo(transferId.toInt(), progressNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                                } else {
                                    setForeground(ForegroundInfo(transferId.toInt(), progressNotification))
                                }
                            } catch (ignored: Exception) {}

                            if (update.isUploadingCompleted) {
                                isFinished = true
                                cancel()
                            }
                        }
                }
                
                // If timeout occurred and upload is not finished, mark as failed
                if (timeoutResult == null && !isFinished) {
                    transferDao.updateStatus(transferId, "FAILED", "Upload timed out", System.currentTimeMillis())
                    return Result.failure()
                }
            }

            transferDao.updateProgress(transferId, transferEntity.fileSize, System.currentTimeMillis())
            transferDao.updateStatus(transferId, "COMPLETED", null, System.currentTimeMillis())

            val parentPath = if (transferEntity.virtualPath.contains('/')) {
                val parent = transferEntity.virtualPath.substringBeforeLast('/')
                if (parent.isEmpty()) "/" else parent
            } else {
                "/"
            }

            localRepository.ensureFolderExists(parentPath, transferEntity.telegramChatId)
            val parentFolder = if (parentPath.isNotEmpty() && parentPath != "/") {
                folderDao.getByPathAndChat(parentPath, transferEntity.telegramChatId)
            } else null

            val existingFile = fileDao.getByMessageId(transferEntity.telegramChatId, msgInfo.messageId)
                ?: (if (transferEntity.fileSize > 0) fileDao.getByChatNameAndSize(transferEntity.telegramChatId, transferEntity.fileName, transferEntity.fileSize) else null)

            val fileEntity = FileEntity(
                fileId = existingFile?.fileId ?: 0,
                telegramMessageId = msgInfo.messageId,
                telegramChatId = transferEntity.telegramChatId,
                virtualPath = transferEntity.virtualPath,
                fileName = transferEntity.fileName,
                fileSize = if (transferEntity.fileSize > 0) transferEntity.fileSize else msgInfo.documentSize,
                mimeType = mimeType,
                telegramFileId = msgInfo.documentFileId,
                thumbnailFileId = msgInfo.thumbnailFileId,
                uploadTimestamp = System.currentTimeMillis(),
                parentFolderId = parentFolder?.folderId,
                isSynced = true
            )
            fileDao.upsertByMessageId(fileEntity)
            fileDao.deleteDuplicatesByNameAndSize()

            // Delete temporary local file
            try {
                File(transferEntity.localFilePath).delete()
            } catch (ignored: Exception) {}

            return Result.success()
        } catch (e: Exception) {
            transferDao.updateStatus(transferId, "FAILED", e.message, System.currentTimeMillis())
            return Result.failure()
        }
    }
}
