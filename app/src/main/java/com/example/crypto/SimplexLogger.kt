package com.example.crypto

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * SimplexLogger - A thread-safe, persistent file-based logging utility.
 * Captures system actions, security protocols, SOCKS5 packet movements, and errors.
 * Write logs to internal app storage (accessible via ADB or in-app tools).
 */
object SimplexLogger {
    private const val FILE_NAME = "simplex_system_logs.txt"
    private var logFile: File? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        logFile = File(context.filesDir, FILE_NAME)
        log("SYSTEM", "================= SIMPLEX LOGGER INITIALIZED =================")
        log("SYSTEM", "System Locale: ${Locale.getDefault().language}, Current Time: ${Date()}")
    }

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timeStr = sdf.format(Date())
        val logLine = "[$timeStr] [$tag] $message"
        
        // Write to system Logcat for developer visibility
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }

        // Write to persistent file
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { fw ->
                    PrintWriter(fw).use { pw ->
                        pw.println(logLine)
                        throwable?.printStackTrace(pw)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SimplexLogger", "Critical error writing to file log: ${e.message}")
        }
    }

    /**
     * Retrieves the file where logs are stored.
     */
    fun getLogFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /**
     * Clean slate - wipes current log file contents (e.g. on panic/zeroization).
     */
    fun clearLog() {
        try {
            logFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
                file.createNewFile()
            }
            log("SYSTEM", "Logs purged and zeroized successfully.")
        } catch (e: Exception) {
            Log.e("SimplexLogger", "Failed to clear persistent logs", e)
        }
    }
}
