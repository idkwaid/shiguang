package dev.lumo.gallery.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val treeUri: String?,
    /** 文件夹图库：相对 treeUri 根的子目录路径；null 表示 treeUri 本身就是图库根 */
    val treeRootRelPath: String? = null,
    /** 父图库 id：分组导入时镜像目录树；null = 顶层图库（首页展示） */
    val parentId: String? = null,
    /** 扫描模式：RECURSIVE=整体导入(递归)；DIRECT=分组节点(只扫直接子项) */
    val scanMode: String = "RECURSIVE",
    /** 图库卡片右下角的收藏（与图片收藏独立）。 */
    val isFavorite: Boolean = false,
    val archiveUri: String?,
    val archiveFormat: String?,
    val displayPath: String,
    val isPrivate: Boolean = false,
    val isAvailable: Boolean = true,
    val sortMode: String = "NATURAL",
    val createdAt: Long,
    val lastScanAt: Long = 0L,
    val coverItemId: String? = null,
    val itemCount: Int = 0,
    val scanStatus: String = "NEVER",
    val lastError: String? = null
)

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["libraryId", "locator"], unique = true),
        Index(value = ["libraryId", "sortKey"]),
        Index(value = ["isFavorite"]),
        Index(value = ["dateTaken"]),
        Index(value = ["isPresent"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val libraryId: String,
    val locator: String,
    val docUri: String?,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val dateTaken: Long?,
    val dateSource: String?,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val note: String?,
    val isFavorite: Boolean = false,
    val isPresent: Boolean = true,
    val thumbReady: Boolean = false,
    val phash: String?,
    val addedAt: Long,
    @ColumnInfo(name = "sortKey") val sortKey: String
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long
)

@Entity(
    tableName = "media_tags",
    primaryKeys = ["mediaId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId"), Index("mediaId")]
)
data class MediaTagEntity(
    val mediaId: String,
    val tagId: Long
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val libraryId: String,
    val mediaId: String,
    val position: Int,
    val updatedAt: Long
)
