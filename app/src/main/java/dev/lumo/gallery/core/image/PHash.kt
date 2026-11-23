package dev.lumo.gallery.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 64 位 dHash：缩到 9x8 灰度后比较相邻像素亮度，得到 64 bit 指纹。
 * 相同/近似图片汉明距离很小，用于「相似图」检测。
 */
object PHash {

    const val DEFAULT_THRESHOLD = 8

    fun dHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(small.getPixel(x, y))
                val right = luminance(small.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (small !== bitmap) small.recycle()
        return hash
    }

    fun fromFile(file: File?): Long? {
        if (file == null || !file.isFile) return null
        return runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            dHash(bitmap).also { bitmap.recycle() }
        }.getOrNull()
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun toHex(hash: Long): String = "%016x".format(hash)

    fun fromHex(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { java.lang.Long.parseUnsignedLong(text, 16) }.getOrNull()
    }

    private fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
