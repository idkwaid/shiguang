package dev.lumo.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class SizeGroup(
    val sizeBytes: Long,
    val count: Int
)

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items WHERE libraryId = :libraryId AND isPresent = 1 ORDER BY sortKey")
    fun observePresent(libraryId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE libraryId = :libraryId ORDER BY sortKey")
    fun observeAll(libraryId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE libraryId = :libraryId ORDER BY sortKey")
    suspend fun snapshot(libraryId: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE libraryId = :libraryId AND isPresent = 1 ORDER BY sortKey")
    suspend fun presentItems(libraryId: String): List<MediaItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isPresent = 0 WHERE libraryId = :libraryId")
    suspend fun markAllAbsent(libraryId: String)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE media_items SET thumbReady = :ready WHERE id = :id")
    suspend fun setThumbReady(id: String, ready: Boolean)

    @Query(
        "UPDATE media_items SET width = :width, height = :height, orientation = :orientation, " +
            "dateTaken = :dateTaken, dateSource = :dateSource, mime = :mime, thumbReady = :thumbReady " +
            "WHERE id = :id"
    )
    suspend fun updateEnrichment(
        id: String,
        width: Int,
        height: Int,
        orientation: Int,
        dateTaken: Long?,
        dateSource: String?,
        mime: String,
        thumbReady: Boolean
    )

    @Query("UPDATE media_items SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("UPDATE media_items SET phash = :phash WHERE id = :id")
    suspend fun setPhash(id: String, phash: String?)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items WHERE libraryId = :libraryId")
    suspend fun deleteByLibrary(libraryId: String)

    @Query("SELECT COUNT(*) FROM media_items WHERE libraryId = :libraryId AND isPresent = 1")
    suspend fun countPresent(libraryId: String): Int

    @Query("SELECT * FROM media_items WHERE libraryId = :libraryId AND isPresent = 1 ORDER BY sortKey LIMIT 1")
    suspend fun firstPresent(libraryId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE thumbReady = 0 AND isPresent = 1 ORDER BY libraryId, sortKey LIMIT :limit")
    suspend fun itemsNeedingThumbnails(limit: Int): List<MediaItemEntity>

    @Query(
        "SELECT * FROM media_items WHERE libraryId = :libraryId AND thumbReady = 0 AND isPresent = 1 " +
            "ORDER BY sortKey LIMIT :limit"
    )
    suspend fun itemsNeedingThumbnailsInLibrary(libraryId: String, limit: Int): List<MediaItemEntity>

    /**
     * 收藏聚合：跨图库、排除私密图库；失效条目（isPresent = 0）保留用于灰占位。
     */
    @Query(
        "SELECT m.* FROM media_items m INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE m.isFavorite = 1 AND l.isPrivate = 0 ORDER BY m.addedAt DESC"
    )
    fun observeFavorites(): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items WHERE isFavorite = 1 AND isPresent = 0")
    suspend fun countAbsentFavorites(): Int

    @Query("DELETE FROM media_items WHERE isFavorite = 1 AND isPresent = 0")
    suspend fun deleteAbsentFavorites(): Int

    @Query(
        "SELECT m.* FROM media_items m INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE m.isPresent = 1 AND l.isPrivate = 0 AND m.displayName LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY m.sortKey LIMIT :limit"
    )
    fun search(query: String, limit: Int = 200): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isPresent = 1 AND dateTaken IS NOT NULL ORDER BY dateTaken DESC")
    fun observeDated(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isPresent = 1 AND dateTaken IS NOT NULL ORDER BY dateTaken DESC")
    suspend fun getDatedOnce(): List<MediaItemEntity>

    @Query(
        "SELECT m.* FROM media_items m INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE m.isPresent = 1 AND l.isPrivate = 0 AND l.isAvailable = 1 ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun randomSample(limit: Int): List<MediaItemEntity>

    @Query(
        "SELECT m.* FROM media_items m INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE m.isPresent = 1 AND m.isFavorite = 1 AND l.isPrivate = 0 ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun randomFavoritesSample(limit: Int): List<MediaItemEntity>

    @Query(
        "SELECT sizeBytes, COUNT(*) AS count FROM media_items WHERE isPresent = 1 " +
            "GROUP BY sizeBytes HAVING count > 1 ORDER BY count DESC LIMIT 200"
    )
    suspend fun duplicateSizeGroups(): List<SizeGroup>

    @Query("SELECT * FROM media_items WHERE isPresent = 1 AND sizeBytes = :size LIMIT 64")
    suspend fun itemsBySize(size: Long): List<MediaItemEntity>

    @Query(
        "SELECT m.* FROM media_items m INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE m.isPresent = 1 AND m.phash IS NOT NULL AND l.isPrivate = 0 LIMIT 20000"
    )
    suspend fun itemsWithPhash(): List<MediaItemEntity>

    @Query(
        "SELECT * FROM media_items WHERE isPresent = 1 AND mime LIKE :pattern " +
            "ORDER BY sizeBytes DESC LIMIT 200"
    )
    suspend fun itemsByMime(pattern: String): List<MediaItemEntity>
}
