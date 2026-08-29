package com.teledrive.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.teledrive.app.data.db.entity.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable

@Immutable
data class LocalMediaItem(
    val id: Long,
    val contentUri: Uri,
    val filePath: String,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val dateModified: Long,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val bucketId: String,
    val bucketName: String
)

@Immutable
data class DeviceAlbum(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val coverPath: String,
    val itemCount: Int,
    val videoCount: Int,
    val photoCount: Int,
    val items: List<LocalMediaItem>
)

@Immutable
data class UnifiedMediaItem(
    val id: String,
    val displayName: String,
    val dateModified: Long,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val mimeType: String,
    val fileSize: Long,
    val localUri: Uri? = null,
    val localPath: String? = null,
    val cloudFile: FileEntity? = null,
    val isCloudBackedUp: Boolean = false,
    val isLocalOnDevice: Boolean = false,
    val bucketName: String? = null
)

class DeviceMediaRepository(private val context: Context) {

    private val mutex = Mutex()
    private var cachedLocalMedia: List<LocalMediaItem>? = null
    private var cachedDeviceAlbums: List<DeviceAlbum>? = null

    suspend fun getAllDeviceMedia(forceRefresh: Boolean = false): List<LocalMediaItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!forceRefresh && cachedLocalMedia != null) {
                return@withContext cachedLocalMedia!!
            }

            val allMedia = mutableListOf<LocalMediaItem>()

            // 1. Query Images
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageProjection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                        val path = cursor.getString(dataCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val date = cursor.getLong(dateCol) * 1000L
                        val bucketId = cursor.getString(bucketIdCol) ?: "0"
                        val bucketName = cursor.getString(bucketNameCol) ?: "Pictures"
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                        allMedia.add(
                            LocalMediaItem(
                                id = id,
                                contentUri = contentUri,
                                filePath = path,
                                displayName = name,
                                size = size,
                                mimeType = mime,
                                dateModified = date,
                                isVideo = false,
                                bucketId = bucketId,
                                bucketName = bucketName
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Query Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )

            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoProjection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                        val path = cursor.getString(dataCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol) ?: "video/mp4"
                        val date = cursor.getLong(dateCol) * 1000L
                        val bucketId = cursor.getString(bucketIdCol) ?: "0"
                        val bucketName = cursor.getString(bucketNameCol) ?: "Videos"
                        val duration = cursor.getLong(durCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        allMedia.add(
                            LocalMediaItem(
                                id = id,
                                contentUri = contentUri,
                                filePath = path,
                                displayName = name,
                                size = size,
                                mimeType = mime,
                                dateModified = date,
                                isVideo = true,
                                durationMs = duration,
                                bucketId = bucketId,
                                bucketName = bucketName
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val sorted = allMedia.sortedByDescending { it.dateModified }
            cachedLocalMedia = sorted
            sorted
        }
    }

    suspend fun getDeviceAlbums(forceRefresh: Boolean = false): List<DeviceAlbum> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedDeviceAlbums != null) {
            return@withContext cachedDeviceAlbums!!
        }

        val allMedia = getAllDeviceMedia(forceRefresh)
        val grouped = allMedia.groupBy { it.bucketName }
        val albums = grouped.map { (bucketName, items) ->
            val sortedItems = items.sortedByDescending { it.dateModified }
            val cover = sortedItems.first()
            val videoCount = sortedItems.count { it.isVideo }
            val photoCount = sortedItems.size - videoCount

            DeviceAlbum(
                bucketId = cover.bucketId,
                name = bucketName,
                coverUri = cover.contentUri,
                coverPath = cover.filePath,
                itemCount = sortedItems.size,
                videoCount = videoCount,
                photoCount = photoCount,
                items = sortedItems
            )
        }.sortedWith(
            compareByDescending<DeviceAlbum> {
                when (it.name.lowercase()) {
                    "camera" -> 100
                    "screenshots" -> 90
                    "whatsapp images" -> 80
                    "whatsapp video" -> 75
                    "pictures" -> 70
                    "movies", "videos" -> 65
                    "downloads", "download" -> 60
                    else -> 0
                }
            }.thenByDescending { it.itemCount }
        )

        cachedDeviceAlbums = albums
        albums
    }

    suspend fun buildUnifiedMedia(cloudFiles: List<FileEntity>): List<UnifiedMediaItem> = withContext(Dispatchers.IO) {
        val localMedia = getAllDeviceMedia(forceRefresh = false)
        val unified = ArrayList<UnifiedMediaItem>(localMedia.size + cloudFiles.size)
        val matchedCloudFileIds = HashSet<Long>(cloudFiles.size)

        // Build fast lookup index for cloud files
        val cloudByName = HashMap<String, MutableList<FileEntity>>()
        for (cloud in cloudFiles) {
            val key = cloud.fileName.lowercase().trim()
            cloudByName.getOrPut(key) { mutableListOf() }.add(cloud)
        }

        for (local in localMedia) {
            val key = local.displayName.lowercase().trim()
            val candidateClouds = cloudByName[key]
            val matchingCloud = candidateClouds?.firstOrNull { cloud ->
                cloud.fileSize == local.size || Math.abs(cloud.fileSize - local.size) < 4096
            } ?: candidateClouds?.firstOrNull()

            if (matchingCloud != null) {
                matchedCloudFileIds.add(matchingCloud.fileId)
                unified.add(
                    UnifiedMediaItem(
                        id = "local_${local.id}",
                        displayName = local.displayName,
                        dateModified = local.dateModified,
                        isVideo = local.isVideo,
                        durationMs = local.durationMs,
                        mimeType = local.mimeType,
                        fileSize = local.size,
                        localUri = local.contentUri,
                        localPath = local.filePath,
                        cloudFile = matchingCloud,
                        isCloudBackedUp = true,
                        isLocalOnDevice = true,
                        bucketName = local.bucketName
                    )
                )
            } else {
                unified.add(
                    UnifiedMediaItem(
                        id = "local_${local.id}",
                        displayName = local.displayName,
                        dateModified = local.dateModified,
                        isVideo = local.isVideo,
                        durationMs = local.durationMs,
                        mimeType = local.mimeType,
                        fileSize = local.size,
                        localUri = local.contentUri,
                        localPath = local.filePath,
                        cloudFile = null,
                        isCloudBackedUp = false,
                        isLocalOnDevice = true,
                        bucketName = local.bucketName
                    )
                )
            }
        }

        // Add remaining cloud files that are not stored locally on device
        for (cloud in cloudFiles) {
            if (!matchedCloudFileIds.contains(cloud.fileId)) {
                val isVid = cloud.mimeType.startsWith("video/") || cloud.fileName.endsWith(".mp4", true) || cloud.fileName.endsWith(".mkv", true)
                unified.add(
                    UnifiedMediaItem(
                        id = "cloud_${cloud.fileId}",
                        displayName = cloud.fileName,
                        dateModified = cloud.uploadTimestamp,
                        isVideo = isVid,
                        durationMs = 0L,
                        mimeType = cloud.mimeType,
                        fileSize = cloud.fileSize,
                        localUri = null,
                        localPath = null,
                        cloudFile = cloud,
                        isCloudBackedUp = true,
                        isLocalOnDevice = false,
                        bucketName = "Telegram Cloud"
                    )
                )
            }
        }

        unified.sortedByDescending { it.dateModified }
    }
}
