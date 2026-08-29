package com.teledrive.app.ui.viewer

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.core.FileUtils
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.ui.theme.GoogleOnDarkTextMuted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialItem: FileEntity,
    allItems: List<FileEntity>,
    onBack: () -> Unit,
    onDelete: (FileEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = TeleDriveApplication.instance

    var activeList by remember(allItems) { mutableStateOf(allItems) }
    val effectiveList = if (activeList.isEmpty()) listOf(initialItem) else activeList

    val initialIndex = effectiveList.indexOfFirst { it.fileId == initialItem.fileId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { effectiveList.size })

    val currentItem = effectiveList.getOrNull(pagerState.currentPage.coerceIn(0, effectiveList.size - 1)) ?: initialItem
    var showControls by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var showThreeDotsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Swipe down to dismiss
    val offsetY = remember { Animatable(0f) }
    val dragScale by remember { derivedStateOf { (1f - (offsetY.value / 1200f)).coerceIn(0.55f, 1f) } }
    val dragAlpha by remember { derivedStateOf { (1f - (offsetY.value / 800f)).coerceIn(0.2f, 1f) } }
    val bgDimAlpha by remember { derivedStateOf { (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgDimAlpha))
            .graphicsLayer {
                translationY = offsetY.value
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
            }
    ) {
        HorizontalPager(
            state = pagerState,
            key = { idx -> effectiveList.getOrNull(idx)?.fileId ?: idx },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = effectiveList.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = (pagerState.currentPage == page)

            val isVideo = item.mimeType.startsWith("video/") || item.fileName.endsWith(".mp4", true) || item.fileName.endsWith(".mkv", true)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY.value > 0) {
                                    scope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                                }
                            },
                            onDragEnd = {
                                if (offsetY.value > 200f) {
                                    onBack()
                                } else {
                                    scope.launch { offsetY.animateTo(0f, tween(150)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetY.animateTo(0f, tween(150)) }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isVideo) {
                    SingleVideoPlayerPage(
                        file = item,
                        isCurrentPage = isCurrentPage,
                        onTap = { showControls = !showControls }
                    )
                } else {
                    SingleImageViewerPage(
                        file = item,
                        onTap = { showControls = !showControls }
                    )
                }
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(animationSpec = tween(180)) { -it } + fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(160)) { -it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatViewerDate(currentItem.uploadTimestamp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "${formatViewerTime(currentItem.uploadTimestamp)} • Telegram Cloud",
                                fontSize = 11.sp,
                                color = GoogleOnDarkTextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFFFC107) else Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { showThreeDotsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = showThreeDotsMenu,
                                onDismissRequest = { showThreeDotsMenu = false },
                                modifier = Modifier
                                    .width(220.dp)
                                    .background(Color(0xFF23232F), RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Download", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color.White) },
                                    onClick = {
                                        showThreeDotsMenu = false
                                        scope.launch {
                                            app.transferManager.enqueueDownload(
                                                virtualPath = currentItem.virtualPath,
                                                fileName = currentItem.fileName,
                                                fileSize = currentItem.fileSize,
                                                chatId = currentItem.telegramChatId,
                                                messageId = currentItem.telegramMessageId
                                            )
                                            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open with…", color = Color.White, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White) },
                                    onClick = {
                                        showThreeDotsMenu = false
                                        val destDir = FileUtils.getDownloadDir(context)
                                        val destFile = File(destDir, currentItem.fileName)
                                        if (destFile.exists()) {
                                            FileUtils.openFile(context, destFile.absolutePath, currentItem.fileName)
                                        } else {
                                            Toast.makeText(context, "Download the file first to open externally", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showThreeDotsMenu = false
                                        onDelete(currentItem)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }

        // Bottom Action Bar: Share, Edit, Download, Delete
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(animationSpec = tween(180)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(160)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerBottomAction(icon = Icons.Default.Share, label = "Share", onClick = {})
                    ViewerBottomAction(icon = Icons.Default.Edit, label = "Edit", onClick = {})
                    ViewerBottomAction(
                        icon = Icons.Default.Download,
                        label = "Download",
                        onClick = {
                            scope.launch {
                                app.transferManager.enqueueDownload(
                                    virtualPath = currentItem.virtualPath,
                                    fileName = currentItem.fileName,
                                    fileSize = currentItem.fileSize,
                                    chatId = currentItem.telegramChatId,
                                    messageId = currentItem.telegramMessageId
                                )
                                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ViewerBottomAction(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        onClick = { showDeleteConfirmDialog = true }
                    )
                }
            }
        }

        // Delete Confirmation Dialog at Root Level
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = Color(0xFF1E293B),
                title = {
                    Text("Delete from Telegram Cloud?", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Are you sure you want to permanently delete \"${currentItem.fileName}\" from your Telegram Saved Messages and cloud storage?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete(currentItem)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
private fun ViewerBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SingleImageViewerPage(
    file: FileEntity,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val app = TeleDriveApplication.instance
    val tdLibManager = app.tdLibManager
    var localPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file.fileId, file.telegramFileId, file.telegramMessageId) {
        isLoading = true
        errorMessage = null

        val cached = File(context.cacheDir, file.fileName)
        if (cached.exists() && cached.length() > 0) {
            localPath = cached.absolutePath
            isLoading = false
            return@LaunchedEffect
        }

        val downloaded = File(com.teledrive.app.core.FileUtils.getDownloadDir(context), file.fileName)
        if (downloaded.exists() && downloaded.length() > 0) {
            localPath = downloaded.absolutePath
            isLoading = false
            return@LaunchedEffect
        }

        var currentFileId = file.telegramFileId
        if (currentFileId != 0) {
            try {
                val tdFile = tdLibManager.getFile(currentFileId)
                if (tdFile.local.isDownloadingCompleted && tdFile.local.path.isNotEmpty() && File(tdFile.local.path).exists()) {
                    localPath = tdFile.local.path
                    isLoading = false
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                currentFileId = 0
            }
        }

        if (currentFileId == 0 && file.telegramMessageId != 0L) {
            val chatId = if (file.telegramChatId != 0L) file.telegramChatId else tdLibManager.getSavedMessagesChatId()
            if (chatId != 0L) {
                val info = tdLibManager.getMessageInfo(chatId, file.telegramMessageId)
                if (info != null && info.documentFileId != 0) {
                    currentFileId = info.documentFileId
                    app.database.fileDao().updateFileIds(file.fileId, info.documentFileId, info.thumbnailFileId)
                } else if (info == null) {
                    app.database.fileDao().delete(file)
                }
            }
        }

        if (currentFileId != 0) {
            try {
                tdLibManager.startDownload(currentFileId, 32)
            } catch (ignored: Exception) {}

            launch {
                val finalPath = tdLibManager.downloadFile(currentFileId, 32)
                if (finalPath.isNotEmpty() && File(finalPath).exists()) {
                    localPath = finalPath
                    isLoading = false
                } else if (localPath == null) {
                    isLoading = false
                    errorMessage = "Failed to download image"
                }
            }

            tdLibManager.fileUpdates
                .filter { it.fileId == currentFileId }
                .collect { update ->
                    if (update.isDownloadingCompleted && update.localPath.isNotEmpty() && File(update.localPath).exists()) {
                        localPath = update.localPath
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
            errorMessage = "Unable to locate photo on Telegram servers"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        if (scale > 1f) {
                            scope.launch {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        } else {
                            scope.launch {
                                scale = 2.5f
                                offsetX = -offset.x * 1.5f
                                offsetY = -offset.y * 1.5f
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && localPath == null) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (localPath != null) {
            AsyncImage(
                model = File(localPath!!),
                contentDescription = file.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "Unable to load photo",
                    color = GoogleOnDarkTextMuted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SingleVideoPlayerPage(
    file: FileEntity,
    isCurrentPage: Boolean,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val app = TeleDriveApplication.instance
    val tdLibManager = app.tdLibManager
    var localPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(file.fileId, file.telegramFileId, file.telegramMessageId) {
        isLoading = true
        errorMessage = null

        // 1. Check local cache or download folder
        val cached = File(context.cacheDir, file.fileName)
        if (cached.exists() && cached.length() > 0) {
            localPath = cached.absolutePath
            isLoading = false
            return@LaunchedEffect
        }

        val downloaded = File(com.teledrive.app.core.FileUtils.getDownloadDir(context), file.fileName)
        if (downloaded.exists() && downloaded.length() > 0) {
            localPath = downloaded.absolutePath
            isLoading = false
            return@LaunchedEffect
        }

        // 2. Determine target file ID
        var currentFileId = file.telegramFileId
        if (currentFileId != 0) {
            try {
                val tdFile = tdLibManager.getFile(currentFileId)
                if (tdFile.local.isDownloadingCompleted && tdFile.local.path.isNotEmpty() && File(tdFile.local.path).exists()) {
                    localPath = tdFile.local.path
                    isLoading = false
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                currentFileId = 0
            }
        }

        // 3. If stale or 0, rehydrate from TDLib message
        if (currentFileId == 0 && file.telegramMessageId != 0L) {
            val chatId = if (file.telegramChatId != 0L) file.telegramChatId else tdLibManager.getSavedMessagesChatId()
            if (chatId != 0L) {
                val info = tdLibManager.getMessageInfo(chatId, file.telegramMessageId)
                if (info != null && info.documentFileId != 0) {
                    currentFileId = info.documentFileId
                    app.database.fileDao().updateFileIds(file.fileId, info.documentFileId, info.thumbnailFileId)
                } else if (info == null) {
                    app.database.fileDao().delete(file)
                }
            }
        }

        if (currentFileId != 0) {
            try {
                tdLibManager.startDownload(currentFileId, 32)
            } catch (ignored: Exception) {}

            launch {
                val finalPath = tdLibManager.downloadFile(currentFileId, 32)
                if (finalPath.isNotEmpty() && File(finalPath).exists()) {
                    localPath = finalPath
                    isLoading = false
                } else if (localPath == null) {
                    isLoading = false
                    errorMessage = "Failed to download video"
                }
            }

            tdLibManager.fileUpdates
                .filter { it.fileId == currentFileId }
                .collect { update ->
                    if (update.isDownloadingCompleted && update.localPath.isNotEmpty() && File(update.localPath).exists()) {
                        localPath = update.localPath
                        isLoading = false
                    }
                }
        } else {
            isLoading = false
            errorMessage = "Unable to locate video on Telegram servers"
        }
    }

    LaunchedEffect(localPath, isCurrentPage) {
        if (isCurrentPage && localPath != null) {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(localPath!!)))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && localPath == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        } else if (localPath != null) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(errorMessage ?: "Unable to load video", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

private fun formatViewerDate(timestamp: Long): String {
    val date = Date(if (timestamp > 0) timestamp else System.currentTimeMillis())
    return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
}

private fun formatViewerTime(timestamp: Long): String {
    val date = Date(if (timestamp > 0) timestamp else System.currentTimeMillis())
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMediaViewerScreen(
    initialItem: com.teledrive.app.data.repository.UnifiedMediaItem,
    allItems: List<com.teledrive.app.data.repository.UnifiedMediaItem>,
    onBack: () -> Unit,
    onUpload: (com.teledrive.app.data.repository.UnifiedMediaItem) -> Unit = {},
    onDelete: (com.teledrive.app.data.repository.UnifiedMediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val effectiveList = if (allItems.isEmpty()) listOf(initialItem) else allItems
    val initialIndex = effectiveList.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { effectiveList.size })

    val currentItem = effectiveList.getOrNull(pagerState.currentPage.coerceIn(0, effectiveList.size - 1)) ?: initialItem
    var showControls by remember { mutableStateOf(true) }

    // Swipe down to dismiss
    val offsetY = remember { Animatable(0f) }
    val dragScale by remember { derivedStateOf { (1f - (offsetY.value / 1200f)).coerceIn(0.55f, 1f) } }
    val dragAlpha by remember { derivedStateOf { (1f - (offsetY.value / 800f)).coerceIn(0.2f, 1f) } }
    val bgDimAlpha by remember { derivedStateOf { (1f - (offsetY.value / 600f)).coerceIn(0f, 1f) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgDimAlpha))
            .graphicsLayer {
                translationY = offsetY.value
                scaleX = dragScale
                scaleY = dragScale
                alpha = dragAlpha
            }
    ) {
        HorizontalPager(
            state = pagerState,
            key = { idx -> effectiveList.getOrNull(idx)?.id ?: idx.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = effectiveList.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = (pagerState.currentPage == page)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY.value > 0) {
                                    scope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                                }
                            },
                            onDragEnd = {
                                if (offsetY.value > 200f) {
                                    onBack()
                                } else {
                                    scope.launch { offsetY.animateTo(0f, tween(150)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetY.animateTo(0f, tween(150)) }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    if (item.localUri != null) {
                        SingleLocalVideoPlayerPage(
                            uri = item.localUri,
                            isCurrentPage = isCurrentPage,
                            onTap = { showControls = !showControls }
                        )
                    } else if (item.cloudFile != null) {
                        SingleVideoPlayerPage(
                            file = item.cloudFile,
                            isCurrentPage = isCurrentPage,
                            onTap = { showControls = !showControls }
                        )
                    }
                } else {
                    if (item.localUri != null) {
                        SingleLocalImageViewerPage(
                            uri = item.localUri,
                            onTap = { showControls = !showControls }
                        )
                    } else if (item.cloudFile != null) {
                        SingleImageViewerPage(
                            file = item.cloudFile,
                            onTap = { showControls = !showControls }
                        )
                    }
                }
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = formatViewerDate(currentItem.dateModified),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (currentItem.isCloudBackedUp) "Telegram Cloud" else (currentItem.bucketName ?: "On this device"),
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }

        // Bottom Action Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerBottomAction(icon = Icons.Default.Share, label = "Share", onClick = {
                        currentItem.localUri?.let { uri ->
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = currentItem.mimeType
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                        }
                    })

                    ViewerBottomAction(icon = Icons.Default.Edit, label = "Edit", onClick = {})

                    if (!currentItem.isCloudBackedUp) {
                        ViewerBottomAction(icon = Icons.Default.CloudUpload, label = "Backup", onClick = {
                            onUpload(currentItem)
                            Toast.makeText(context, "Uploading ${currentItem.displayName} to Cloud...", Toast.LENGTH_SHORT).show()
                        })
                    } else {
                        ViewerBottomAction(icon = Icons.Default.CloudDone, label = "Backed Up", onClick = {})
                    }

                    ViewerBottomAction(icon = Icons.Default.Delete, label = "Delete", onClick = {
                        onDelete(currentItem)
                    })
                }
            }
        }
    }
}

@Composable
fun SingleLocalImageViewerPage(
    uri: Uri,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        if (scale > 1f) {
                            scope.launch {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        } else {
                            scope.launch {
                                scale = 2.5f
                                offsetX = -offset.x * 1.5f
                                offsetY = -offset.y * 1.5f
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

@Composable
fun SingleLocalVideoPlayerPage(
    uri: Uri,
    isCurrentPage: Boolean,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(uri, isCurrentPage) {
        if (isCurrentPage) {
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
