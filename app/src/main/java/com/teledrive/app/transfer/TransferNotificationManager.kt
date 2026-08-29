package com.teledrive.app.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class TransferNotificationManager {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "teledrive_transfers"
    }

    fun createNotification(
        context: Context,
        fileName: String,
        progress: Int,
        isUpload: Boolean
    ): Notification {
        createChannel(context)

        val title = if (isUpload) "Uploading $fileName" else "Downloading $fileName"
        val icon = if (isUpload) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download

        val cancelIntent = Intent(context, TransferForegroundService::class.java).apply {
            action = "CANCEL_TRANSFER"
        }
        val pendingCancel = PendingIntent.getService(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .build()
    }

    fun createCompletedNotification(
        context: Context,
        fileName: String,
        isUpload: Boolean
    ): Notification {
        createChannel(context)

        val title = if (isUpload) "Upload complete" else "Download complete"
        val icon = if (isUpload) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_sys_download_done

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(fileName)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for active file transfers"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
