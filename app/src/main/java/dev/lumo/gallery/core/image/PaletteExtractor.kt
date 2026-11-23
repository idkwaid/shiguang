package dev.lumo.gallery.core.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import java.io.File

/** 从当前图片抽主色，用于沉浸式界面控件动态取色。 */
object PaletteExtractor {

    fun fromFile(file: File?): Color? {
        if (file == null) return null
        return runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val palette = Palette.from(bitmap).generate()
            bitmap.recycle()
            val rgb = palette.getVibrantColor(palette.getMutedColor(0))
            if (rgb == 0) null else Color(rgb)
        }.getOrNull()
    }
}
