package com.teledrive.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Explorer : Screen("explorer/{path}") {
        fun createRoute(path: String = "/"): String = "explorer/${Uri.encode(path)}"
    }
    data object ImageViewer : Screen("image_viewer/{fileId}") {
        fun createRoute(fileId: Long): String = "image_viewer/$fileId"
    }
    data object VideoPlayer : Screen("video_player/{fileId}") {
        fun createRoute(fileId: Long): String = "video_player/$fileId"
    }
    data object AudioPlayer : Screen("audio_player/{fileId}") {
        fun createRoute(fileId: Long): String = "audio_player/$fileId"
    }
    data object Transfers : Screen("transfers")
    data object Settings : Screen("settings")
}
