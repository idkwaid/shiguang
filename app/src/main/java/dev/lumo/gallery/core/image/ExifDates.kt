package dev.lumo.gallery.core.image

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** EXIF 时间字符串解析（格式固定但没有时区，按本地时区解释）。 */
object ExifDates {

    private val FORMATS = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy:MM:dd HH:mm"
    )

    fun parse(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        for (pattern in FORMATS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
                val date = format.parse(value) ?: continue
                if (date.time > 0) return date.time
            } catch (_: ParseException) {
            }
        }
        return null
    }

    /** 展示用：2001-01-01 格式，缺失时显示“未知”。 */
    fun format(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "未知"
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "未知"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
