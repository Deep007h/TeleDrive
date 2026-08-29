package com.teledrive.app.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: String? = null,
    val threadName: String = Thread.currentThread().name
) {
    fun toFormattedString(dateFormat: SimpleDateFormat): String {
        val timeStr = dateFormat.format(Date(timestamp))
        val threadStr = "[Thread:${threadName}]"
        val levelStr = "[${level.name}]"
        val tagStr = "[${tag}]"
        val errorStr = if (!throwable.isNullOrBlank()) "\nStacktrace:\n$throwable" else ""
        return "$timeStr $levelStr $threadStr $tagStr: $message$errorStr"
    }
}

object AppLogger {
    private const val GLOBAL_TAG = "TeleDrive"
    private const val MAX_MEMORY_LOGS = 1000
    private const val MAX_LOG_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val _liveLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val liveLogs: StateFlow<List<LogEntry>> = _liveLogs.asStateFlow()

    private var primaryLogFile: File? = null
    private var internalLogFile: File? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            // Primary location in external files dir (accessible via adb run-as or direct path)
            val externalLogsDir = context.getExternalFilesDir("logs")
            if (externalLogsDir != null) {
                if (!externalLogsDir.exists()) externalLogsDir.mkdirs()
                primaryLogFile = File(externalLogsDir, "teledrive.log")
            }

            // Internal fallback location
            val internalLogsDir = File(context.filesDir, "logs")
            if (!internalLogsDir.exists()) internalLogsDir.mkdirs()
            internalLogFile = File(internalLogsDir, "teledrive.log")

            // Setup uncaught exception handler
            setupUncaughtExceptionHandler()

            i("AppLogger", "=======================================================")
            i("AppLogger", "TeleDrive AppLogger Initialized")
            i("AppLogger", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})")
            i("AppLogger", "Primary Log File: ${primaryLogFile?.absolutePath}")
            i("AppLogger", "Internal Log File: ${internalLogFile?.absolutePath}")
            i("AppLogger", "=======================================================")
        } catch (e: Exception) {
            Log.e(GLOBAL_TAG, "Failed to initialize AppLogger files", e)
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("CrashHandler", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            // Flush synchronously before exit
            flushSync()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    // Specialized Logging Helpers
    fun logTdLib(event: String, details: String) {
        i("TDLib", "[$event] $details")
    }

    fun logSync(stage: String, details: String) {
        i("SyncEngine", "[$stage] $details")
    }

    fun logDb(operation: String, details: String) {
        d("Database", "[$operation] $details")
    }

    fun logTransfer(type: String, fileId: Int, fileName: String, details: String) {
        i("Transfer", "[$type | fileId=$fileId | $fileName] $details")
    }

    fun logUi(screen: String, action: String, details: String = "") {
        d("UI", "[$screen] $action ${if (details.isNotEmpty()) "- $details" else ""}")
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val stackTraceStr = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        // 1. Output to Android Logcat
        val fullTag = "$GLOBAL_TAG:$tag"
        val logcatMsg = if (stackTraceStr != null) "$message\n$stackTraceStr" else message
        when (level) {
            LogLevel.DEBUG -> Log.d(fullTag, logcatMsg)
            LogLevel.INFO -> Log.i(fullTag, logcatMsg)
            LogLevel.WARN -> Log.w(fullTag, logcatMsg)
            LogLevel.ERROR -> Log.e(fullTag, logcatMsg)
        }

        // 2. Add to in-memory state flow and file write queue
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = stackTraceStr
        )

        logQueue.add(entry)

        scope.launch {
            // Update in-memory ring buffer
            val current = _liveLogs.value
            val updated = if (current.size >= MAX_MEMORY_LOGS) {
                current.drop(current.size - MAX_MEMORY_LOGS + 1) + entry
            } else {
                current + entry
            }
            _liveLogs.value = updated

            // Write to disk
            writePendingLogs()
        }
    }

    @Synchronized
    private fun writePendingLogs() {
        val entriesToWrite = mutableListOf<LogEntry>()
        while (true) {
            val entry = logQueue.poll() ?: break
            entriesToWrite.add(entry)
        }

        if (entriesToWrite.isEmpty()) return

        val text = buildString {
            entriesToWrite.forEach { entry ->
                appendLine(entry.toFormattedString(dateFormat))
            }
        }

        writeToFile(primaryLogFile, text)
        writeToFile(internalLogFile, text)
    }

    private fun flushSync() {
        writePendingLogs()
    }

    private fun writeToFile(file: File?, text: String) {
        if (file == null) return
        try {
            if (file.exists() && file.length() > MAX_LOG_FILE_SIZE_BYTES) {
                // Rotate log
                val backupFile = File(file.parentFile, "${file.nameWithoutExtension}_prev.log")
                if (backupFile.exists()) backupFile.delete()
                file.renameTo(backupFile)
            }

            FileWriter(file, true).use { writer ->
                writer.write(text)
            }
        } catch (e: Exception) {
            Log.e(GLOBAL_TAG, "Error writing to log file: ${file.absolutePath}", e)
        }
    }

    fun getLogFilePath(): String {
        return primaryLogFile?.absolutePath ?: internalLogFile?.absolutePath ?: "Logs not initialized"
    }

    fun getAllLogsText(): String {
        return try {
            val file = primaryLogFile?.takeIf { it.exists() } ?: internalLogFile?.takeIf { it.exists() }
            file?.readText() ?: "No log file found"
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs() {
        try {
            primaryLogFile?.delete()
            internalLogFile?.delete()
            _liveLogs.value = emptyList()
            i("AppLogger", "Logs cleared by user")
        } catch (e: Exception) {
            e("AppLogger", "Failed to clear logs", e)
        }
    }
}
