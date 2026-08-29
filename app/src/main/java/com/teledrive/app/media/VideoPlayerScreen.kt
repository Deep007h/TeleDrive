package com.teledrive.app.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.data.db.entity.FileEntity
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(fileId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = TeleDriveApplication.instance
    val fileDao = app.database.fileDao()
    val tdLibManager = app.tdLibManager

    var fileEntity by remember { mutableStateOf<FileEntity?>(null) }
    var localPath by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(fileId) {
        val files = fileDao.getAllFilesList()
        val found = files.firstOrNull { it.fileId == fileId }
        fileEntity = found

        if (found != null) {
            val tdFileId = found.telegramFileId
            if (tdFileId != 0) {
                // Check if already downloaded/cached
                try {
                    val tdFile = tdLibManager.getFile(tdFileId)
                    if (tdFile.local.isDownloadingCompleted && tdFile.local.path.isNotEmpty() && File(tdFile.local.path).exists()) {
                        localPath = tdFile.local.path
                        isLoading = false
                        return@LaunchedEffect
                    }
                } catch (ignored: Exception) {}

                try {
                    tdLibManager.startDownload(tdFileId, 32)
                } catch (ignored: Exception) {}

                // Trigger background download waiter
                launch {
                    val path = tdLibManager.downloadFile(tdFileId, 32)
                    if (path.isNotEmpty() && File(path).exists()) {
                        localPath = path
                        isLoading = false
                    }
                }

                tdLibManager.fileUpdates
                    .filter { it.fileId == tdFileId }
                    .collect { update ->
                        if (update.isDownloadingCompleted && update.localPath.isNotEmpty() && File(update.localPath).exists()) {
                            localPath = update.localPath
                            isLoading = false
                        }
                    }
            } else {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    LaunchedEffect(localPath) {
        localPath?.let { path ->
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path)))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileEntity?.fileName ?: "Video Player",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        fileEntity?.let {
                            coroutineScope.launch {
                                app.transferManager.enqueueDownload(
                                    virtualPath = it.virtualPath,
                                    fileName = it.fileName,
                                    fileSize = it.fileSize,
                                    chatId = it.telegramChatId,
                                    messageId = it.telegramMessageId
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
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
                Text(
                    text = "Failed to load video",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
