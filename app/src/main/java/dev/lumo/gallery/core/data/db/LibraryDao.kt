package dev.lumo.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ParentChildCount(
    val parentId: String,
    val count: Int
)

@Dao
interface LibraryDao {

    @Query("SELECT * FROM libraries WHERE isPrivate = 0 AND parentId IS NULL ORDER BY createdAt DESC")
    fun observeVisible(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE isPrivate = 0 AND parentId IS NULL ORDER BY createdAt DESC")
    fun observeVisibleRoots(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE parentId = :parentId ORDER BY createdAt")
    fun observeChildren(parentId: String): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE parentId = :parentId ORDER BY createdAt")
    suspend fun getChildren(parentId: String): List<LibraryEntity>

    @Query("SELECT COUNT(*) FROM libraries WHERE parentId = :parentId")
    suspend fun countChildren(parentId: String): Int

    @Query("SELECT * FROM libraries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE id = :id")
    fun observeById(id: String): Flow<LibraryEntity?>

    @Query("SELECT * FROM libraries WHERE id = :id")
    suspend fun getById(id: String): LibraryEntity?

    @Query("SELECT * FROM libraries ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<LibraryEntity>

    @Query("SELECT * FROM libraries WHERE type = 'FOLDER' AND treeUri = :uri LIMIT 1")
    suspend fun findFolderByUri(uri: String): LibraryEntity?

    @Query("SELECT parentId AS parentId, COUNT(*) AS count FROM libraries WHERE parentId IS NOT NULL GROUP BY parentId")
    fun observeChildCounts(): Flow<List<ParentChildCount>>

    @Query("SELECT * FROM libraries WHERE type = 'ARCHIVE' AND archiveUri = :uri LIMIT 1")
    suspend fun findArchiveByUri(uri: String): LibraryEntity?

    @Query(
        "SELECT * FROM libraries WHERE type = 'FOLDER' AND treeUri = :uri " +
            "AND treeRootRelPath IS NULL AND parentId IS NULL LIMIT 1"
    )
    suspend fun findFolderRootByUri(uri: String): LibraryEntity?

    @Query(
        "SELECT * FROM libraries WHERE type = 'FOLDER' AND treeUri = :uri AND parentId IS NULL LIMIT 1"
    )
    suspend fun findAnyTopFolderByUri(uri: String): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE type = 'FOLDER' AND treeUri = :uri AND treeRootRelPath = :relPath LIMIT 1")
    suspend fun findFolderByRelPath(uri: String, relPath: String): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE isPrivate = 1 AND parentId IS NULL ORDER BY createdAt DESC")
    fun observePrivate(): Flow<List<LibraryEntity>>

    @Upsert
    suspend fun upsert(entity: LibraryEntity)

    @Query("DELETE FROM libraries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE libraries SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun setPrivate(id: String, isPrivate: Boolean)

    @Query("UPDATE libraries SET isPrivate = :isPrivate WHERE parentId = :parentId")
    suspend fun setChildrenPrivate(parentId: String, isPrivate: Boolean)

    @Query("UPDATE libraries SET coverItemId = :coverItemId WHERE id = :id")
    suspend fun updateCover(id: String, coverItemId: String?)

    @Query("UPDATE libraries SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE libraries SET sortMode = :sortMode WHERE id = :id")
    suspend fun setSortMode(id: String, sortMode: String)

    @Query(
        "UPDATE libraries SET lastScanAt = :lastScanAt, itemCount = :itemCount, " +
            "coverItemId = :coverItemId, scanStatus = :scanStatus, lastError = :lastError, " +
            "isAvailable = :isAvailable WHERE id = :id"
    )
    suspend fun updateScanResult(
        id: String,
        lastScanAt: Long,
        itemCount: Int,
        coverItemId: String?,
        scanStatus: String,
        lastError: String?,
        isAvailable: Boolean
    )

    @Query("SELECT * FROM libraries WHERE name LIKE '%' || :query || '%' ESCAPE '\\' AND isPrivate = 0 ORDER BY name")
    fun search(query: String): Flow<List<LibraryEntity>>
}
