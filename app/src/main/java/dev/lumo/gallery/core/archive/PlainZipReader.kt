package dev.lumo.gallery.core.archive

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.FileChannel

/** 普通 ZIP：commons-compress 直接随机读写（不复制大文件）。 */
class PlainZipReader private constructor(
    private val zip: ZipFile,
    private val closer: AutoCloseable
) : ArchiveReader {

    override val entries: List<ArchiveEntryRef> = zip.entries.toList()
        .filter { !it.isDirectory }
        .map { entry ->
            ArchiveEntryRef(
                path = entry.name,
                name = entry.name.substringAfterLast('/'),
                sizeBytes = entry.size,
                lastModified = entry.lastModifiedDate?.time ?: 0L,
                isDirectory = entry.isDirectory,
                isEncrypted = entry.generalPurposeBit?.usesEncryption() == true
            )
        }

    private val byPath = zip.entries.toList().associateBy { it.name }

    override fun openEntry(path: String): InputStream {
        val entry = byPath[path] ?: throw CorruptArchiveException("条目不存在：$path")
        return zip.getInputStream(entry)
    }

    override fun close() {
        runCatching { zip.close() }
        runCatching { closer.close() }
    }

    companion object {
        fun fromChannel(channel: FileChannel, closer: AutoCloseable): PlainZipReader {
            val zip = try {
                ZipFile.builder().setSeekableByteChannel(channel).get()
            } catch (e: Exception) {
                throw CorruptArchiveException("ZIP 打开失败：${e.message}", e)
            }
            return PlainZipReader(zip, closer)
        }

        fun fromFile(file: File): PlainZipReader {
            val channel = FileInputStream(file).channel
            return fromChannel(channel, AutoCloseable { channel.close() })
        }
    }
}
