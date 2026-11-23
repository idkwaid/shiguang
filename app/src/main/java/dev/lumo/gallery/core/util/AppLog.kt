package dev.lumo.gallery.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量文件日志：
 * - 同时写到 Logcat 与 filesDir/logs/lumo.log；
 * - 单文件超过 1MB 自动轮转，最多保留 3 个历史文件；
 * - 注册全局崩溃处理器，崩溃堆栈也会落盘，便于反馈问题；
 * - 日志只在本地，用户可在「设置 → 运行日志」查看/分享/清空。
 */
object AppLog {

    private const val DEFAULT_MAX_BYTES = 1_000_000L
    private const val MAX_ARCHIVES = 3

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var dir: File? = null
    @Volatile private var maxBytes: Long = DEFAULT_MAX_BYTES

    fun init(context: Context) {
        init(dirPath = File(context.filesDir, "logs"), installCrashHandler = true)
    }

    /** 仅供单元测试，避免依赖 Android Context。 */
    fun initForTest(testDir: File, maxBytesLimit: Long = DEFAULT_MAX_BYTES) {
        init(dirPath = testDir, installCrashHandler = false, limit = maxBytesLimit)
    }

    private fun init(dirPath: File, installCrashHandler: Boolean, limit: Long = DEFAULT_MAX_BYTES) {
        dir = dirPath.apply { mkdirs() }
        maxBytes = limit
        if (installCrashHandler) installCrashHandlerOnce()
        i("App", "日志启动 @ ${file()?.absolutePath}")
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)
    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, t: Throwable? = null) = write("W", tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = write("E", tag, message, t)

    fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (throwable != null) {
                append('\n').append(throwable.stackTraceToString())
            }
            append('\n')
        }
        runCatching { Log.println(Log.DEBUG, tag, message) }
        append(line)
    }

    private fun append(line: String) {
        val directory = dir ?: return
        synchronized(lock) {
            runCatching {
                rotateIfNeeded(directory)
                File(directory, "lumo.log").appendText(line)
            }
        }
    }

    private fun rotateIfNeeded(directory: File) {
        val current = File(directory, "lumo.log")
        if (!current.isFile || current.length() < maxBytes) return
        for (index in MAX_ARCHIVES downTo 1) {
            val older = File(directory, "lumo.$index.log")
            val newer = if (index == 1) current else File(directory, "lumo.${index - 1}.log")
            if (newer.isFile) {
                if (index == MAX_ARCHIVES) older.delete()
                newer.renameTo(older)
            }
        }
    }

    fun file(): File? = dir?.let { File(it, "lumo.log") }

    fun readTail(maxLines: Int = 800): String {
        val all = mutableListOf<String>()
        val directory = dir ?: return "日志尚未初始化"
        for (index in MAX_ARCHIVES downTo 1) {
            val archive = File(directory, "lumo.$index.log")
            if (archive.isFile) runCatching { all.addAll(archive.readLines()) }
        }
        val current = File(directory, "lumo.log")
        if (current.isFile) runCatching { all.addAll(current.readLines()) }
        if (all.isEmpty()) return "还没有日志"
        return all.takeLast(maxLines).joinToString("\n")
    }

    fun clear() {
        val directory = dir ?: return
        synchronized(lock) {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun installCrashHandlerOnce() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(previous))
    }

    private class CrashHandler(
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            AppLog.e("Crash", "未捕获异常（线程 ${thread.name}）", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
