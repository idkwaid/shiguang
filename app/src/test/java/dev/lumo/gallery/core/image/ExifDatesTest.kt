package dev.lumo.gallery.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifDatesTest {

    @Test
    fun parses_standard_exif_format() {
        val parsed = ExifDates.parse("2023:05:18 09:31:07")
        // 只校验可解析且格式化为同一天，避免受本地时区影响
        assertEquals("2023-05-18", ExifDates.format(parsed))
    }

    @Test
    fun parses_dash_and_minute_variants() {
        assertEquals("2023-05-18", ExifDates.format(ExifDates.parse("2023-05-18 09:31:07")))
        assertEquals("2023-05-18", ExifDates.format(ExifDates.parse("2023:05:18 09:31")))
    }

    @Test
    fun rejects_garbage_and_empty() {
        assertNull(ExifDates.parse(""))
        assertNull(ExifDates.parse(null))
        assertNull(ExifDates.parse("0000:00:00 00:00:00"))
        assertNull(ExifDates.parse("not a date"))
    }

    @Test
    fun format_unknown_for_missing() {
        assertEquals("未知", ExifDates.format(null))
        assertEquals("未知", ExifDates.format(0L))
        assertEquals("未知", ExifDates.formatDateTime(-1L))
    }
}
