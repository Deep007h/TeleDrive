package com.teledrive.app.core.ota

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileSize: Long = 0L,
    val releaseDate: String = "",
    val isMandatory: Boolean = false
)

sealed interface OtaUpdateState {
    data object Idle : OtaUpdateState
    data object Checking : OtaUpdateState
    data class UpdateAvailable(val info: UpdateInfo) : OtaUpdateState
    data class UpToDate(val checkedAt: Long = System.currentTimeMillis()) : OtaUpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Long,
        val info: UpdateInfo
    ) : OtaUpdateState
    data class ReadyToInstall(
        val apkFile: File,
        val info: UpdateInfo
    ) : OtaUpdateState
    data class Error(val message: String) : OtaUpdateState
}
