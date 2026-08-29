package com.teledrive.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun FileIcon(
    mimeType: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val icon = when {
        mimeType.startsWith("image/") -> Icons.Outlined.Image
        mimeType.startsWith("video/") -> Icons.Outlined.Videocam
        mimeType.startsWith("audio/") -> Icons.Outlined.MusicNote
        mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
        mimeType in listOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed", "application/x-tar", "application/gzip") -> Icons.Outlined.FolderZip
        mimeType.startsWith("text/") -> Icons.Outlined.Description
        mimeType.startsWith("application/vnd.ms-") || mimeType.startsWith("application/vnd.openxmlformats-") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }

    if (tint != null) {
        Icon(imageVector = icon, contentDescription = "File Type", modifier = modifier, tint = tint)
    } else {
        Icon(imageVector = icon, contentDescription = "File Type", modifier = modifier)
    }
}
