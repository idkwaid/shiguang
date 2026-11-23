package dev.lumo.gallery.core.image

import android.content.Context
import java.io.File

/**
 * 缩略图磁盘缓存：cacheDir/thumbs/<libraryId>/<itemId>_<mtime>.jpg
 * 文件名带 mtime，源文件变化时自然换新文件，旧文件由清理任务回收。
 */
class ThumbCache(private val context: Context) {

    private val root: File = File(context.cacheDir, "thumbs")

    fun fileFor(libraryId: String, itemId: String, lastModified: Long, kind: String = "thumbs"): File {
        val dir = File(File(context.cacheDir, kind), libraryId)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${itemId}_$lastModified.jpg")
    }

    fun existing(libraryId: String, itemId: String, lastModified: Long, kind: String = "thumbs"): File? {
        val file = fileFor(libraryId, itemId, lastModified, kind)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    fun deleteLibrary(libraryId: String) {
        File(root, libraryId).deleteRecursively()
        File(File(context.cacheDir, "previews"), libraryId).deleteRecursively()
    }

    fun clearAll() {
        root.deleteRecursively()
        root.mkdirs()
    }

    fun totalBytes(): Long {
        if (!root.exists()) return 0
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun libraryBytes(libraryId: String): Long {
        val dir = File(root, libraryId)
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** 删除该图库下不在保留集合里的缩略图。 */
    fun pruneLibrary(libraryId: String, keep: Set<String>) {
        val dir = File(root, libraryId)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }
}
