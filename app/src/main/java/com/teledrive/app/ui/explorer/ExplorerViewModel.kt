package com.teledrive.app.ui.explorer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teledrive.app.TeleDriveApplication
import com.teledrive.app.data.db.entity.FileEntity
import com.teledrive.app.data.db.entity.FolderEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode { GRID, LIST }
enum class SortBy { NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC }
enum class FileTypeFilter { ALL, IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES }
enum class StorageSource { TELEDRIVE_CHANNEL, SAVED_MESSAGES }

data class PathSegment(val path: String, val name: String)

data class ExplorerUiState(
    val currentPath: String = "/",
    val pathSegments: List<PathSegment> = listOf(PathSegment("/", "Home")),
    val folders: List<FolderEntity> = emptyList(),
    val files: List<FileEntity> = emptyList(),
    val allMedia: List<FileEntity> = emptyList(),
    val allCloudFiles: List<FileEntity> = emptyList(),
    val deviceAlbums: List<com.teledrive.app.data.repository.DeviceAlbum> = emptyList(),
    val unifiedMedia: List<com.teledrive.app.data.repository.UnifiedMediaItem> = emptyList(),
    val peopleClusters: List<com.teledrive.app.data.repository.PersonCluster> = emptyList(),
    val isScanningPeople: Boolean = false,
    val isLoadingAlbums: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortBy: SortBy = SortBy.NAME_ASC,
    val selectedFiles: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val searchQuery: String = "",
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.ALL,
    val storageChannelId: Long = 0L,
    val savedMessagesChatId: Long = 0L,
    val storageSource: StorageSource = StorageSource.SAVED_MESSAGES,
    val activeChatId: Long = 0L
)

class ExplorerViewModel : ViewModel() {

    private val localRepository = TeleDriveApplication.instance.localRepository
    private val channelRepository = TeleDriveApplication.instance.channelRepository
    private val transferManager = TeleDriveApplication.instance.transferManager
    private val preferences = TeleDriveApplication.instance.preferences

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var loadFolderJob: Job? = null
    private var mediaFlowJob: Job? = null
    private var allFilesFlowJob: Job? = null

