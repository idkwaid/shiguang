package dev.lumo.gallery.core.model

/**
 * 一个图库 = 一个文件夹，或一个压缩包（虚拟文件夹）。
 */
data class Library(
    val id: String,
    val type: LibraryType,
    val name: String,
    val treeUri: String?,
    /** 文件夹图库：相对 treeUri 根的子目录路径；null 表示 treeUri 本身就是图库根 */
    val treeRootRelPath: String? = null,
    /** 父图库 id：分组导入时镜像目录树；null = 顶层图库（首页展示） */
    val parentId: String? = null,
    /** 扫描模式：RECURSIVE=整体导入(递归)；DIRECT=分组节点(只扫直接子项) */
    val scanMode: ScanMode = ScanMode.RECURSIVE,
    val archiveUri: String?,
    val archiveFormat: ArchiveFormat?,
    val displayPath: String,
    val isPrivate: Boolean,
    val isAvailable: Boolean,
    val sortMode: SortMode,
    val createdAt: Long,
    val lastScanAt: Long,
    val coverItemId: String?,
    val itemCount: Int,
    val scanStatus: ScanStatus,
    val lastError: String?
) {
    val isArchive: Boolean get() = type == LibraryType.ARCHIVE
}

/**
 * 一张图片。id 由 (libraryId + locator) 哈希得到，locator 在文件夹里是相对路径，
 * 在压缩包里是条目路径。
 */
data class MediaItem(
    val id: String,
    val libraryId: String,
    val locator: String,
    val docUri: String?,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val dateTaken: Long?,
    val dateSource: DateSource?,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val note: String?,
    val isFavorite: Boolean,
    val isPresent: Boolean,
    val thumbReady: Boolean,
    val phash: String?,
    val addedAt: Long
) {
    val hasDimensions: Boolean get() = width > 0 && height > 0
}

/** 扫描产出的单条源条目，与具体来源（文件夹/压缩包）解耦。 */
data class ScannedEntry(
    val locator: String,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val docUri: String? = null,
    /** 压缩包条目的额外探测信息，文件夹来源为 null。 */
    val sourceExtra: String? = null
)

/** 扫描进度（供 UI 进度条展示）。 */
data class ScanProgress(
    val libraryId: String,
    val phase: Phase,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
) {
    enum class Phase { WALKING, INDEXING, DONE, FAILED }
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}
