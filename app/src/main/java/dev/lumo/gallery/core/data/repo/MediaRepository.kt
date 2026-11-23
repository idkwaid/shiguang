package dev.lumo.gallery.core.data.repo

import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.db.MediaItemEntity
import dev.lumo.gallery.core.data.db.ReadingProgressEntity
import dev.lumo.gallery.core.data.db.TagCount
import dev.lumo.gallery.core.data.db.TagEntity
import dev.lumo.gallery.core.data.db.toModel
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 图片级别的读写：瀑布流、收藏、搜索、随机、进度、标签。
 */
class MediaRepository(private val db: LumoDatabase) {

    private val mediaDao get() = db.mediaDao()
    private val tagDao get() = db.tagDao()
    private val progressDao get() = db.progressDao()

    fun observeLibraryItems(libraryId: String): Flow<List<MediaItem>> =
        mediaDao.observePresent(libraryId).map { list -> list.map(MediaItemEntity::toModel) }

    fun observeAllItems(libraryId: String): Flow<List<MediaItem>> =
        mediaDao.observeAll(libraryId).map { list -> list.map(MediaItemEntity::toModel) }

    fun observeFavoriteItems(): Flow<List<MediaItem>> =
        mediaDao.observeFavorites().map { list -> list.map(MediaItemEntity::toModel) }

    fun observeSearch(query: String): Flow<List<MediaItem>> =
        mediaDao.search(escapeLike(query)).map { list -> list.map(MediaItemEntity::toModel) }

    suspend fun get(id: String): MediaItem? = mediaDao.getById(id)?.toModel()

    suspend fun itemsByIds(ids: List<String>): List<MediaItem> =
        mediaDao.getByIds(ids).map(MediaItemEntity::toModel)

    suspend fun libraryItems(libraryId: String): List<MediaItem> =
        mediaDao.presentItems(libraryId).map(MediaItemEntity::toModel)

    suspend fun toggleFavorite(id: String, current: Boolean): Boolean {
        val next = !current
        mediaDao.setFavorite(id, next)
        return next
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = mediaDao.setFavorite(id, favorite)

    suspend fun setNote(id: String, note: String?) = mediaDao.setNote(id, note?.trim()?.ifEmpty { null })

    suspend fun absentFavoriteCount(): Int = mediaDao.countAbsentFavorites()

    suspend fun clearAbsentFavorites(): Int = mediaDao.deleteAbsentFavorites()

    suspend fun randomSample(limit: Int): List<MediaItem> =
        mediaDao.randomSample(limit).map(MediaItemEntity::toModel)

    suspend fun randomFavoritesSample(limit: Int): List<MediaItem> =
        mediaDao.randomFavoritesSample(limit).map(MediaItemEntity::toModel)

    suspend fun datedItems(): List<MediaItem> =
        mediaDao.getDatedOnce().map(MediaItemEntity::toModel)

    // ---------- 标签 ----------

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map(TagEntity::toModel) }

    suspend fun tagsFor(mediaId: String): List<Tag> =
        tagDao.tagsFor(mediaId).map(TagEntity::toModel)

    suspend fun tagCounts(): List<TagCount> = tagDao.counts()

    fun observeTagCounts(): Flow<List<TagCount>> = tagDao.observeCounts()

    suspend fun createTag(name: String, colorArgb: Int): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val existing = tagDao.findByName(trimmed)
        if (existing != null) return existing.id
        return tagDao.insert(
            TagEntity(
                name = trimmed,
                colorArgb = colorArgb,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTag(tagId: Long) = tagDao.delete(tagId)

    suspend fun addTagToMedia(mediaId: String, tagId: Long) =
        tagDao.link(dev.lumo.gallery.core.data.db.MediaTagEntity(mediaId, tagId))

    suspend fun removeTagFromMedia(mediaId: String, tagId: Long) = tagDao.unlink(mediaId, tagId)

    fun observeItemsWithTag(tagId: Long): Flow<List<MediaItem>> =
        tagDao.observeItemsWithTag(tagId).map { list -> list.map(MediaItemEntity::toModel) }

    // ---------- 阅读进度 ----------

    fun observeProgress(libraryId: String): Flow<ReadingProgressEntity?> = progressDao.observe(libraryId)

    suspend fun getProgress(libraryId: String): ReadingProgressEntity? = progressDao.get(libraryId)

    suspend fun saveProgress(libraryId: String, mediaId: String, position: Int) {
        progressDao.upsert(
            ReadingProgressEntity(libraryId, mediaId, position, System.currentTimeMillis())
        )
    }

    suspend fun clearProgress(libraryId: String) = progressDao.clear(libraryId)

    companion object {
        /** LIKE 通配符转义，避免用户输入 % _ \ 影响结果。 */
        fun escapeLike(input: String): String = buildString(input.length) {
            for (c in input) {
                when (c) {
                    '\\', '%', '_' -> append('\\').append(c)
                    else -> append(c)
                }
            }
        }
    }
}
