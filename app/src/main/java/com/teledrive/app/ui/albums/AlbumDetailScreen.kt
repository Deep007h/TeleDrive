package com.teledrive.app.ui.albums

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teledrive.app.data.repository.DeviceAlbum
import com.teledrive.app.data.repository.LocalMediaItem
import com.teledrive.app.ui.theme.GoogleDarkBackground
import com.teledrive.app.ui.theme.GoogleDarkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: DeviceAlbum,
    onBack: () -> Unit,
    onUploadItems: (List<LocalMediaItem>) -> Unit,
    onItemClick: (LocalMediaItem) -> Unit = {}
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<Long>() }
    var activeFullscreenItem by remember { mutableStateOf<LocalMediaItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedItemIds.size} selected" else album.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isSelectionMode) {
                            val countText = when {
                                album.videoCount > 0 && album.photoCount > 0 -> "${album.photoCount} photos, ${album.videoCount} videos"
                                album.videoCount > 0 -> "${album.videoCount} videos"
                                else -> "${album.itemCount} items"
                            }
                            Text(
                                text = countText,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedItemIds.clear()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                if (selectedItemIds.size == album.items.size) {
                                    selectedItemIds.clear()
                                } else {
                                    selectedItemIds.clear()
                                    selectedItemIds.addAll(album.items.map { it.id })
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedItemIds.size == album.items.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Toggle Select All",
                                tint = Color.White
                            )
                        }
                    } else {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select Mode", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                onUploadItems(album.items)
                            }
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload All to Cloud", tint = Color(0xFFA8C7FA))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF191922))
            )
        },
        bottomBar = {
            if (isSelectionMode && selectedItemIds.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1E1F2B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedItemIds.size} item(s) chosen",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                val chosen = album.items.filter { it.id in selectedItemIds }
                                onUploadItems(chosen)
                                isSelectionMode = false
                                selectedItemIds.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8C7FA)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF0F141C),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload to Cloud",
                                color = Color(0xFF0F141C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = GoogleDarkBackground
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GoogleDarkBackground)
        ) {
            items(album.items, key = { it.id }) { item ->
                val isSelected = item.id in selectedItemIds

                LocalMediaGridTile(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelectionMode) {
                            if (isSelected) {
                                selectedItemIds.remove(item.id)
                            } else {
                                selectedItemIds.add(item.id)
                            }
                        } else {
                            activeFullscreenItem = item
                            onItemClick(item)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedItemIds.add(item.id)
                        }
                    }
                )
            }
        }
    }

    if (activeFullscreenItem != null) {
        LocalFullscreenMediaViewer(
            initialItem = activeFullscreenItem!!,
            allItems = album.items,
            onClose = { activeFullscreenItem = null },
            onUpload = { itemToUpload ->
                onUploadItems(listOf(itemToUpload))
            }
        )
    }
}

@Composable
fun LocalMediaGridTile(
    item: LocalMediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF22232E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.contentUri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Duration Overlay
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 60f
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatVideoDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Selection Checkbox Badge
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color(0x664285F4) else Color.Transparent)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF4285F4) else Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LocalFullscreenMediaViewer(
    initialItem: LocalMediaItem,
    allItems: List<LocalMediaItem>,
    onClose: () -> Unit,
    onUpload: (LocalMediaItem) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(allItems.indexOf(initialItem).coerceAtLeast(0)) }
    val currentItem = allItems.getOrNull(currentIndex) ?: initialItem

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = currentItem.contentUri,
            contentDescription = currentItem.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Top App Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentItem.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentIndex + 1} of ${allItems.size}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = { onUpload(currentItem) }) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Upload to Cloud", tint = Color(0xFFA8C7FA))
            }
        }
    }
}

private fun formatVideoDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        String.format("%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
