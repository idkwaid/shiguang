package dev.lumo.gallery.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/** 保存编辑后的图片副本：API 29+ 写入系统相册，旧系统落到应用外部目录。 */
object MediaExporter {

    fun saveBitmap(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val mime = "image/jpeg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { saveToMediaStore(context, bitmap, displayName, mime) }.getOrNull()
        } else {
            runCatching { saveToAppDir(context, bitmap, displayName) }.getOrNull()
        }
    }

    private fun saveToMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        mime: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/拾光")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveToAppDir(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "拾光")
        dir.mkdirs()
        val file = File(dir, displayName)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        return Uri.fromFile(file)
    }
}
