package dev.lumo.gallery.core.archive

import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.InputStream

/** 7z：commons-compress 随机访问 + 密码（含加密头）。 */
class SevenZReader private constructor(
    private val sevenZ: SevenZFile,
    private val closer: AutoCloseable
) : ArchiveReader {

    private val allEntries: List<SevenZArchiveEntry> = sevenZ.entries.toList()
    private val byPath: Map<String, SevenZArchiveEntry> = allEntries.associateBy { it.name }

    override val entries: List<ArchiveEntryRef> = allEntries
        .filter { !it.isDirectory }
        .map { entry ->
            ArchiveEntryRef(
                path = entry.name,
                name = entry.name.substringAfterLast('/'),
                sizeBytes = entry.size,
                lastModified = runCatching { entry.lastModifiedDate?.time ?: 0L }.getOrDefault(0L),
                isDirectory = entry.isDirectory,
                isEncrypted = false
            )
        }

    override fun openEntry(path: String): InputStream {
        val entry = byPath[path] ?: throw CorruptArchiveException("条目不存在：$path")
        return sevenZ.getInputStream(entry)
    }

    override fun close() {
        runCatching { sevenZ.close() }
        runCatching { closer.close() }
    }

    companion object {
        fun fromChannel(
            channel: java.nio.channels.SeekableByteChannel,
            closer: AutoCloseable,
            password: String?
        ): SevenZReader {
            val builder = SevenZFile.builder().setSeekableByteChannel(channel)
            if (!password.isNullOrEmpty()) builder.setPassword(password)
            val file = try {
                builder.get()
            } catch (e: PasswordRequiredException) {
                throw ArchivePasswordRequiredException("7z 压缩包已加密")
            } catch (e: Exception) {
                throw CorruptArchiveException("7z 打开失败：${e.message}", e)
            }
            return SevenZReader(file, closer)
        }
    }
}
