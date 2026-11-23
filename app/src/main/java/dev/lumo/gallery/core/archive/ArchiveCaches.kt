package dev.lumo.gallery.core.archive

import android.content.Context
import java.io.File

/** 压缩包复制的暂存目录（RAR / 加密 ZIP 用）。 */
class ArchiveStagingCache(private val context: Context) {
    private val root = File(context.cacheDir, "archive-stage")

    fun dir(): File {
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun deleteLibrary(libraryId: String) {
        dir().listFiles()?.forEach { file ->
            if (file.name.startsWith("$libraryId.")) file.delete()
        }
    }

    fun deleteAll() {
        root.deleteRecursively()
    }
}

/**
 * 压缩包条目解压缓存：把条目落到 cache 文件，保证流可多次打开且不受读取器并发影响。
 */
class ArchiveEntryCache(private val context: Context) {

    private val root = File(context.cacheDir, "archive-entries")

    fun fileFor(libraryId: String, locator: String, lastModified: Long): File {
        val dir = File(root, libraryId)
        if (!dir.exists()) dir.mkdirs()
        val key = dev.lumo.gallery.core.util.MediaIds.of(libraryId, locator)
        return File(dir, "${key}_$lastModified.bin")
    }

    fun existing(libraryId: String, locator: String, lastModified: Long): File? {
        val file = fileFor(libraryId, locator, lastModified)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    fun deleteLibrary(libraryId: String) {
        File(root, libraryId).deleteRecursively()
    }

    fun trim(maxBytes: Long = 512L * 1024 * 1024) {
        if (!root.exists()) return
        val files = root.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    fun totalBytes(): Long =
        if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
