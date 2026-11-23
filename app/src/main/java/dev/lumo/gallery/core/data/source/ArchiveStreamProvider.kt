package dev.lumo.gallery.core.data.source

import dev.lumo.gallery.core.archive.ArchiveEntryCache
import dev.lumo.gallery.core.archive.ArchiveSessionStore
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 压缩包条目流：先解到 cache 文件再读，保证同一张图能被解码多次（EXIF/缩略图/大图），
 * 也避免多个读取器并发操作同一个压缩包句柄。
 */
class ArchiveStreamProvider(
    private val sessions: ArchiveSessionStore,
    private val entries: ArchiveEntryCache,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : MediaStreamProvider {

    override suspend fun open(library: Library, item: MediaItem): InputStream? = withContext(io) {
        entries.existing(library.id, item.locator, item.lastModified)?.let {
            return@withContext FileInputStream(it)
        }

        val session = sessions.readerOrNull(library) ?: return@withContext null
        session.lock.acquire()
        try {
            entries.existing(library.id, item.locator, item.lastModified)?.let {
                return@withContext FileInputStream(it)
            }
            val target = entries.fileFor(library.id, item.locator, item.lastModified)
            target.parentFile?.mkdirs()
            val temp = java.io.File(target.parentFile, target.name + ".part")
            session.reader.openEntry(item.locator).use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, 256 * 1024)
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            entries.trim()
            FileInputStream(target)
        } catch (e: dev.lumo.gallery.core.archive.ArchiveOpenException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            session.lock.release()
        }
    }
}

/** 按图库类型把读取流派发给文件夹/压缩包实现。 */
class CompositeStreamProvider(
    private val folder: MediaStreamProvider,
    private val archive: MediaStreamProvider
) : MediaStreamProvider {
    override suspend fun open(library: Library, item: MediaItem): InputStream? =
        if (library.isArchive) archive.open(library, item) else folder.open(library, item)
}
