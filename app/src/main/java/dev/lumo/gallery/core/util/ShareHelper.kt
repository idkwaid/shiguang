package dev.lumo.gallery.core.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 分享：把条目导出到 cache/share 后走 FileProvider 发出。 */
object ShareHelper {

    suspend fun share(
        context: Context,
        provider: MediaStreamProvider,
        library: Library,
        item: MediaItem
    ): Boolean = withContext(Dispatchers.IO) {
        val safeName = item.displayName.replace(Regex("[^A-Za-z0-9._\\u4e00-\\u9fa5-]"), "_")
            .ifBlank { "image" }
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        val out = File(dir, safeName)
        val ok = runCatching {
            provider.open(library, item)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (!ok || out.length() == 0L) {
            out.delete()
            return@withContext false
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mime.ifBlank { "image/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
