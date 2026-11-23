package dev.lumo.gallery.core.archive

import dev.lumo.gallery.core.model.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * 压缩包读取会话缓存：同一图库只保留一个读取器（7z/RAR 打开/seek 代价高），
 * 密码只存内存，进程被杀后需要重新输入。
 */
class ArchiveSessionStore(
    private val factory: ArchiveReaderFactory
) {

    class Session(val reader: ArchiveReader) {
        /** 串行读取：7z/RAR 的条目流共享底层句柄。 */
        val lock = Semaphore(1)
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val passwords = ConcurrentHashMap<String, String>()
    private val openLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun reader(library: Library): ArchiveReader {
        sessions[library.id]?.let { return it.reader }
        val mutex = openLocks.getOrPut(library.id) { Mutex() }
        return mutex.withLock {
            sessions[library.id]?.reader ?: run {
                val reader = withContext(Dispatchers.IO) {
                    factory.open(library, passwords[library.id])
                }
                sessions[library.id] = Session(reader)
                reader
            }
        }
    }

    suspend fun readerOrNull(library: Library): Session? = runCatching {
        Session(reader(library)).let { session ->
            // 复用已缓存会话，避免重复包装
            sessions[library.id] ?: session
        }
    }.getOrNull()

    fun hasPassword(libraryId: String): Boolean = passwords.containsKey(libraryId)

    fun setPassword(libraryId: String, password: String) {
        passwords[libraryId] = password
        close(libraryId)
    }

    fun forgetPassword(libraryId: String) {
        passwords.remove(libraryId)
    }

    fun close(libraryId: String) {
        sessions.remove(libraryId)?.let { session ->
            runCatching { session.reader.close() }
        }
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
        sessions.clear()
    }
}
