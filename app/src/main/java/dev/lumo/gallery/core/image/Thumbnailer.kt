package dev.lumo.gallery.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

data class ThumbnailResult(
    val file: File,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val dateTaken: Long?,
    val mime: String
)

/**
 * 生成 480px 缩略图，同时一次性读出宽高 / 方向 / EXIF 拍摄时间，供索引富化使用。
 * 解码失败返回 null，由调用方标记“已尝试”，UI 显示占位。
 */
class Thumbnailer(
    @Suppress("unused") private val context: Context,
    private val cache: ThumbCache,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun generate(
        library: Library,
        item: MediaItem,
        provider: MediaStreamProvider,
        maxSize: Int = 480,
        kind: String = "thumbs"
    ): ThumbnailResult? = withContext(io) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        provider.open(library, item)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

        var sample = 1
        while (maxOf(rawWidth, rawHeight) / (sample * 2) >= maxSize) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = provider.open(library, item)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        val meta = readExif(library, item, provider)
        val scaled = scale(decoded, maxSize)
        if (scaled !== decoded) decoded.recycle()

        val rotated = rotate(scaled, meta.orientation)
        if (rotated !== scaled) scaled.recycle()

        val target = cache.fileFor(library.id, item.id, item.lastModified, kind)
        target.parentFile?.mkdirs()
        val ok = runCatching {
            FileOutputStream(target).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        }.isSuccess
        rotated.recycle()

        if (!ok || !target.isFile || target.length() == 0L) {
            target.delete()
            return@withContext null
        }

        ThumbnailResult(
            file = target,
            width = rawWidth,
            height = rawHeight,
            orientation = meta.orientation,
            dateTaken = meta.dateTaken,
            mime = item.mime
        )
    }

    private data class ExifMeta(val orientation: Int, val dateTaken: Long?)

    private suspend fun readExif(
        library: Library,
        item: MediaItem,
        provider: MediaStreamProvider
    ): ExifMeta {
        var orientation = 0
        var dateTaken: Long? = null
        runCatching {
            // ExifInterface 需要 mark/reset，用 BufferedInputStream 包一层
            val stream = provider.open(library, item) ?: return@runCatching
            BufferedInputStream(stream).use { input ->
                val exif = ExifInterface(input)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                dateTaken = ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    ?: ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                    ?: ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME))
            }
        }
        return ExifMeta(orientation, dateTaken)
    }

    private fun scale(bitmap: Bitmap, maxSize: Int): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / max
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL
        if (degrees == 0f && !flip) return bitmap
        val matrix = Matrix().apply {
            if (degrees != 0f) postRotate(degrees)
            if (flip) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
