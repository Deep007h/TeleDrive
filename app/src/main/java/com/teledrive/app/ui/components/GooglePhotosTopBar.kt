package com.teledrive.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teledrive.app.ui.theme.GoogleDarkBackground
import com.teledrive.app.ui.theme.GoogleOnDarkText
import com.teledrive.app.ui.theme.GoogleOnDarkTextSubtle
import com.teledrive.app.ui.theme.GooglePrimaryAccent
import com.teledrive.app.ui.theme.GoogleTertiaryAccent
import java.io.File

@Composable
fun GooglePhotosTopBar(
    statusText: String = "Backup complete",
    photoCount: Int = 0,
    userDisplayName: String = "Cloud",
    profilePhotoPath: String? = null,
    isRefreshing: Boolean = false,
    onAddClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val headerBg = Brush.verticalGradient(
        colors = listOf(
            GoogleDarkBackground,
            GoogleDarkBackground
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left brand block: cloud status + count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = GooglePrimaryAccent.copy(alpha = 0.16f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isRefreshing) GoogleTertiaryAccent else GooglePrimaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isRefreshing) "Backing up…" else statusText,
                        color = GoogleOnDarkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (photoCount > 0) {
                        Text(
                            text = "$photoCount items",
                            color = GoogleOnDarkTextSubtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Right actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = GoogleOnDarkText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = GoogleOnDarkText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                        .padding(4.dp)
                ) {
                    val photoFile = profilePhotoPath?.let { File(it) }
                    val hasPhoto = photoFile != null && photoFile.exists() && photoFile.length() > 0
                    Surface(
                        shape = CircleShape,
                        color = if (hasPhoto) Color.Transparent else GooglePrimaryAccent,
                        border = BorderStroke(1.5.dp, GoogleOnDarkText.copy(alpha = 0.4f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (hasPhoto) {
                                AsyncImage(
                                    model = photoFile,
                                    contentDescription = userDisplayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = userDisplayName.take(1).uppercase().ifEmpty { "U" },
                                    color = Color(0xFF003063),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
