package com.teledrive.app.ui.memories

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.data.repository.UnifiedMediaItem
import com.teledrive.app.ui.components.TelegramThumbnail
import com.teledrive.app.ui.photos.MemoryStory
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MemorySlideshowScreen(
    currentStory: MemoryStory,
    allStories: List<MemoryStory>,
    items: List<UnifiedMediaItem>,
    onClose: () -> Unit,
    onStoryCompleted: (MemoryStory) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var activeStoryIndex by remember(currentStory) {
        mutableStateOf(allStories.indexOfFirst { it.id == currentStory.id }.coerceAtLeast(0))
    }

    val activeStory = allStories.getOrNull(activeStoryIndex) ?: currentStory
    val storyItems = remember(activeStory, items) { items.take(8).ifEmpty { listOf(activeStory.item) } }

    var currentSlideIndex by remember(activeStoryIndex) { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val kenBurnsScale = remember { Animatable(1.0f) }
    val offsetX = remember { Animatable(0f) }

    DisposableEffect(activeStory.id) {
        onDispose {
            onStoryCompleted(activeStory)
        }
    }

    LaunchedEffect(activeStoryIndex, currentSlideIndex, isPaused) {
        if (!isPaused && storyItems.isNotEmpty()) {
            progress.snapTo(0f)
            kenBurnsScale.snapTo(1.0f)

            scope.launch {
                kenBurnsScale.animateTo(
                    targetValue = 1.18f,
                    animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
                )
            }

            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )

            if (currentSlideIndex < storyItems.size - 1) {
                currentSlideIndex++
            } else {
                onStoryCompleted(activeStory)
                if (activeStoryIndex < allStories.size - 1) {
                    activeStoryIndex++
                } else {
                    onClose()
                }
            }
        }
    }

    val currentItem = storyItems.getOrNull(currentSlideIndex) ?: activeStory.item

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(activeStoryIndex) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                        }
                    },
                    onDragEnd = {
                        val dragDistance = offsetX.value
                        if (abs(dragDistance) > 120f) {
                            if (dragDistance < 0) {
                                onStoryCompleted(activeStory)
                                if (activeStoryIndex < allStories.size - 1) {
                                    activeStoryIndex++
                                } else {
                                    onClose()
                                }
                            } else {
                                if (activeStoryIndex > 0) {
                                    activeStoryIndex--
                                }
                            }
                        }
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(150))
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(150))
                        }
                    }
                )
            }
            .pointerInput(activeStoryIndex, currentSlideIndex) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 3) {
                            if (currentSlideIndex > 0) {
                                currentSlideIndex--
                            } else if (activeStoryIndex > 0) {
                                activeStoryIndex--
                            }
                        } else {
                            if (currentSlideIndex < storyItems.size - 1) {
                                currentSlideIndex++
                            } else {
                                onStoryCompleted(activeStory)
                                if (activeStoryIndex < allStories.size - 1) {
                                    activeStoryIndex++
                                } else {
                                    onClose()
                                }
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                translationX = offsetX.value
            }
    ) {
        // Ken-Burns Zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = kenBurnsScale.value
                    scaleY = kenBurnsScale.value
                }
        ) {
            if (currentItem.localUri != null) {
                AsyncImage(
                    model = currentItem.localUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (currentItem.cloudFile != null) {
                TelegramThumbnail(
                    file = currentItem.cloudFile,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Gradient Scrim Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Top Segmented Progress Bar & Close Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                storyItems.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentSlideIndex -> 1f
                        index == currentSlideIndex -> progress.value
                        else -> 0f
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(segmentProgress)
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = CircleShape,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = {
                        onStoryCompleted(activeStory)
                        onClose()
                    })
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Left Title & Date
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 80.dp)
        ) {
            Text(
                text = activeStory.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeStory.dateTag,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // Bottom Action Bar Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Style",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { isMuted = !isMuted }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
