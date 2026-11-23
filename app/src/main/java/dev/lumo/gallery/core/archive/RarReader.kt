package dev.lumo.gallery.core.archive

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarEncryptedException
import com.github.junrar.exception.WrongPasswordException
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.InputStream

/** RAR：junrar 读取（RAR 4–7，支持密码与加密头）。junrar 需要本地文件。 */
class RarReader(
    private val file: File,
    password: String?
) : ArchiveReader {

    private val archive: Archive = try {
        Archive(file, password)
    } catch (e: WrongPasswordException) {
        throw WrongArchivePasswordException("RAR 密码不正确")
    } catch (e: UnsupportedRarEncryptedException) {
        throw UnsupportedArchiveException("暂不支持这种 RAR 加密方式")
    } catch (e: Exception) {
        if (e is ArchiveOpenException) throw e
        if (password.isNullOrEmpty() && e.message?.contains("password", ignoreCase = true) == true) {
            throw ArchivePasswordRequiredException("RAR 压缩包已加密")
        }
        throw CorruptArchiveException("RAR 打开失败：${e.message}", e)
    }

    private val headers: List<FileHeader> = archive.fileHeaders.orEmpty()

    private val byPath: Map<String, FileHeader> = headers.associateBy { it.fileName }

    override val entries: List<ArchiveEntryRef> = headers
        .filter { !it.isDirectory }
        .map { header ->
            ArchiveEntryRef(
                path = header.fileName,
                name = header.fileName.substringAfterLast('/'),
                sizeBytes = header.fullUnpackSize,
                lastModified = runCatching { header.mTime?.time ?: 0L }.getOrDefault(0L),
                isDirectory = header.isDirectory,
                isEncrypted = header.isEncrypted
            )
        }

    override fun openEntry(path: String): InputStream {
        val header = byPath[path] ?: throw CorruptArchiveException("条目不存在：$path")
        return try {
            archive.getInputStream(header)
        } catch (e: WrongPasswordException) {
            throw WrongArchivePasswordException("RAR 密码不正确")
        } catch (e: Exception) {
            throw CorruptArchiveException("RAR 解压失败：${e.message}", e)
        }
    }

    override fun close() {
        runCatching { archive.close() }
    }
}
