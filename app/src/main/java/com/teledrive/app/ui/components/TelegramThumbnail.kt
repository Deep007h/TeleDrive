package com.teledrive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.data.db.entity.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TelegramThumbnail(
    file: FileEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val thumbManager = TeleDriveApplication.instance.thumbnailCacheManager

    val cacheKey = remember(file.fileId, file.telegramFileId, file.thumbnailFileId, file.fileName) {
        arrayOf(file.fileId, file.telegramFileId, file.thumbnailFileId, file.fileName)
    }

    val initialCachedPath = remember(cacheKey) {
        thumbManager.getFastCachedPath(file)
    }

    var localPath by remember(cacheKey) {
        mutableStateOf(initialCachedPath)
    }

    LaunchedEffect(cacheKey) {
        if (localPath == null) {
            withContext(Dispatchers.IO) {
                val fetched = thumbManager.getOrFetchThumbnail(file)
                if (!fetched.isNullOrEmpty()) {
                    localPath = fetched
                }
            }
        }
    }

    val activePath = localPath
    val hasImage = !activePath.isNullOrEmpty() && File(activePath).exists()

    Box(
        modifier = modifier.background(Color(0xFF20202A)),
        contentAlignment = Alignment.Center
    ) {
        if (hasImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(activePath!!))
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(if (initialCachedPath != null) false else true)
                    .build(),
                contentDescription = file.fileName,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FileIcon(
                mimeType = file.mimeType,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            )
        }
    }
}
