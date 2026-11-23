package dev.lumo.gallery.core.archive

import android.content.Context
import android.net.Uri
import dev.lumo.gallery.core.model.ArchiveFormat
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.util.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 按图库来源打开压缩包读取器：
 * - ZIP：SAF 通道直接随机读，不复制；
 * - 7z：SAF 通道直接随机读，不复制；
 * - RAR / 加密 ZIP：压缩包需要本地文件（复制到 cache）。
 */
class ArchiveReaderFactory(
    private val context: Context,
    private val staging: ArchiveStagingCache,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun open(library: Library, password: String?): ArchiveReader = withContext(io) {
        val uri = library.archiveUri?.let(Uri::parse)
            ?: throw UnsupportedArchiveException("图库缺少压缩包地址")
        AppLog.i(
            "Archive",
            "打开压缩包 library=${library.id} format=${library.archiveFormat} " +
                "password=${!password.isNullOrEmpty()} uri=$uri"
        )
        when (library.archiveFormat) {
            ArchiveFormat.ZIP -> openZip(library, uri, password)
            ArchiveFormat.SEVEN_Z -> openSevenZ(library, uri, password)
            ArchiveFormat.RAR -> RarReader(localCopy(library, uri), password)
            null -> {
                // 从扩展名兜底
                val name = library.name.lowercase()
                when {
                    name.endsWith(".zip") -> openZip(library, uri, password)
                    name.endsWith(".7z") -> openSevenZ(library, uri, password)
                    else -> RarReader(localCopy(library, uri), password)
                }
            }
        }
    }

    private fun openZip(library: Library, uri: Uri, password: String?): ArchiveReader {
        val handle = SafFiles.openChannel(context, uri)
        val reader = try {
            PlainZipReader.fromChannel(handle.channel, handle)
        } catch (e: Exception) {
            handle.close()
            throw e
        }
        if (reader.entries.any { it.isEncrypted }) {
            reader.close()
            val local = localCopy(library, uri)
            if (password.isNullOrEmpty()) {
                throw ArchivePasswordRequiredException("ZIP 压缩包已加密")
            }
            return EncryptedZipReader(local, password)
        }
        return reader
    }

    private fun openSevenZ(library: Library, uri: Uri, password: String?): ArchiveReader {
        val handle = SafFiles.openChannel(context, uri)
        return try {
            SevenZReader.fromChannel(handle.channel, handle, password)
        } catch (e: ArchiveOpenException) {
            handle.close()
            throw e
        } catch (e: Exception) {
            handle.close()
            throw CorruptArchiveException("7z 打开失败：${e.message}", e)
        }
    }

    private fun localCopy(library: Library, uri: Uri): File {
        val extension = when (library.archiveFormat) {
            ArchiveFormat.RAR -> "rar"
            ArchiveFormat.ZIP -> "zip"
            ArchiveFormat.SEVEN_Z -> "7z"
            null -> "bin"
        }
        val dest = File(staging.dir(), "${library.id}.$extension")
        val remoteSize = SafFiles.sizeOf(context, uri)
        if (dest.isFile && dest.length() > 0 && (remoteSize < 0 || dest.length() == remoteSize)) {
            return dest
        }
        SafFiles.copyTo(context, uri, dest)
        return dest
    }
}
