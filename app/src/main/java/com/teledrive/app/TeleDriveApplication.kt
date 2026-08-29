package com.teledrive.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.teledrive.app.core.Constants
import com.teledrive.app.data.db.TeleDriveDatabase
import com.teledrive.app.data.preferences.AppPreferences
import com.teledrive.app.data.repository.LocalRepository
import com.teledrive.app.telegram.AuthRepository
import com.teledrive.app.telegram.ChannelRepository
import com.teledrive.app.telegram.FileRepository
import com.teledrive.app.telegram.MetadataParser
import com.teledrive.app.telegram.TdLibManager
import com.teledrive.app.transfer.TransferManager

import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.teledrive.app.core.ThumbnailCacheManager
import java.io.File

class TeleDriveApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_disk_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
    }

    lateinit var database: TeleDriveDatabase
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var tdLibManager: TdLibManager
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var fileRepository: FileRepository
        private set

    lateinit var channelRepository: ChannelRepository
        private set

    lateinit var localRepository: LocalRepository
        private set

    lateinit var transferManager: TransferManager
        private set

    lateinit var deviceMediaRepository: com.teledrive.app.data.repository.DeviceMediaRepository
        private set

    lateinit var peopleRepository: com.teledrive.app.data.repository.PeopleRepository
        private set

    lateinit var thumbnailCacheManager: ThumbnailCacheManager
        private set

    lateinit var otaUpdateManager: com.teledrive.app.core.ota.OtaUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        com.teledrive.app.core.AppLogger.init(this)
        createNotificationChannel()

        database = TeleDriveDatabase.getInstance(this)
        preferences = AppPreferences(this)

        tdLibManager = TdLibManager()
        tdLibManager.initialize(this)

        thumbnailCacheManager = ThumbnailCacheManager(this, tdLibManager)
        otaUpdateManager = com.teledrive.app.core.ota.OtaUpdateManager(this, preferences)
        authRepository = AuthRepository(tdLibManager)
        fileRepository = FileRepository(tdLibManager)
        channelRepository = ChannelRepository(tdLibManager, preferences)
        deviceMediaRepository = com.teledrive.app.data.repository.DeviceMediaRepository(this)
        peopleRepository = com.teledrive.app.data.repository.PeopleRepository(this, tdLibManager)
        localRepository = LocalRepository(
            fileDao = database.fileDao(),
            folderDao = database.folderDao(),
            transferDao = database.transferDao(),
            tdLibManager = tdLibManager,
            metadataParser = MetadataParser
        )
        transferManager = TransferManager(this, database.transferDao())
    }

    override fun onTerminate() {
        super.onTerminate()
        localRepository.close()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Transfers"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = "Upload and download progress notifications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: TeleDriveApplication
            private set
    }
}
