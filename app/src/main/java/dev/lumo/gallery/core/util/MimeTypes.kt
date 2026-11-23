package dev.lumo.gallery.core.util

import android.webkit.MimeTypeMap
import java.util.Locale

object MimeTypes {

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
        "jfif", "jpe", "dng", "tif", "tiff"
    )

    private val ARCHIVE_EXTENSIONS = mapOf(
        "zip" to "ZIP",
        "rar" to "RAR",
        "7z" to "SEVEN_Z"
    )

    /** 扩展名（小写，不含点）。 */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun isImageName(name: String): Boolean = extensionOf(name) in IMAGE_EXTENSIONS

    fun isImageMime(mime: String?): Boolean =
        mime != null && mime.startsWith("image/", ignoreCase = true)

    /** 图片判断：MIME 优先，文件名兜底（部分 SAF provider 对 MIME 不可靠）。 */
    fun isImage(name: String, mime: String?): Boolean =
        isImageMime(mime) || isImageName(name)

    fun archiveFormatOf(name: String): String? = ARCHIVE_EXTENSIONS[extensionOf(name)]

    fun guessMimeOf(name: String, fallback: String? = null): String {
        if (fallback != null && fallback.isNotBlank() && fallback != "application/octet-stream") {
            return fallback
        }
        val ext = extensionOf(name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "heic", "heif" -> "image/heic"
                "avif" -> "image/avif"
                "webp" -> "image/webp"
                else -> "image/*"
            }
    }

    /** 是否是可播放动图（GIF/动态 WebP）。 */
    fun isAnimated(mime: String?, name: String): Boolean {
        val ext = extensionOf(name)
        return ext == "gif" || ext == "webp" && (mime ?: "").contains("webp") ||
            (mime ?: "").equals("image/gif", ignoreCase = true)
    }
}
