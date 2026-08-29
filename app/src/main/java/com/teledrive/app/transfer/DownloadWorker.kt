package com.teledrive.app.transfer

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.core.FileUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val transferId = inputData.getLong("transfer_id", -1L)
        if (transferId == -1L) return Result.failure()

        val app = context.applicationContext as TeleDriveApplication
        val transferDao = app.database.transferDao()
        val fileDao = app.database.fileDao()
        val tdLibManager = app.tdLibManager

        val transferEntity = transferDao.getById(transferId) ?: return Result.failure()

        transferDao.updateStatus(transferId, "IN_PROGRESS", null, System.currentTimeMillis())
        val notificationManager = TransferNotificationManager()

        try {
            val fileName = transferEntity.fileName
            val notification = notificationManager.createNotification(
                context = context,
                fileName = fileName,
                progress = 0,
                isUpload = false
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setForeground(ForegroundInfo(transferId.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                } else {
                    setForeground(ForegroundInfo(transferId.toInt(), notification))
                }
            } catch (ignored: Exception) {}

            var tdFileId = 0
            val msgId = transferEntity.telegramMessageId ?: 0L
            if (transferEntity.telegramChatId != 0L && msgId != 0L) {
                val fileByMsg = fileDao.getByMessageId(transferEntity.telegramChatId, msgId)
                if (fileByMsg != null) {
                    tdFileId = fileByMsg.telegramFileId
                }
            }

            if (tdFileId == 0) {
                val fileEntity = fileDao.getByPath(transferEntity.virtualPath)
                tdFileId = fileEntity?.telegramFileId ?: 0
            }

            if (tdFileId != 0) {
                var completedLocalPath: String? = null

                try {
                    val tdFile = tdLibManager.getFile(tdFileId)
                    if (tdFile.local.isDownloadingCompleted && tdFile.local.path.isNotEmpty() && File(tdFile.local.path).exists()) {
                        completedLocalPath = tdFile.local.path
                    }
                } catch (ignored: Exception) {}

                if (completedLocalPath == null) {
                    try {
                        tdLibManager.startDownload(tdFileId, 32)
                    } catch (ignored: Exception) {}

                    val timeoutResult = withTimeoutOrNull(180_000L) {
                        tdLibManager.fileUpdates
                            .filter { it.fileId == tdFileId }
                            .collect { update ->
                                val progress = if (update.expectedSize > 0) {
                                    ((update.downloadedSize.toFloat() / update.expectedSize) * 100).toInt()
                                } else if (transferEntity.fileSize > 0) {
                                    ((update.downloadedSize.toFloat() / transferEntity.fileSize) * 100).toInt()
                                } else 0

                                transferDao.updateProgress(transferId, update.downloadedSize, System.currentTimeMillis())

                                val progressNotification = notificationManager.createNotification(
                                    context = context,
                                    fileName = fileName,
                                    progress = progress.coerceIn(0, 100),
                                    isUpload = false
                                )
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        setForeground(ForegroundInfo(transferId.toInt(), progressNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                                    } else {
                                        setForeground(ForegroundInfo(transferId.toInt(), progressNotification))
                                    }
                                } catch (ignored: Exception) {}

                                if (update.isDownloadingCompleted && update.localPath.isNotEmpty() && File(update.localPath).exists()) {
                                    completedLocalPath = update.localPath
                                    cancel()
                                }
                            }
                    }
                    
                    // If timeout occurred and download is not finished, try one more time with downloadFile
                    if (timeoutResult == null && completedLocalPath == null) {
                        val finalPath = tdLibManager.downloadFile(tdFileId, 32)
                        if (finalPath.isNotEmpty() && File(finalPath).exists()) {
                            completedLocalPath = finalPath
                        }
                    }
                }

                if (completedLocalPath == null) {
                    val finalPath = tdLibManager.downloadFile(tdFileId, 32)
                    if (finalPath.isNotEmpty() && File(finalPath).exists()) {
                        completedLocalPath = finalPath
                    }
                }

                if (completedLocalPath != null && completedLocalPath!!.isNotEmpty()) {
                    val sourceFile = File(completedLocalPath!!)
                    val downloadDir = FileUtils.getDownloadDir(context)
                    val destFile = File(downloadDir, fileName)
                    sourceFile.copyTo(destFile, overwrite = true)

                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                    transferDao.updateStatus(transferId, "COMPLETED", null, System.currentTimeMillis())

                    val completedNotification = notificationManager.createCompletedNotification(context, fileName, false)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(transferId.toInt(), completedNotification)

                    return Result.success()
                }
            }

            transferDao.updateStatus(transferId, "FAILED", "Could not locate telegram file ID", System.currentTimeMillis())
            return Result.failure()
        } catch (e: Exception) {
            transferDao.updateStatus(transferId, "FAILED", e.message, System.currentTimeMillis())
            return if (e is java.io.IOException) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
