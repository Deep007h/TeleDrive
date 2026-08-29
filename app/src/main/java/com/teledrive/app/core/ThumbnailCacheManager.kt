package com.teledrive.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Process
import android.util.LruCache
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.telegram.TdLibManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class ThumbnailCacheManager(
    private val context: Context,
    private val tdLibManager: TdLibManager
) {
    // Dedicated limited-parallelism background dispatcher running at lower CPU priority
    // so Main UI thread and smooth 60fps/120fps scrolling are never starved!
    private val backgroundDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val scope = CoroutineScope(backgroundDispatcher + SupervisorJob())
    private val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
    
    // In-memory fast cache (ChatId_MessageId -> LocalFilePath)
    private val memoryPathCache = LruCache<String, String>(1000)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()
    private val mutex = Mutex()

    fun getCacheKey(file: FileEntity): String {
        return if (file.telegramChatId != 0L && file.telegramMessageId != 0L) {
            "thumb_${file.telegramChatId}_${file.telegramMessageId}.jpg"
        } else if (file.fileSize > 0) {
            "thumb_${file.fileName.hashCode()}_${file.fileSize}.jpg"
        } else {
            "thumb_${file.fileName.hashCode()}_${file.fileId}.jpg"
        }
    }

    fun getLookupKey(file: FileEntity): String {
        return if (file.telegramChatId != 0L && file.telegramMessageId != 0L) {
            "${file.telegramChatId}_${file.telegramMessageId}"
        } else {
            "${file.fileName.hashCode()}_${file.fileSize}"
        }
    }

    /**
     * Synchronous fast lookup for Frame-0 instant rendering without any flicker or placeholder.
     */
    fun getFastCachedPath(file: FileEntity): String? {
        val lookupKey = getLookupKey(file)
        
        // 1. Check RAM Cache
        memoryPathCache.get(lookupKey)?.let { path ->
            if (File(path).exists()) return path
        }

        // 2. Check Permanent Thumbnail Store
        val thumbFile = File(thumbDir, getCacheKey(file))
        if (thumbFile.exists() && thumbFile.length() > 0) {
            val path = thumbFile.absolutePath
            memoryPathCache.put(lookupKey, path)
            return path
        }

        // 3. Check App Cache Dir
        val cachedAppFile = File(context.cacheDir, file.fileName)
        if (cachedAppFile.exists() && cachedAppFile.length() > 0) {
            val path = cachedAppFile.absolutePath
            memoryPathCache.put(lookupKey, path)
            return path
        }

        return null
    }

    /**
     * Asynchronous fetch and persistent cache on disk.
     */
    suspend fun getOrFetchThumbnail(file: FileEntity): String? = withContext(backgroundDispatcher) {
        // Fast path
        getFastCachedPath(file)?.let { return@withContext it }

        val lookupKey = getLookupKey(file)
        
        // Deduplicate in-flight fetch requests
        val deferred = inFlightRequests.computeIfAbsent(lookupKey) {
            scope.async {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (ignored: Exception) {}
                fetchAndStoreThumbnail(file)
            }
        }

        try {
            deferred.await()
        } finally {
            inFlightRequests.remove(lookupKey)
        }
    }

    private suspend fun fetchAndStoreThumbnail(file: FileEntity): String? {
        val targetThumbFile = File(thumbDir, getCacheKey(file))
        if (targetThumbFile.exists() && targetThumbFile.length() > 0) {
            val path = targetThumbFile.absolutePath
            memoryPathCache.put(getLookupKey(file), path)
            return path
        }

        val isImage = file.mimeType.startsWith("image/") || file.fileName.endsWith(".jpg", true) || file.fileName.endsWith(".jpeg", true) || file.fileName.endsWith(".png", true) || file.fileName.endsWith(".webp", true)
        val isVideo = file.mimeType.startsWith("video/") || file.fileName.endsWith(".mp4", true) || file.fileName.endsWith(".mov", true) || file.fileName.endsWith(".mkv", true)

        var targetFileId = when {
            file.thumbnailFileId != null && file.thumbnailFileId != 0 -> file.thumbnailFileId!!
            (isImage || isVideo) && file.telegramFileId != 0 -> file.telegramFileId
            else -> 0
        }

        // If target file ID is missing/0, resolve from Telegram message
        if (targetFileId == 0 && file.telegramMessageId != 0L) {
            val chatId = if (file.telegramChatId != 0L) file.telegramChatId else tdLibManager.getSavedMessagesChatId()
            if (chatId != 0L) {
                val info = tdLibManager.getMessageInfo(chatId, file.telegramMessageId)
                if (info != null) {
                    targetFileId = if (info.thumbnailFileId != null && info.thumbnailFileId != 0) {
                        info.thumbnailFileId
                    } else if (isImage || isVideo) {
                        info.documentFileId
                    } else 0
                }
            }
        }

        if (targetFileId == 0) return null

        try {
            val downloadedPath = tdLibManager.downloadFile(targetFileId)
            if (downloadedPath.isNotEmpty()) {
                val sourceFile = File(downloadedPath)
                if (sourceFile.exists() && sourceFile.length() > 0) {
                    // Copy / optimize to permanent thumbnails directory
                    if (sourceFile.length() > 400 * 1024) {
                        saveOptimizedThumbnail(sourceFile, targetThumbFile)
                    } else {
                        sourceFile.copyTo(targetThumbFile, overwrite = true)
                    }

                    val finalPath = targetThumbFile.absolutePath
                    memoryPathCache.put(getLookupKey(file), finalPath)
                    return finalPath
                }
            }
        } catch (e: Exception) {
            AppLogger.w("ThumbCache", "Failed to fetch thumbnail for ${file.fileName}: ${e.message}")
        }
        return null
    }

    private fun saveOptimizedThumbnail(sourceFile: File, targetFile: File) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            var sampleSize = 1
            while ((options.outWidth / sampleSize) >= 480 || (options.outHeight / sampleSize) >= 480) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts)
            if (bitmap != null) {
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        } catch (e: Exception) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }
    }

    /**
     * Proactive preloading of thumbnails in background so scrolling is 100% instant.
     * Paced gently with yields so it never stutters the UI!
     */
    fun preloadThumbnails(files: List<FileEntity>) {
        scope.launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (ignored: Exception) {}

            val mediaFiles = files.filter { file ->
                val mime = file.mimeType.lowercase()
                val name = file.fileName.lowercase()
                mime.startsWith("image/") || mime.startsWith("video/") ||
                        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                        name.endsWith(".webp") || name.endsWith(".mp4") || name.endsWith(".mkv")
            }

            for (file in mediaFiles.take(150)) {
                if (getFastCachedPath(file) == null) {
                    getOrFetchThumbnail(file)
                    // Paced gently to keep UI buttery smooth
                    delay(35)
                }
            }
        }
    }

    /**
     * Evict and delete thumbnail when an item is deleted.
     */
    fun removeThumbnail(file: FileEntity) {
        val lookupKey = getLookupKey(file)
        memoryPathCache.remove(lookupKey)
        val targetThumbFile = File(thumbDir, getCacheKey(file))
        if (targetThumbFile.exists()) {
            targetThumbFile.delete()
        }
    }

    fun removeThumbnailByMessageId(chatId: Long, messageId: Long) {
        val lookupKey = "${chatId}_${messageId}"
        memoryPathCache.remove(lookupKey)
        val targetThumbFile = File(thumbDir, "thumb_${chatId}_${messageId}.jpg")
        if (targetThumbFile.exists()) {
            targetThumbFile.delete()
        }
    }

    fun clearAll() {
        memoryPathCache.evictAll()
        thumbDir.deleteRecursively()
        thumbDir.mkdirs()
    }
}
