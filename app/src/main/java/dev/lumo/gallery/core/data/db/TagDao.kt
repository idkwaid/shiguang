package dev.lumo.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TagCount(
    val tagId: Long,
    val count: Int
)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY createdAt")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: MediaTagEntity)

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun unlink(mediaId: String, tagId: Long)

    @Query(
        "SELECT t.* FROM tags t INNER JOIN media_tags mt ON mt.tagId = t.id " +
            "WHERE mt.mediaId = :mediaId ORDER BY t.createdAt"
    )
    suspend fun tagsFor(mediaId: String): List<TagEntity>

    @Query(
        "SELECT m.* FROM media_items m INNER JOIN media_tags mt ON mt.mediaId = m.id " +
            "INNER JOIN libraries l ON l.id = m.libraryId " +
            "WHERE mt.tagId = :tagId AND m.isPresent = 1 AND l.isPrivate = 0 ORDER BY m.sortKey"
    )
    fun observeItemsWithTag(tagId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT tagId AS tagId, COUNT(*) AS count FROM media_tags GROUP BY tagId")
    suspend fun counts(): List<TagCount>

    @Upsert
    suspend fun upsert(tag: TagEntity)
}

@Dao
interface ProgressDao {

    @Query("SELECT * FROM reading_progress WHERE libraryId = :libraryId")
    suspend fun get(libraryId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE libraryId = :libraryId")
    fun observe(libraryId: String): Flow<ReadingProgressEntity?>

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE libraryId = :libraryId")
    suspend fun clear(libraryId: String)
}
