package dev.lumo.gallery.core.archive

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel

/** SAF 文档到可随机读写通道/本地文件的桥接。 */
object SafFiles {

    class ChannelHandle(
        val channel: FileChannel,
        private val pfd: ParcelFileDescriptor,
        private val stream: FileInputStream
    ) : Closeable {
        override fun close() {
            runCatching { channel.close() }
            runCatching { stream.close() }
            runCatching { pfd.close() }
        }
    }

    fun openChannel(context: Context, uri: Uri): ChannelHandle {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw CorruptArchiveException("无法打开压缩包：$uri")
        val stream = FileInputStream(pfd.fileDescriptor)
        return ChannelHandle(stream.channel, pfd, stream)
    }

    fun openStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw CorruptArchiveException("无法读取压缩包：$uri")

    fun sizeOf(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: -1L

    /** 把压缩包复制到应用缓存（RAR / 加密 ZIP 需要本地文件）。 */
    fun copyTo(context: Context, uri: Uri, dest: File, onProgress: (Long) -> Unit = {}) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, dest.name + ".part")
        openStream(context, uri).use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied % (4L * 1024 * 1024) < buffer.size) onProgress(copied)
                }
                output.fd.sync()
            }
        }
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
    }
}
