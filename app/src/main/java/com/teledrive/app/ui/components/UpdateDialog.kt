package com.teledrive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teledrive.app.core.ota.OtaUpdateState
import com.teledrive.app.core.ota.UpdateInfo
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun UpdateDialog(
    state: OtaUpdateState,
    currentVersion: String,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    if (state is OtaUpdateState.Idle || state is OtaUpdateState.Checking || state is OtaUpdateState.UpToDate) {
        return
    }

    AlertDialog(
        onDismissRequest = {
            if (state is OtaUpdateState.Downloading) {
                onCancelDownload()
            } else if (state !is OtaUpdateState.UpdateAvailable || !state.info.isMandatory) {
                onDismiss()
            }
        },
        containerColor = Color(0xFF1E1E26),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = when (state) {
                        is OtaUpdateState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        is OtaUpdateState.ReadyToInstall -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (state) {
                                is OtaUpdateState.Error -> Icons.Default.ErrorOutline
                                is OtaUpdateState.ReadyToInstall -> Icons.Default.Verified
                                is OtaUpdateState.Downloading -> Icons.Default.CloudDownload
                                else -> Icons.Default.SystemUpdate
                            },
                            contentDescription = null,
                            tint = when (state) {
                                is OtaUpdateState.Error -> MaterialTheme.colorScheme.error
                                is OtaUpdateState.ReadyToInstall -> Color(0xFF4CAF50)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = when (state) {
                            is OtaUpdateState.UpdateAvailable -> "Update Available"
                            is OtaUpdateState.Downloading -> "Downloading Update"
                            is OtaUpdateState.ReadyToInstall -> "Ready to Install"
                            is OtaUpdateState.Error -> "Update Failed"
                            else -> "App Update"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (state is OtaUpdateState.UpdateAvailable) {
                        Text(
                            text = "v$currentVersion → v${state.info.versionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state is OtaUpdateState.ReadyToInstall) {
                        Text(
                            text = "v${state.info.versionName} downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    is OtaUpdateState.UpdateAvailable -> {
                        if (state.info.fileSize > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF282834),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = "Package Size: ${formatBytes(state.info.fileSize)}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "What's New:",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF252533))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = state.info.changelog.ifBlank { "• Performance optimizations and bug fixes." },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    is OtaUpdateState.Downloading -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF2A2A38)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (state.speedBps > 0) {
                                Text(
                                    text = "${formatBytes(state.speedBps)}/s",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (state.totalBytes > 0) {
                            Text(
                                text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    is OtaUpdateState.ReadyToInstall -> {
                        Text(
                            text = "TeleDrive v${state.info.versionName} has been downloaded and verified. Tap below to launch the installer.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp
                        )
                    }

                    is OtaUpdateState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is OtaUpdateState.UpdateAvailable -> {
                    Button(
                        onClick = { onStartDownload(state.info) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Update Now")
                    }
                }

                is OtaUpdateState.Downloading -> {
                    TextButton(onClick = onCancelDownload) {
                        Text("Cancel", color = Color.Gray)
                    }
                }

                is OtaUpdateState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstall(state.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Install Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                is OtaUpdateState.Error -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Retry")
                    }
                }

                else -> {}
            }
        },
        dismissButton = {
            if (state is OtaUpdateState.UpdateAvailable && !state.info.isMandatory) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = Color.Gray)
                }
            } else if (state is OtaUpdateState.ReadyToInstall) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = Color.Gray)
                }
            } else if (state is OtaUpdateState.Error) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = Color.Gray)
                }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}
