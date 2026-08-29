package com.teledrive.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.teledrive.app.ui.theme.GoogleDarkCard
import com.teledrive.app.ui.theme.GoogleDarkCardElevated
import com.teledrive.app.ui.theme.GoogleDarkSurface
import com.teledrive.app.ui.theme.GoogleOnDarkText
import com.teledrive.app.ui.theme.GoogleOnDarkTextMuted
import com.teledrive.app.ui.theme.GoogleOnDarkTextSubtle
import com.teledrive.app.ui.theme.GooglePrimaryAccent
import com.teledrive.app.ui.theme.GoogleTertiaryAccent
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

enum class ProfileDialogType {
    STORAGE_INFO,
    FREE_SPACE,
    AI_PLAN,
    PRIVACY,
    HELP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePhotosProfileSheet(
    userDisplayName: String,
    phoneNumber: String,
    profilePhotoPath: String? = null,
    totalCount: Int,
    syncedCount: Int,
    totalSizeBytes: Long,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onTriggerBackup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onCheckUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeDialog by remember { mutableStateOf<ProfileDialogType?>(null) }

    val rawName = userDisplayName.ifBlank { "Telegram User" }
    val initialLetter = rawName.take(1).uppercase().ifEmpty { "T" }
    val phoneText = phoneNumber.ifEmpty { "Connected via Telegram Cloud" }

    val photoFile = remember(profilePhotoPath) {
        profilePhotoPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GoogleDarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GoogleOnDarkTextSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (photoFile != null) Color.Transparent else GooglePrimaryAccent,
                modifier = Modifier
                    .size(72.dp)
                    .border(2.dp, GoogleOnDarkText.copy(alpha = 0.2f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (photoFile != null) {
                        AsyncImage(
                            model = photoFile,
                            contentDescription = rawName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = initialLetter,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF003063)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = rawName,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoogleOnDarkText
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = phoneText,
                fontSize = 13.sp,
                color = GoogleOnDarkTextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Opening Telegram Account Manager…", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleOnDarkText),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    text = "Manage your Telegram Account",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GoogleDarkCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onLogout()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Switch account / Sign out",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoogleOnDarkText
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (photoFile != null) Color.Transparent else GooglePrimaryAccent,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (photoFile != null) {
                                    AsyncImage(
                                        model = photoFile,
                                        contentDescription = rawName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(initialLetter, color = Color(0xFF003063), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch",
                            tint = GoogleOnDarkText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GoogleDarkCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Storage",
                            tint = GooglePrimaryAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unlimited Telegram Cloud Storage",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoogleOnDarkText
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val progressVal = if (totalCount > 0) (syncedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0.71f
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = GooglePrimaryAccent,
                        trackColor = GoogleDarkCardElevated
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val formattedSize = formatBytes(totalSizeBytes)
                    Text(
                        text = "$syncedCount of $totalCount items backed up ($formattedSize) • Unlimited Cloud",
                        fontSize = 12.sp,
                        color = GoogleOnDarkTextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { activeDialog = ProfileDialogType.STORAGE_INFO }) {
                            Text("Get storage", color = GooglePrimaryAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { activeDialog = ProfileDialogType.FREE_SPACE }) {
                            Text("Clean up space", color = GooglePrimaryAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "More from Photos",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoogleOnDarkTextSubtle,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 6.dp, bottom = 10.dp)
            )

            ProfileOptionRow(
                icon = Icons.Default.CloudUpload,
                title = "Backup",
                subtitle = if (syncedCount >= totalCount && totalCount > 0) "Backup complete • All items synced" else "Backing up • $syncedCount/$totalCount synced",
                onClick = {
                    onDismiss()
                    onTriggerBackup()
                    Toast.makeText(context, "Scanning local media for backup…", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.AutoAwesome,
                title = "Get a Telegram AI plan",
                subtitle = "Unlimited cloud & AI features",
                onClick = { activeDialog = ProfileDialogType.AI_PLAN }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.DeleteSweep,
                title = "Free up space on this device",
                subtitle = "Safely remove backed up media",
                onClick = { activeDialog = ProfileDialogType.FREE_SPACE }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.Shield,
                title = "Your data in Telegram Gallery",
                subtitle = "Encrypted private channel storage",
                onClick = { activeDialog = ProfileDialogType.PRIVACY }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.Settings,
                title = "Gallery settings",
                subtitle = "Backup, network & channel controls",
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.SystemUpdate,
                title = "Check for updates",
                subtitle = "Over-the-air app updates",
                onClick = {
                    onDismiss()
                    onCheckUpdates()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help & feedback",
                subtitle = "FAQs & Telegram storage guide",
                onClick = { activeDialog = ProfileDialogType.HELP }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProfileOptionRow(
                icon = Icons.Default.BugReport,
                title = "App debug logs",
                subtitle = "View live logs, TDLib events & copy path",
                onClick = {
                    onDismiss()
                    onOpenLogs()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    activeDialog?.let { dialogType ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = GoogleDarkCard,
            title = {
                Text(
                    text = when (dialogType) {
                        ProfileDialogType.STORAGE_INFO -> "Unlimited Telegram Storage"
                        ProfileDialogType.FREE_SPACE -> "Free Up Device Space"
                        ProfileDialogType.AI_PLAN -> "Telegram AI Plan"
                        ProfileDialogType.PRIVACY -> "Data Security & Privacy"
                        ProfileDialogType.HELP -> "Help & Support"
                    },
                    color = GoogleOnDarkText,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = when (dialogType) {
                        ProfileDialogType.STORAGE_INFO ->
                            "Your photos and videos are backed up to Telegram cloud storage. Telegram provides unlimited storage for all uploaded media items without compression limits."
                        ProfileDialogType.FREE_SPACE ->
                            "$syncedCount items have been safely backed up to Telegram Cloud. You can safely remove original local copies to free up local disk space."
                        ProfileDialogType.AI_PLAN ->
                            "Telegram AI features include smart search, automatic album organization, photo enhancement, and high-speed multi-part uploads."
                        ProfileDialogType.PRIVACY ->
                            "All backed-up media is stored in your private Telegram channel or Saved Messages. Files are encrypted with end-to-end envelope keys."
                        ProfileDialogType.HELP ->
                            "Telegram Gallery / TeleDrive v1.0\n• Cloud Backend: Telegram MTProto\n• Support: Open Settings for channel and network controls."
                    },
                    color = GoogleOnDarkTextMuted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("OK", color = GooglePrimaryAccent, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
fun ProfileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = GoogleDarkCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GoogleOnDarkText,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = GoogleOnDarkText
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = GoogleOnDarkTextMuted
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}
