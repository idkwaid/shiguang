package dev.lumo.gallery.core.image

import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.db.toModel
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.model.DateSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 索引富化：为尚未生成缩略图的条目补宽度、高度、方向、拍摄时间和缩略图。
 * 每个图库一条串行任务，避免和 UI 抢 CPU；文件变化时扫描会把 thumbReady 重置，自动重跑。
 */
class ImageEnricher(
    private val db: LumoDatabase,
    private val thumbnailer: Thumbnailer,
    private val provider: MediaStreamProvider,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    private val jobs = ConcurrentHashMap<String, Job>()

    fun enqueue(libraryId: String) {
        jobs[libraryId]?.cancel()
        jobs[libraryId] = scope.launch(io) {
            enrich(libraryId)
        }
    }

    fun cancel(libraryId: String) {
        jobs.remove(libraryId)?.cancel()
    }

    private suspend fun enrich(libraryId: String) {
        val libraryEntity = db.libraryDao().getById(libraryId) ?: return
        val library = libraryEntity.toModel()
        while (true) {
            val pending = db.mediaDao().itemsNeedingThumbnailsInLibrary(libraryId, limit = 40)
            if (pending.isEmpty()) return
            for (entity in pending) {
                val item = entity.toModel()
                val result = runCatching {
                    thumbnailer.generate(library, item, provider)
                }.getOrNull()
                if (result != null) {
                    db.mediaDao().updateEnrichment(
                        id = item.id,
                        width = result.width,
                        height = result.height,
                        orientation = result.orientation,
                        dateTaken = result.dateTaken ?: item.lastModified,
                        dateSource = (if (result.dateTaken != null) DateSource.EXIF else DateSource.FILE).name,
                        mime = result.mime,
                        thumbReady = true
                    )
                } else {
                    // 解码失败也要标记“已尝试”，否则会无限重试；UI 用 hasDimensions/文件存在性显示占位
                    db.mediaDao().updateEnrichment(
                        id = item.id,
                        width = item.width,
                        height = item.height,
                        orientation = item.orientation,
                        dateTaken = item.dateTaken ?: item.lastModified,
                        dateSource = item.dateSource?.name ?: DateSource.FILE.name,
                        mime = item.mime,
                        thumbReady = true
                    )
                }
            }
        }
    }
}
