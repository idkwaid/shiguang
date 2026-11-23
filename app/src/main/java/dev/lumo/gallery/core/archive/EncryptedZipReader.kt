package dev.lumo.gallery.core.archive

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.InputStream

/** 加密 ZIP：zip4j 读标准 ZipCrypto 与 AES（需要本地文件）。 */
class EncryptedZipReader(file: java.io.File, password: String) : ArchiveReader {

    private val zip = ZipFile(file, password.toCharArray())

    private val headers: List<FileHeader> = try {
        zip.fileHeaders
    } catch (e: Exception) {
        throw WrongArchivePasswordException("ZIP 密码不正确：${e.message}")
    }

    private val byPath = headers.associateBy { it.fileName }

    override val entries: List<ArchiveEntryRef> = headers
        .filter { !it.isDirectory }
        .map { header ->
            ArchiveEntryRef(
                path = header.fileName,
                name = header.fileName.substringAfterLast('/'),
                sizeBytes = header.uncompressedSize,
                lastModified = runCatching { header.lastModifiedTime }.getOrDefault(0L),
                isDirectory = header.isDirectory,
                isEncrypted = header.isEncrypted
            )
        }

    override fun openEntry(path: String): InputStream {
        val header = byPath[path] ?: throw CorruptArchiveException("条目不存在：$path")
        return try {
            zip.getInputStream(header)
        } catch (e: Exception) {
            throw WrongArchivePasswordException("ZIP 密码不正确：${e.message}")
        }
    }

    override fun close() {
        runCatching { zip.close() }
    }
}
