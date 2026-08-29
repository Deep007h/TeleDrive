package com.teledrive.app.telegram

import android.content.Context
import android.os.Build
import com.teledrive.app.core.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdLibManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<TdLibAuthState>(TdLibAuthState.Initial)
    val authState: StateFlow<TdLibAuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow<TdLibConnectionState>(TdLibConnectionState.WaitingForNetwork)
    val connectionState: StateFlow<TdLibConnectionState> = _connectionState.asStateFlow()

    private val _fileUpdates = MutableSharedFlow<TdFileUpdate>(extraBufferCapacity = 100)
    val fileUpdates: SharedFlow<TdFileUpdate> = _fileUpdates.asSharedFlow()

    private val _newMessages = MutableSharedFlow<TdMessageInfo>(extraBufferCapacity = 100)
    val newMessages: SharedFlow<TdMessageInfo> = _newMessages.asSharedFlow()

    private val _deletedMessages = MutableSharedFlow<Pair<Long, LongArray>>(extraBufferCapacity = 100)
    val deletedMessages: SharedFlow<Pair<Long, LongArray>> = _deletedMessages.asSharedFlow()

    private val _recaptchaRequests = MutableSharedFlow<RecaptchaRequest>(extraBufferCapacity = 10)
    val recaptchaRequests: SharedFlow<RecaptchaRequest> = _recaptchaRequests.asSharedFlow()

    private var client: Client? = null
    private var appContext: Context? = null
    private var activeApiId: Int = Constants.API_ID
    private var activeApiHash: String = Constants.API_HASH

    private inner class UpdateHandler : Client.ResultHandler {
        override fun onResult(obj: TdApi.Object?) {
            when (obj) {
                is TdApi.UpdateAuthorizationState -> {
                    handleAuthState(obj.authorizationState)
                }
                is TdApi.UpdateConnectionState -> {
                    _connectionState.value = mapConnectionState(obj.state)
                }
                is TdApi.UpdateFile -> {
                    handleFileUpdate(obj.file)
                }
                is TdApi.UpdateNewMessage -> {
                    val msgInfo = parseMessageInfo(obj.message)
                    if (msgInfo != null) {
                        com.teledrive.app.core.AppLogger.logTdLib("NewMessage", "Received real-time message: file=${msgInfo.documentFileName}, mime=${msgInfo.documentMimeType}")
                        _newMessages.tryEmit(msgInfo)
                    }
                }
                is TdApi.UpdateDeleteMessages -> {
                    com.teledrive.app.core.AppLogger.logTdLib("DeleteMessages", "Received real-time delete event: chatId=${obj.chatId}, count=${obj.messageIds.size}")
                    _deletedMessages.tryEmit(Pair(obj.chatId, obj.messageIds))
                }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        val newState = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                applyParameters()
                TdLibAuthState.WaitTdlibParameters
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> TdLibAuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> {
                TdLibAuthState.WaitCode()
            }
            is TdApi.AuthorizationStateWaitPassword -> TdLibAuthState.WaitPassword()
            is TdApi.AuthorizationStateReady -> {
                client?.send(TdApi.LoadChats(null, 20)) { result ->
                    com.teledrive.app.core.AppLogger.logTdLib("LoadChats", "Initial chat list loaded: ${result::class.simpleName}")
                }
                TdLibAuthState.Ready
            }
            is TdApi.AuthorizationStateLoggingOut -> TdLibAuthState.LoggingOut
            is TdApi.AuthorizationStateClosing -> TdLibAuthState.Closing
            is TdApi.AuthorizationStateClosed -> TdLibAuthState.Closed
            else -> TdLibAuthState.Initial
        }
        com.teledrive.app.core.AppLogger.logTdLib("AuthState", "State changed to: ${newState::class.simpleName}")
        _authState.value = newState
    }

    private fun applyParameters() {
        val context = appContext ?: return
        val filesDir = context.filesDir
        val tdlibDir = File(filesDir, "tdlib").apply { mkdirs() }
        val databaseDir = File(tdlibDir, "database").apply { mkdirs() }
        val filesDirTd = File(tdlibDir, "files").apply { mkdirs() }

        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = databaseDir.absolutePath
            filesDirectory = filesDirTd.absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = activeApiId
            apiHash = activeApiHash
            systemLanguageCode = "en"
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = "10.14.5"
        }

        com.teledrive.app.core.AppLogger.logTdLib("Init", "Setting TDLib parameters for device: ${parameters.deviceModel}")
        client?.send(parameters) { result ->
            if (result is TdApi.Error) {
                com.teledrive.app.core.AppLogger.e("TDLib", "SetTdlibParameters error: ${result.message}")
                _authState.value = TdLibAuthState.Error(result.message)
            }
        }
    }

    private fun mapConnectionState(state: TdApi.ConnectionState): TdLibConnectionState {
        return when (state) {
            is TdApi.ConnectionStateConnectingToProxy -> TdLibConnectionState.ConnectingToProxy
            is TdApi.ConnectionStateConnecting -> TdLibConnectionState.Connecting
            is TdApi.ConnectionStateUpdating -> TdLibConnectionState.Updating
            is TdApi.ConnectionStateReady -> TdLibConnectionState.Ready
            else -> TdLibConnectionState.WaitingForNetwork
        }
    }

    private fun handleFileUpdate(file: TdApi.File) {
        val update = TdFileUpdate(
            fileId = file.id,
            size = file.size,
            expectedSize = file.expectedSize,
            downloadedSize = file.local.downloadedSize,
            uploadedSize = file.remote.uploadedSize,
            isDownloadingCompleted = file.local.isDownloadingCompleted,
            isUploadingCompleted = file.remote.isUploadingCompleted,
            localPath = file.local.path
        )
        if (file.local.isDownloadingCompleted) {
            com.teledrive.app.core.AppLogger.logTransfer("FileCompleted", file.id, "", "Downloaded ${file.local.downloadedSize} bytes to ${file.local.path}")
        }
        _fileUpdates.tryEmit(update)
    }

    suspend fun sendRequest(function: TdApi.Function<*>): TdApi.Object {
        return withTimeout(25_000) {
            suspendCancellableCoroutine { cont ->
                client?.send(function) { result ->
                    if (result is TdApi.Error) {
                        com.teledrive.app.core.AppLogger.e("TDLib", "Error on ${function::class.simpleName}: code=${result.code}, msg=${result.message}")
                        cont.resumeWithException(TdLibException(result.code, result.message))
                    } else {
                        cont.resume(result)
                    }
                } ?: cont.resumeWithException(IllegalStateException("TDLib client is not initialized"))
            }
        }
    }

    fun initialize(context: Context) {
        if (client != null) return
        appContext = context.applicationContext

        try {
            client = Client.create(UpdateHandler(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restartClient(apiId: Int, apiHash: String) {
        if (apiId > 0 && apiHash.isNotBlank()) {
            activeApiId = apiId
            activeApiHash = apiHash
        }

        try {
            client?.send(TdApi.Close(), null)
        } catch (ignored: Exception) {}
        client = null

        val context = appContext ?: return
        val tdlibDir = File(context.filesDir, "tdlib")
        if (tdlibDir.exists()) {
            tdlibDir.deleteRecursively()
        }

        client = Client.create(UpdateHandler(), null, null)
        applyParameters()

        try {
            withTimeout(6000) {
                authState.filter { it is TdLibAuthState.WaitPhoneNumber || it is TdLibAuthState.Ready }.first()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun setPhoneNumber(phone: String) {
        if (_authState.value !is TdLibAuthState.WaitPhoneNumber && _authState.value !is TdLibAuthState.Ready) {
            applyParameters()
            try {
                withTimeout(8000) {
                    authState.filter { it is TdLibAuthState.WaitPhoneNumber || it is TdLibAuthState.Ready }.first()
                }
            } catch (ignored: Exception) {}
        }
        sendRequest(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }

    suspend fun setApplicationVerificationToken(verificationId: Long, token: String) {
        sendRequest(TdApi.SetApplicationVerificationToken(verificationId, token))
    }

    suspend fun submitAuthCode(code: String) {
        sendRequest(TdApi.CheckAuthenticationCode(code))
    }

    suspend fun submit2FAPassword(password: String) {
        sendRequest(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun logout() {
        sendRequest(TdApi.LogOut())
    }

    suspend fun createPrivateChannel(title: String, description: String): TdChatInfo {
        val chat = sendRequest(TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false)) as TdApi.Chat
        return TdChatInfo(
            chatId = chat.id,
            title = chat.title,
            type = "channel"
        )
    }

    suspend fun getChat(chatId: Long): TdChatInfo {
        val chat = sendRequest(TdApi.GetChat(chatId)) as TdApi.Chat
        val type = when (chat.type) {
            is TdApi.ChatTypePrivate -> "private"
            is TdApi.ChatTypeSupergroup -> if ((chat.type as TdApi.ChatTypeSupergroup).isChannel) "channel" else "supergroup"
            else -> "group"
        }
        return TdChatInfo(
            chatId = chat.id,
            title = chat.title,
            type = type
        )
    }

    suspend fun getMessage(chatId: Long, messageId: Long): TdMessageInfo? {
        return try {
            val msg = sendRequest(TdApi.GetMessage(chatId, messageId)) as TdApi.Message
            parseMessageInfo(msg)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int): List<TdMessageInfo> {
        val messages = sendRequest(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) as TdApi.Messages
        return messages.messages.mapNotNull { message ->
            parseMessageInfo(message)
        }
    }

    data class ChatHistoryBatch(
        val parsedItems: List<TdMessageInfo>,
        val lastRawMessageId: Long,
        val totalRawCount: Int
    )

    suspend fun getChatHistoryFull(chatId: Long, fromMessageId: Long, limit: Int = 100): ChatHistoryBatch {
        val messages = sendRequest(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) as TdApi.Messages
        val rawList = messages.messages.toList()
        val parsed = rawList.mapNotNull { parseMessageInfo(it) }
        val lastId = rawList.lastOrNull()?.id ?: 0L
        return ChatHistoryBatch(
            parsedItems = parsed,
            lastRawMessageId = lastId,
            totalRawCount = rawList.size
        )
    }

    suspend fun sendFile(chatId: Long, filePath: String, caption: String): TdMessageInfo {
        val inputFile = TdApi.InputFileLocal(filePath)
        val inputDoc = TdApi.InputDocument(inputFile, null, false)
        val inputMessageContent = TdApi.InputMessageDocument(
            inputDoc,
            TdApi.FormattedText(caption, emptyArray())
        )
        val message = sendRequest(TdApi.SendMessage(chatId, null, null, null, null, inputMessageContent)) as TdApi.Message
        return parseMessageInfo(message) ?: throw Exception("Failed to parse sent message info")
    }

    suspend fun getFile(fileId: Int): TdApi.File {
        return sendRequest(TdApi.GetFile(fileId)) as TdApi.File
    }

    suspend fun startDownload(fileId: Int, priority: Int = 1): TdApi.File {
        return sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false)) as TdApi.File
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): String {
        try {
            val file = getFile(fileId)
            if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty() && File(file.local.path).exists()) {
                return file.local.path
            }
        } catch (ignored: Exception) {}

        try {
            sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return try {
            withTimeout(30_000) {
                val update = fileUpdates.first { it.fileId == fileId && it.isDownloadingCompleted && it.localPath.isNotEmpty() }
                update.localPath
            }
        } catch (e: Exception) {
            try {
                val file = getFile(fileId)
                if (file.local.isDownloadingCompleted) file.local.path else ""
            } catch (ex: Exception) {
                ""
            }
        }
    }

    suspend fun cancelDownload(fileId: Int) {
        sendRequest(TdApi.CancelDownloadFile(fileId, false))
    }

    suspend fun deleteMessages(chatId: Long, messageIds: LongArray) {
        sendRequest(TdApi.DeleteMessages(chatId, messageIds, true))
    }

    suspend fun editMessageCaption(chatId: Long, messageId: Long, newCaption: String) {
        sendRequest(
            TdApi.EditMessageCaption(
                chatId,
                messageId,
                null,
                TdApi.FormattedText(newCaption, emptyArray()),
                false
            )
        )
    }

    suspend fun getMe(): Pair<String, String?> {
        val user = sendRequest(TdApi.GetMe()) as TdApi.User
        return Pair(user.phoneNumber, user.usernames?.activeUsernames?.firstOrNull())
    }

    suspend fun getMeUser(): TdApi.User {
        return sendRequest(TdApi.GetMe()) as TdApi.User
    }

    /**
     * Returns the smallest available profile photo file id for the currently
     * signed-in user, or null if the user has no profile photo set.
     */
    suspend fun getMyProfilePhotoFileId(): Int? {
        val me = getMeUser()
        val photo = me.profilePhoto ?: return null
        val file = photo.small ?: photo.big ?: return null
        return file.id
    }

    /**
     * Downloads (or returns the cached local path of) the profile photo for the
     * given Telegram file id. Cached under the app's cache dir by file id.
     */
    suspend fun downloadProfilePhoto(fileId: Int, context: Context): File? {
        val cacheFile = File(context.cacheDir, "profile_photo_$fileId.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }
        return try {
            val path = downloadFile(fileId, 1)
            if (path.isBlank() || !File(path).exists()) return null
            val src = File(path)
            src.copyTo(cacheFile, overwrite = true)
            cacheFile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSavedMessagesChatId(): Long {
        val me = getMeUser()
        val privateChat = sendRequest(TdApi.CreatePrivateChat(me.id, false)) as TdApi.Chat
        return privateChat.id
    }

    suspend fun getMessageInfo(chatId: Long, messageId: Long): TdMessageInfo? {
        return try {
            val message = sendRequest(TdApi.GetMessage(chatId, messageId)) as TdApi.Message
            parseMessageInfo(message)
        } catch (e: Exception) {
            com.teledrive.app.core.AppLogger.w("TDLib", "getMessage failed for chatId=$chatId, msgId=$messageId: ${e.message}")
            null
        }
    }

    suspend fun rehydrateAndDownloadFile(
        chatId: Long,
        messageId: Long,
        preferredFileId: Int,
        priority: Int = 32
    ): String {
        if (preferredFileId != 0) {
            try {
                val tdFile = getFile(preferredFileId)
                if (tdFile.local.isDownloadingCompleted && tdFile.local.path.isNotEmpty() && File(tdFile.local.path).exists()) {
                    return tdFile.local.path
                }
            } catch (ignored: Exception) {}

            try {
                val path = downloadFile(preferredFileId, priority)
                if (path.isNotEmpty() && File(path).exists()) {
                    return path
                }
            } catch (ignored: Exception) {}
        }

        // Rehydrate using GetMessage if preferredFileId was stale or 0
        if (chatId != 0L && messageId != 0L) {
            val info = getMessageInfo(chatId, messageId)
            if (info != null && info.documentFileId != 0) {
                try {
                    val freshPath = downloadFile(info.documentFileId, priority)
                    if (freshPath.isNotEmpty() && File(freshPath).exists()) {
                        return freshPath
                    }
                } catch (ignored: Exception) {}
            }
        }
        return ""
    }

    suspend fun openChat(chatId: Long) {
        try {
            sendRequest(TdApi.OpenChat(chatId))
            com.teledrive.app.core.AppLogger.logTdLib("OpenChat", "Opened chat stream for chatId=$chatId")
        } catch (e: Exception) {
            com.teledrive.app.core.AppLogger.w("TDLib", "Failed to openChat $chatId: ${e.message}")
        }
    }

    suspend fun closeChat(chatId: Long) {
        try {
            sendRequest(TdApi.CloseChat(chatId))
        } catch (ignored: Exception) {}
    }

    private fun parseMessageInfo(message: TdApi.Message): TdMessageInfo? {
        when (val content = message.content) {
            is TdApi.MessageDocument -> {
                val doc = content.document
                val thumbId = doc.thumbnail?.file?.id
                return TdMessageInfo(
                    messageId = message.id,
                    chatId = message.chatId,
                    date = message.date,
                    caption = content.caption.text,
                    documentFileId = doc.document.id,
                    documentFileName = doc.fileName,
                    documentSize = doc.document.size,
                    documentMimeType = doc.mimeType,
                    thumbnailFileId = thumbId
                )
            }
            is TdApi.MessagePhoto -> {
                val photo = content.photo
                val bestSize = photo.sizes.maxByOrNull { it.photo.size } ?: photo.sizes.firstOrNull()
                val thumbSize = photo.sizes.minByOrNull { it.photo.size }
                if (bestSize != null) {
                    return TdMessageInfo(
                        messageId = message.id,
                        chatId = message.chatId,
                        date = message.date,
                        caption = content.caption.text,
                        documentFileId = bestSize.photo.id,
                        documentFileName = "Photo_${message.date}.jpg",
                        documentSize = bestSize.photo.size,
                        documentMimeType = "image/jpeg",
                        thumbnailFileId = thumbSize?.photo?.id
                    )
                }
            }
            is TdApi.MessageVideo -> {
                val video = content.video
                val thumbId = video.thumbnail?.file?.id
                return TdMessageInfo(
                    messageId = message.id,
                    chatId = message.chatId,
                    date = message.date,
                    caption = content.caption.text,
                    documentFileId = video.video.id,
                    documentFileName = video.fileName.ifBlank { "Video_${message.date}.mp4" },
                    documentSize = video.video.size,
                    documentMimeType = video.mimeType.ifBlank { "video/mp4" },
                    thumbnailFileId = thumbId
                )
            }
            is TdApi.MessageAudio -> {
                val audio = content.audio
                val thumbId = audio.albumCoverThumbnail?.file?.id
                return TdMessageInfo(
                    messageId = message.id,
                    chatId = message.chatId,
                    date = message.date,
                    caption = content.caption.text,
                    documentFileId = audio.audio.id,
                    documentFileName = audio.fileName.ifBlank { "Audio_${message.date}.mp3" },
                    documentSize = audio.audio.size,
                    documentMimeType = audio.mimeType.ifBlank { "audio/mpeg" },
                    thumbnailFileId = thumbId
                )
            }
            is TdApi.MessageAnimation -> {
                val animation = content.animation
                val thumbId = animation.thumbnail?.file?.id
                return TdMessageInfo(
                    messageId = message.id,
                    chatId = message.chatId,
                    date = message.date,
                    caption = content.caption.text,
                    documentFileId = animation.animation.id,
                    documentFileName = animation.fileName.ifBlank { "Animation_${message.date}.gif" },
                    documentSize = animation.animation.size,
                    documentMimeType = animation.mimeType.ifBlank { "image/gif" },
                    thumbnailFileId = thumbId
                )
            }
        }
        return null
    }
}
