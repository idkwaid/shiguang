package dev.lumo.gallery.core.data.scan

import androidx.room.withTransaction
import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.db.MediaItemEntity
import dev.lumo.gallery.core.model.ScannedEntry
import dev.lumo.gallery.core.util.MediaIds
import dev.lumo.gallery.core.util.NaturalSortKey

/**
 * 把扫描到的条目写入数据库：增量保留未变化条目的缩略图/EXIF/收藏/备注，
 * 消失的条目标记为 isPresent = false（收藏占位依赖它）。
 */
class MediaIndexer(
    private val db: LumoDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {

    data class IndexResult(val count: Int, val coverItemId: String?)

    suspend fun index(
        libraryId: String,
        entries: List<ScannedEntry>,
        sortKeyOf: (ScannedEntry) -> String = { NaturalSortKey.of(it.displayName) }
    ): IndexResult {
        val now = clock()
        val existing = db.mediaDao().snapshot(libraryId).associateBy { it.locator }

        val entities = entries.map { entry ->
            val id = MediaIds.of(libraryId, entry.locator)
            val sortKey = sortKeyOf(entry)
            val old = existing[entry.locator]
            val unchanged = old != null &&
                old.sizeBytes == entry.sizeBytes &&
                old.lastModified == entry.lastModified
            if (unchanged && old != null) {
                old.copy(
                    id = id,
                    docUri = entry.docUri,
                    displayName = entry.displayName,
                    mime = entry.mime,
                    sortKey = sortKey,
                    isPresent = true
                )
            } else {
                MediaItemEntity(
                    id = id,
                    libraryId = libraryId,
                    locator = entry.locator,
                    docUri = entry.docUri,
                    displayName = entry.displayName,
                    mime = entry.mime,
                    sizeBytes = entry.sizeBytes,
                    lastModified = entry.lastModified,
                    dateTaken = null,
                    dateSource = null,
                    width = 0,
                    height = 0,
                    orientation = 0,
                    note = old?.note,
                    isFavorite = old?.isFavorite ?: false,
                    isPresent = true,
                    thumbReady = false,
                    phash = null,
                    addedAt = old?.addedAt ?: now,
                    sortKey = sortKey
                )
            }
        }

        db.withTransaction {
            db.mediaDao().markAllAbsent(libraryId)
            entities.chunked(300).forEach { db.mediaDao().upsertAll(it) }
        }
        val cover = db.mediaDao().firstPresent(libraryId)
        return IndexResult(count = entities.size, coverItemId = cover?.id)
    }

    suspend fun markEmpty(libraryId: String) {
        db.withTransaction {
            db.mediaDao().markAllAbsent(libraryId)
        }
    }
}
