package com.teledrive.app.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.data.repository.UnifiedMediaItem
import com.teledrive.app.ui.components.TelegramThumbnail
import com.teledrive.app.ui.theme.GoogleDarkBackground
import com.teledrive.app.ui.theme.GoogleDarkCard
import com.teledrive.app.ui.theme.GoogleOnDarkText
import com.teledrive.app.ui.theme.GoogleOnDarkTextMuted
import com.teledrive.app.ui.theme.GoogleOnDarkTextSubtle
import com.teledrive.app.ui.theme.GooglePrimaryAccent
import com.teledrive.app.ui.theme.GoogleTertiaryAccent
import java.text.SimpleDateFormat
import java.util.*

@Immutable
data class MemoryStory(
    val id: String,
    val title: String,
    val dateTag: String,
    val item: UnifiedMediaItem
)

private val MEMORY_TITLES = listOf("Revisit the moment", "Mata and Daddy", "Best of July", "Recent Highlights", "Special Memories")

@Composable
fun GooglePhotosMainGrid(
    items: List<UnifiedMediaItem>,
    onItemClick: (UnifiedMediaItem) -> Unit,
    onStoryClick: (MemoryStory) -> Unit = {}
) {
    val columnCount = 3
    val gridState = rememberLazyGridState()

    val memoryStories = remember(items) {
        if (items.isEmpty()) emptyList()
        else {
            items.take(4).mapIndexed { index, mediaItem ->
                MemoryStory(
                    id = "memory_$index",
                    title = MEMORY_TITLES.getOrElse(index) { "Highlights" },
                    dateTag = "JUL 2025",
                    item = mediaItem
                )
            }
        }
    }

    // Group items by Day Date
    val groupedItems = remember(items) {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.ENGLISH)
        val map = linkedMapOf<String, MutableList<UnifiedMediaItem>>()
        for (item in items) {
            val dateKey = if (item.dateModified > 0) sdf.format(Date(item.dateModified)) else "Recent"
            map.getOrPut(dateKey) { mutableListOf() }.add(item)
        }
        map
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoogleDarkBackground)
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GooglePrimaryAccent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Photos or Videos Found",
                        color = GoogleOnDarkTextMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add media from the + button to back it up",
                        color = GoogleOnDarkTextSubtle,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Memory Highlights Slideshow Carousel
                if (memoryStories.isNotEmpty()) {
                    item(key = "stories_header", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SectionHeader(text = "Memories")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                items(memoryStories, key = { it.id }) { memory ->
                                    MemoryStoryCard(memory = memory, onClick = { onStoryClick(memory) })
                                }
                            }
                        }
                    }
                }

                // Render each Date Group
                groupedItems.forEach { (dateHeader, groupItems) ->
                    item(key = "header_$dateHeader", span = { GridItemSpan(maxLineSpan) }) {
                        DateHeaderRow(
                            text = dateHeader,
                            subtitle = "${groupItems.size} items • Telegram Cloud"
                        )
                    }

                    itemsIndexed(
                        items = groupItems,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        GoogleMediaTile(
                            item = item,
                            itemIndex = index,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = GoogleOnDarkText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun DateHeaderRow(text: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = text,
                color = GoogleOnDarkText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = GoogleOnDarkTextSubtle,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun MemoryStoryCard(
    memory: MemoryStory,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(138.dp)
            .height(184.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GoogleDarkCard)
            .clickable(onClick = onClick)
    ) {
        if (memory.item.localUri != null) {
            AsyncImage(
                model = memory.item.localUri,
                contentDescription = memory.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (memory.item.cloudFile != null) {
            TelegramThumbnail(
                file = memory.item.cloudFile,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = memory.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = memory.dateTag,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GoogleMediaTile(
    item: UnifiedMediaItem,
    isTopRowItem: Boolean = false,
    itemIndex: Int = 0,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF16161D))
            .clickable(onClick = onClick)
    ) {
        if (item.localUri != null) {
            AsyncImage(
                model = item.localUri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (item.cloudFile != null) {
            TelegramThumbnail(
                file = item.cloudFile,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle top-down gradient for badge readability on light photos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
        )

        // Video duration pill
        if (item.isVideo) {
            DurationPill(
                durationMs = item.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }

        // Cloud Synced Badge
        if (item.isCloudBackedUp) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Backed up",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleMediaTile(
    item: FileEntity,
    isTopRowItem: Boolean = false,
    itemIndex: Int = 0,
    onClick: () -> Unit
) {
    val isVideo = item.mimeType.startsWith("video/") || item.fileName.endsWith(".mp4", true) || item.fileName.endsWith(".mkv", true)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF16161D))
            .clickable(onClick = onClick)
    ) {
        TelegramThumbnail(
            file = item,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
        )

        if (isVideo) {
            DurationPill(
                durationMs = 0,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                fallbackText = "0:16"
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Backed up",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun DurationPill(
    durationMs: Long,
    modifier: Modifier = Modifier,
    fallbackText: String? = null
) {
    val durationStr = when {
        durationMs > 0 -> {
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
        fallbackText != null -> fallbackText
        else -> "0:00"
    }

    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = durationStr,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
