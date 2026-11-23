package dev.lumo.gallery.core.archive

/** 归档读取错误分类，UI 按类型给不同提示与操作。 */
sealed class ArchiveOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ArchivePasswordRequiredException(
    message: String = "压缩包已加密，需要密码",
    cause: Throwable? = null
) : ArchiveOpenException(message, cause)

class WrongArchivePasswordException(
    message: String = "密码不正确",
    cause: Throwable? = null
) : ArchiveOpenException(message, cause)

class CorruptArchiveException(
    message: String = "压缩包已损坏或格式不受支持",
    cause: Throwable? = null
) : ArchiveOpenException(message, cause)

class UnsupportedArchiveException(
    message: String,
    cause: Throwable? = null
) : ArchiveOpenException(message, cause)

/** 压缩包内一个条目（目录已过滤掉）。 */
data class ArchiveEntryRef(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean
)

/**
 * 压缩包读取器。打开后条目列表固定，条目流按路径按需读取。
 */
interface ArchiveReader : AutoCloseable {
    val entries: List<ArchiveEntryRef>
    fun openEntry(path: String): java.io.InputStream
}