    init {
        loadDeviceAlbums()

        viewModelScope.launch {
            val savedViewMode = preferences.viewMode.first()
            val mode = if (savedViewMode == "list") ViewMode.LIST else ViewMode.GRID
            _uiState.update { it.copy(viewMode = mode) }

            // 1. Immediately resolve Saved Messages and start listening + syncing in background
            launch {
                try {
                    val savedId = channelRepository.getSavedMessagesChatId()
                    if (savedId != 0L) {
                        _uiState.update {
                            it.copy(
                                savedMessagesChatId = savedId,
                                storageSource = StorageSource.SAVED_MESSAGES,
                                activeChatId = savedId
                            )
                        }
                        listenToMediaAndFiles(savedId)
                        loadFolder("/", savedId)
                        localRepository.syncFromTelegram(savedId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Concurrently resolve / prepare Channel in background
            launch {
                try {
                    val channelId = channelRepository.getOrCreateStorageChannel()
                    if (channelId != 0L) {
                        _uiState.update { it.copy(storageChannelId = channelId) }
                        localRepository.syncFromTelegram(channelId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        viewModelScope.launch {
            TeleDriveApplication.instance.peopleRepository.peopleClusters.collect { clusters ->
                _uiState.update { it.copy(peopleClusters = clusters) }
            }
        }

        viewModelScope.launch {
            TeleDriveApplication.instance.peopleRepository.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanningPeople = scanning) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun listenToMediaAndFiles(chatId: Long) {
        // Cancel any existing jobs to prevent leaks
        mediaFlowJob?.cancel()
        allFilesFlowJob?.cancel()

        mediaFlowJob = viewModelScope.launch {
            localRepository.getAllMedia(chatId)
                .flowOn(kotlinx.coroutines.Dispatchers.IO)
                .debounce(100L)
                .collect { mediaList ->
                    _uiState.update { it.copy(allMedia = mediaList) }
                    refreshUnifiedMedia()
                    // Trigger face clustering on cloud media
                    TeleDriveApplication.instance.peopleRepository.scanCloudMedia(mediaList)
                }
        }

        allFilesFlowJob = viewModelScope.launch {
            localRepository.getAllFiles(chatId)
                .flowOn(kotlinx.coroutines.Dispatchers.IO)
                .debounce(100L)
                .collect { fileList ->
                    _uiState.update { it.copy(allCloudFiles = fileList) }
                    refreshUnifiedMedia()
                }
        }
    }

    fun refreshUnifiedMedia() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val cloudMedia = _uiState.value.allMedia
                val unified = TeleDriveApplication.instance.deviceMediaRepository.buildUnifiedMedia(cloudMedia)
                _uiState.update { it.copy(unifiedMedia = unified) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renamePerson(personId: String, newName: String) {
        viewModelScope.launch {
            TeleDriveApplication.instance.peopleRepository.renamePerson(personId, newName)
        }
    }

    fun rescanPeople() {
        viewModelScope.launch {
            TeleDriveApplication.instance.peopleRepository.rescanAll(_uiState.value.allMedia)
        }
    }

    fun syncCurrentSource() {
        viewModelScope.launch {
            val chatId = _uiState.value.activeChatId
            if (chatId != 0L) {
                _uiState.update { it.copy(isRefreshing = true) }
                localRepository.syncFromTelegram(chatId)
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun selectStorageSource(source: StorageSource) {
        viewModelScope.launch {
            val targetChatId = if (source == StorageSource.SAVED_MESSAGES) {
                if (_uiState.value.savedMessagesChatId != 0L) {
                    _uiState.value.savedMessagesChatId
                } else {
                    val id = channelRepository.getSavedMessagesChatId()
                    _uiState.update { it.copy(savedMessagesChatId = id) }
                    id
                }
            } else {
                _uiState.value.storageChannelId
            }

            _uiState.update {
                it.copy(
                    storageSource = source,
                    activeChatId = targetChatId,
                    isLoading = true
                )
            }

            listenToMediaAndFiles(targetChatId)
            loadFolder("/", targetChatId)
            if (targetChatId != 0L) {
                localRepository.syncFromTelegram(targetChatId)
            }
        }
    }

    fun loadFolder(path: String, chatId: Long = _uiState.value.activeChatId) {
        val normalizedPath = if (path.isEmpty()) "/" else path
        val segments = buildSegments(normalizedPath)

        val isPathChange = normalizedPath != _uiState.value.currentPath
        val isChatChange = chatId != _uiState.value.activeChatId

        _uiState.update {
            it.copy(
                currentPath = normalizedPath,
                pathSegments = segments,
                isLoading = isPathChange || isChatChange,
                error = null,
                selectedFiles = if (isPathChange) emptySet() else it.selectedFiles,
                isSelectionMode = if (isPathChange) false else it.isSelectionMode
            )
        }

        // Cancel previous load job to prevent duplicate subscriptions
        loadFolderJob?.cancel()
        loadFolderJob = viewModelScope.launch {
            val filesFlow = localRepository.getFilesInFolder(normalizedPath, chatId)
            val foldersFlow = localRepository.getFolders(normalizedPath, chatId)

            // Use a single-shot collect to avoid multiple subscriptions
            val combinedFlow = combine(filesFlow, foldersFlow) { files, folders ->
                Pair(files, folders)
            }.distinctUntilChanged { prev, next ->
                prev.first == next.first && prev.second == next.second
            }

            combinedFlow.collect { (files, folders) ->
                _uiState.update { state ->
                    val filteredFiles = filterFiles(files, state.fileTypeFilter, state.searchQuery)
                    val sortedFiles = sortFiles(filteredFiles, state.sortBy)
                    val filteredFolders = if (state.searchQuery.isNotEmpty()) {
                        folders.filter { it.folderName.contains(state.searchQuery, ignoreCase = true) }
                    } else folders

                    state.copy(
                        files = sortedFiles,
                        folders = filteredFolders,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun navigateToFolder(path: String) {
        loadFolder(path, _uiState.value.activeChatId)
    }

    fun navigateUp(): Boolean {
        val current = _uiState.value.currentPath
        if (current == "/" || current.isEmpty()) return false
        val parent = current.substringBeforeLast('/')
        val target = if (parent.isEmpty()) "/" else parent
        loadFolder(target, _uiState.value.activeChatId)
        return true
    }

    fun refresh() {
        val chatId = _uiState.value.activeChatId
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            if (chatId != 0L) {
                localRepository.syncFromTelegram(chatId)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggleViewMode() {
        val newMode = if (_uiState.value.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        _uiState.update { it.copy(viewMode = newMode) }
        viewModelScope.launch {
            preferences.setViewMode(if (newMode == ViewMode.LIST) "list" else "grid")
        }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update { state ->
            state.copy(
                sortBy = sortBy,
                files = sortFiles(state.files, sortBy)
            )
        }
    }

    fun setFileTypeFilter(filter: FileTypeFilter) {
        _uiState.update { it.copy(fileTypeFilter = filter) }
        reapplyFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        reapplyFilters()
    }

    private fun reapplyFilters() {
        val state = _uiState.value
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            localRepository.getFilesInFolder(state.currentPath, state.activeChatId).first()
                .let { files ->
                    val filtered = filterFiles(files, state.fileTypeFilter, state.searchQuery)
                    val sorted = sortFiles(filtered, state.sortBy)
                    val folders = localRepository.getFolders(state.currentPath, state.activeChatId).first()
                    val filteredFolders = if (state.searchQuery.isNotEmpty()) {
                        folders.filter { it.folderName.contains(state.searchQuery, ignoreCase = true) }
                    } else folders
                    _uiState.update {
                        it.copy(
                            files = sorted,
                            folders = filteredFolders,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun toggleFileSelection(fileId: Long) {
        _uiState.update { state ->
            val updated = if (state.selectedFiles.contains(fileId)) {
                state.selectedFiles - fileId
            } else {
                state.selectedFiles + fileId
            }
            state.copy(
                selectedFiles = updated,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun selectAllFiles() {
        _uiState.update { state ->
            val allIds = state.files.map { it.fileId }.toSet()
            state.copy(
                selectedFiles = allIds,
                isSelectionMode = allIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedFiles = emptySet(), isSelectionMode = false)
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        val currentPath = _uiState.value.currentPath
        val distinctUris = uris.distinct()
        viewModelScope.launch {
            var targetChatId = _uiState.value.activeChatId
            if (targetChatId == 0L) {
                targetChatId = _uiState.value.savedMessagesChatId
            }
            if (targetChatId == 0L) {
                targetChatId = channelRepository.getSavedMessagesChatId()
            }
            if (targetChatId != 0L) {
                for (uri in distinctUris) {
                    transferManager.enqueueUpload(uri, currentPath, targetChatId)
                }
            }
        }
    }

    fun downloadFile(file: FileEntity) {
        viewModelScope.launch {
            transferManager.enqueueDownload(
                virtualPath = file.virtualPath,
                fileName = file.fileName,
                fileSize = file.fileSize,
                chatId = file.telegramChatId,
                messageId = file.telegramMessageId
            )
        }
    }

    fun downloadSelectedFiles() {
        val selectedIds = _uiState.value.selectedFiles
        val selectedFileList = _uiState.value.files.filter { selectedIds.contains(it.fileId) }
        viewModelScope.launch {
            for (file in selectedFileList) {
                transferManager.enqueueDownload(
                    virtualPath = file.virtualPath,
                    fileName = file.fileName,
                    fileSize = file.fileSize,
                    chatId = file.telegramChatId,
                    messageId = file.telegramMessageId
                )
            }
            clearSelection()
        }
    }

    fun deleteSelectedFiles() {
        val selectedIds = _uiState.value.selectedFiles
        val selectedFileList = _uiState.value.files.filter { selectedIds.contains(it.fileId) }
        viewModelScope.launch {
            for (file in selectedFileList) {
                localRepository.deleteFile(file)
            }
            clearSelection()
        }
    }

    fun deleteFile(file: FileEntity) {
        viewModelScope.launch {
            localRepository.deleteFile(file)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            localRepository.deleteFolder(folder)
            loadFolder(_uiState.value.currentPath, _uiState.value.activeChatId)
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        val currentPath = _uiState.value.currentPath
        val chatId = _uiState.value.activeChatId
        viewModelScope.launch {
            localRepository.createFolder(name.trim(), currentPath, chatId)
            loadFolder(currentPath, chatId)
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            localRepository.renameFile(fileId, newName.trim())
            loadFolder(_uiState.value.currentPath, _uiState.value.activeChatId)
        }
    }

    private fun buildSegments(path: String): List<PathSegment> {
        if (path == "/" || path.isEmpty()) {
            return listOf(PathSegment("/", "Home"))
        }
        val segments = mutableListOf(PathSegment("/", "Home"))
        val parts = path.trim('/').split('/')
        var accumulated = ""
        for (part in parts) {
            accumulated += "/$part"
            segments.add(PathSegment(accumulated, part))
        }
        return segments
    }

    private fun filterFiles(files: List<FileEntity>, filter: FileTypeFilter, query: String): List<FileEntity> {
        var result = files
        if (query.isNotEmpty()) {
            result = result.filter { it.fileName.contains(query, ignoreCase = true) }
        }
        return when (filter) {
            FileTypeFilter.ALL -> result
            FileTypeFilter.IMAGES -> result.filter { it.mimeType.startsWith("image/") }
            FileTypeFilter.VIDEOS -> result.filter { it.mimeType.startsWith("video/") }
            FileTypeFilter.AUDIO -> result.filter { it.mimeType.startsWith("audio/") }
            FileTypeFilter.DOCUMENTS -> result.filter {
                it.mimeType.startsWith("application/") || it.mimeType.startsWith("text/")
            }
            FileTypeFilter.ARCHIVES -> result.filter {
                it.mimeType.contains("zip") || it.mimeType.contains("tar") ||
                it.mimeType.contains("rar") || it.mimeType.contains("7z") ||
                it.mimeType.contains("compressed")
            }
        }
    }

    private fun sortFiles(files: List<FileEntity>, sortBy: SortBy): List<FileEntity> {
        return when (sortBy) {
            SortBy.NAME_ASC -> files.sortedBy { it.fileName.lowercase() }
            SortBy.NAME_DESC -> files.sortedByDescending { it.fileName.lowercase() }
            SortBy.DATE_ASC -> files.sortedBy { it.uploadTimestamp }
            SortBy.DATE_DESC -> files.sortedByDescending { it.uploadTimestamp }
            SortBy.SIZE_ASC -> files.sortedBy { it.fileSize }
            SortBy.SIZE_DESC -> files.sortedByDescending { it.fileSize }
        }
    }

    fun loadDeviceAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlbums = true) }
            try {
                val albums = TeleDriveApplication.instance.deviceMediaRepository.getDeviceAlbums()
                _uiState.update { it.copy(deviceAlbums = albums, isLoadingAlbums = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingAlbums = false) }
            }
        }
    }

    fun uploadLocalMediaItems(items: List<com.teledrive.app.data.repository.LocalMediaItem>, targetVirtualPath: String = "/") {
        viewModelScope.launch {
            var targetChatId = _uiState.value.activeChatId
            if (targetChatId == 0L) {
                targetChatId = _uiState.value.savedMessagesChatId
            }
            if (targetChatId == 0L) {
                targetChatId = channelRepository.getSavedMessagesChatId()
            }
            if (targetChatId != 0L) {
                for (item in items) {
                    transferManager.enqueueUpload(item.contentUri, targetVirtualPath, targetChatId)
                }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                TeleDriveApplication.instance.tdLibManager.logout()
            } catch (e: Exception) {}
            onLoggedOut()
        }
    }
}
