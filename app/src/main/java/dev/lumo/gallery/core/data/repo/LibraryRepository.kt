package dev.lumo.gallery.core.data.repo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import dev.lumo.gallery.core.data.db.LibraryEntity
import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.db.toEntity
import dev.lumo.gallery.core.data.db.toModel
import dev.lumo.gallery.core.data.scan.LibraryScanner
import dev.lumo.gallery.core.data.scan.ScanOutcome
import dev.lumo.gallery.core.data.scan.ScanProgressBus
import dev.lumo.gallery.core.data.source.FolderWalker
import dev.lumo.gallery.core.data.source.SafPermissions
import dev.lumo.gallery.core.archive.ArchiveEntryCache
import dev.lumo.gallery.core.archive.ArchiveSessionStore
import dev.lumo.gallery.core.archive.ArchiveStagingCache
import dev.lumo.gallery.core.image.ImageEnricher
import dev.lumo.gallery.core.image.ThumbCache
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.ArchiveFormat
import dev.lumo.gallery.core.model.LibraryType
import dev.lumo.gallery.core.model.ScanMode
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.SortMode
import dev.lumo.gallery.core.util.AppLog
import dev.lumo.gallery.core.util.MimeTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface GroupedImportResult {
    data class Created(
        val root: Library,
        val libraries: List<Library>,
        val folderLibraries: Int,
        val archiveLibraries: Int
    ) : GroupedImportResult

    data class AlreadyImported(val library: Library) : GroupedImportResult
    data object Empty : GroupedImportResult
    data class Failed(val message: String) : GroupedImportResult
}

sealed interface AddLibraryResult {
    data class Added(val library: Library) : AddLibraryResult
    data class Duplicate(val library: Library) : AddLibraryResult
    data class Failed(val message: String) : AddLibraryResult
}

class LibraryRepository(
    private val context: Context,
    private val db: LumoDatabase,
    private val permissions: SafPermissions,
    private val walker: FolderWalker,
    private val scanner: LibraryScanner,
    private val progressBus: ScanProgressBus,
    private val thumbCache: ThumbCache,
    private val enricher: ImageEnricher,
    private val appScope: CoroutineScope,
    private val archiveSessions: ArchiveSessionStore,
    private val archiveStaging: ArchiveStagingCache,
    private val archiveEntries: ArchiveEntryCache
) {

    fun observeVisible(): Flow<List<Library>> =
        db.libraryDao().observeVisibleRoots().map { list -> list.map(LibraryEntity::toModel) }

    fun observeAll(): Flow<List<Library>> =
        db.libraryDao().observeAll().map { list -> list.map(LibraryEntity::toModel) }

    fun observeChildren(parentId: String): Flow<List<Library>> =
        db.libraryDao().observeChildren(parentId).map { list -> list.map(LibraryEntity::toModel) }

    suspend fun getChildren(parentId: String): List<Library> =
        db.libraryDao().getChildren(parentId).map(LibraryEntity::toModel)

    fun observeChildCounts(): Flow<Map<String, Int>> =
        db.libraryDao().observeChildCounts().map { counts ->
            counts.associate { it.parentId to it.count }
        }

    fun observePrivate(): Flow<List<Library>> =
        db.libraryDao().observePrivate().map { list -> list.map(LibraryEntity::toModel) }

    fun observeById(id: String): Flow<Library?> =
        db.libraryDao().observeById(id).map { it?.toModel() }

    suspend fun get(id: String): Library? = db.libraryDao().getById(id)?.toModel()

    fun observeSearch(query: String): Flow<List<Library>> =
        db.libraryDao().search(MediaRepository.escapeLike(query)).map { list -> list.map(LibraryEntity::toModel) }

    fun scanProgress(libraryId: String): Flow<ScanProgress?> = progressBus.progressOf(libraryId)

    fun allScanProgress(): Flow<Map<String, ScanProgress>> = progressBus.states

    /**
     * 分组导入：把选中目录**按文件树镜像成层级图库**。
     * 例如选中 /ben，内部有 /ben/xxx/ 时会创建顶层图库 ben，
     * 打开 ben 后能看到子图库 xxx；每个节点只扫描自己的直接图片，不把子目录图片摊平到父级。
     */
    suspend fun addFolderGrouped(
        treeUri: Uri,
        onProgress: (String) -> Unit = {}
    ): GroupedImportResult = withContext(Dispatchers.IO) {
        permissions.takePersistable(treeUri, write = true)
        if (!permissions.hasReadPermission(treeUri)) {
            AppLog.e("Import", "分组导入拿不到持久化读权限: $treeUri")
            return@withContext GroupedImportResult.Failed("无法获得该文件夹的读取权限")
        }
        val treeKey = treeUri.toString()
        // 已有该文件夹的顶层图库（整体或分组）→ 明确提示“已导入”，而不是扫描不到
        db.libraryDao().findAnyTopFolderByUri(treeKey)?.let {
            AppLog.i("Import", "分组导入重复: $treeUri")
            return@withContext GroupedImportResult.AlreadyImported(it.toModel())
        }
        AppLog.i("Import", "开始分组导入: $treeUri")
        val rootName = queryTreeDisplayName(treeUri) ?: "文件夹"
        onProgress("正在分析目录结构…")
        val summary = try {
            walker.summarize(treeUri) { dirs -> onProgress("已扫描 $dirs 个目录…") }
        } catch (e: Exception) {
            AppLog.e("Import", "分组导入目录读取失败", e)
            return@withContext GroupedImportResult.Failed("目录读取失败：${e.message}")
        }

        val hasContent = summary.rootHasDirectImages ||
            summary.dirsWithDirectImages.isNotEmpty() ||
            summary.archives.isNotEmpty()
        if (!hasContent) return@withContext GroupedImportResult.Empty

        // 收集“需要建节点”的目录：含直接图片的目录 + 所有祖先目录（镜像层级）
        val relevant = LinkedHashSet<String>()
        fun addAncestors(relPath: String) {
            var path = relPath
            while (path.isNotEmpty()) {
                relevant.add(path)
                path = path.substringBeforeLast('/', "")
            }
        }
        summary.dirsWithDirectImages.forEach { addAncestors(it) }
        summary.archives.forEach { archive ->
            val parent = archive.relPath.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) addAncestors(parent)
        }

        val created = ArrayList<Library>()
        val idByRelPath = HashMap<String, String>()
        var stamp = System.currentTimeMillis()
        var folderCount = 1
        var archiveCount = 0

        val root = newFolderLibrary(
            treeUri = treeKey,
            relPath = null,
            name = rootName,
            parentId = null,
            scanMode = ScanMode.DIRECT,
            createdAt = stamp++
        )
        created.add(root)
        idByRelPath[""] = root.id

        val orderedDirs = relevant.sortedBy { path -> path.count { it == '/' } }
        for (relPath in orderedDirs) {
            if (idByRelPath.containsKey(relPath)) continue
            val parentRel = relPath.substringBeforeLast('/', "")
            val parentId = idByRelPath[parentRel] ?: root.id
            val node = newFolderLibrary(
                treeUri = treeKey,
                relPath = relPath,
                name = relPath.substringAfterLast('/'),
                parentId = parentId,
                scanMode = ScanMode.DIRECT,
                createdAt = stamp++
            )
            created.add(node)
            idByRelPath[relPath] = node.id
            folderCount++
        }

        for (child in summary.archives) {
            val format = child.archiveFormat
                ?.let { runCatching { ArchiveFormat.valueOf(it) }.getOrNull() }
                ?: continue
            val archiveUri = child.docUri.toString()
            if (db.libraryDao().findArchiveByUri(archiveUri) != null) continue
            val parentRel = child.relPath.substringBeforeLast('/', "")
            val parentId = idByRelPath[parentRel] ?: root.id
            created.add(
                Library(
                    id = UUID.randomUUID().toString(),
                    type = LibraryType.ARCHIVE,
                    name = child.name,
                    treeUri = treeKey,
                    treeRootRelPath = child.relPath,
                    parentId = parentId,
                    scanMode = ScanMode.RECURSIVE,
                    archiveUri = archiveUri,
                    archiveFormat = format,
                    displayPath = child.relPath,
                    isPrivate = false,
                    isAvailable = true,
                    sortMode = SortMode.NATURAL,
                    createdAt = stamp++,
                    lastScanAt = 0L,
                    coverItemId = null,
                    itemCount = 0,
                    scanStatus = ScanStatus.NEVER,
                    lastError = null
                )
            )
            archiveCount++
        }

        created.forEach { db.libraryDao().upsert(it.toEntity()) }
        AppLog.i(
            "Import",
            "分组导入完成: 目录节点=${summary.dirsWithDirectImages.size} 压缩包=${summary.archives.size} " +
                "新建=${created.size}（文件夹 $folderCount / 压缩包 $archiveCount）root=${root.name}"
        )
        GroupedImportResult.Created(root, created, folderCount, archiveCount)
    }

    private fun newFolderLibrary(
        treeUri: String,
        relPath: String?,
        name: String,
        parentId: String?,
        scanMode: ScanMode,
        createdAt: Long
    ): Library = Library(
        id = UUID.randomUUID().toString(),
        type = LibraryType.FOLDER,
        name = name,
        treeUri = treeUri,
        treeRootRelPath = relPath,
        parentId = parentId,
        scanMode = scanMode,
        archiveUri = null,
        archiveFormat = null,
        displayPath = relPath ?: name,
        isPrivate = false,
        isAvailable = true,
        sortMode = SortMode.NATURAL,
        createdAt = createdAt,
        lastScanAt = 0L,
        coverItemId = null,
        itemCount = 0,
        scanStatus = ScanStatus.NEVER,
        lastError = null
    )

    /** 分组导入后按“先深后浅”顺序后台扫描，子库先出封面，父级容器再回填封面。 */
    fun startBatchScan(libraryIds: List<String>, reason: String = "首次导入") {
        appScope.launch(Dispatchers.IO) {
            val ordered = libraryIds
                .mapNotNull { id ->
                    db.libraryDao().getById(id)?.let { entity -> id to (entity.treeRootRelPath?.count { it == '/' } ?: 0) }
                }
                .sortedByDescending { it.second }
                .map { it.first }
            ordered.forEach { scan(it, reason) }
        }
    }

    /**
     * 添加文件夹图库：申请持久化授权 → 去重 → 建库 → 首次扫描。
     */
    suspend fun addFolder(treeUri: Uri): AddLibraryResult {
        permissions.takePersistable(treeUri, write = true)
        if (!permissions.hasReadPermission(treeUri)) {
            AppLog.e("Import", "整体导入拿不到持久化读权限: $treeUri")
            return AddLibraryResult.Failed("无法获得该文件夹的读取权限")
        }
        AppLog.i("Import", "整体导入: $treeUri")
        db.libraryDao().findAnyTopFolderByUri(treeUri.toString())?.let {
            AppLog.i("Import", "整体导入重复: $treeUri")
            return AddLibraryResult.Duplicate(it.toModel())
        }
        val name = queryTreeDisplayName(treeUri) ?: treeUri.lastPathSegment?.substringAfterLast('/')
            ?: "未命名文件夹"
        val library = Library(
            id = UUID.randomUUID().toString(),
            type = LibraryType.FOLDER,
            name = name,
            treeUri = treeUri.toString(),
            treeRootRelPath = null,
            parentId = null,
            scanMode = ScanMode.RECURSIVE,
            archiveUri = null,
            archiveFormat = null,
            displayPath = name,
            isPrivate = false,
            isAvailable = true,
            sortMode = SortMode.NATURAL,
            createdAt = System.currentTimeMillis(),
            lastScanAt = 0L,
            coverItemId = null,
            itemCount = 0,
            scanStatus = ScanStatus.NEVER,
            lastError = null
        )
        db.libraryDao().upsert(library.toEntity())
        return AddLibraryResult.Added(library)
    }

    /**
     * 导入压缩包：建 ARCHIVE 图库并返回，扫描由调用方触发（可能先要密码）。
     */
    suspend fun addArchive(uri: Uri): AddLibraryResult {
        permissions.takePersistable(uri, write = false)
        if (!permissions.hasReadPermission(uri)) {
            AppLog.e("Import", "压缩包拿不到持久化读权限: $uri")
            return AddLibraryResult.Failed("无法读取该压缩包，请重新选择")
        }
        AppLog.i("Import", "导入压缩包: $uri")
        db.libraryDao().findArchiveByUri(uri.toString())?.let {
            return AddLibraryResult.Duplicate(it.toModel())
        }
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "压缩包"
        val format = MimeTypes.archiveFormatOf(name)?.let { raw ->
            runCatching { ArchiveFormat.valueOf(raw) }.getOrNull()
        } ?: return AddLibraryResult.Failed("只支持 ZIP / RAR / 7Z 压缩包")
        val library = Library(
            id = UUID.randomUUID().toString(),
            type = LibraryType.ARCHIVE,
            name = name,
            treeUri = null,
            archiveUri = uri.toString(),
            archiveFormat = format,
            displayPath = name,
            isPrivate = false,
            isAvailable = true,
            sortMode = SortMode.NATURAL,
            createdAt = System.currentTimeMillis(),
            lastScanAt = 0L,
            coverItemId = null,
            itemCount = 0,
            scanStatus = ScanStatus.NEVER,
            lastError = null
        )
        db.libraryDao().upsert(library.toEntity())
        return AddLibraryResult.Added(library)
    }

    /** 权限丢失后重新授权：更新 treeUri 并重扫。 */
    suspend fun reauthorize(libraryId: String, treeUri: Uri): Boolean {
        permissions.takePersistable(treeUri, write = true)
        if (!permissions.hasReadPermission(treeUri)) return false
        val entity = db.libraryDao().getById(libraryId) ?: return false
        db.libraryDao().upsert(
            entity.copy(
                treeUri = treeUri.toString(),
                isAvailable = true,
                scanStatus = ScanStatus.NEVER.name,
                lastError = null
            )
        )
        startScan(libraryId, "重新授权")
        return true
    }

    /** 用户在密码弹窗里输入密码后重试扫描。密码只保留在内存会话里。 */
    suspend fun provideArchivePassword(libraryId: String, password: String): Boolean {
        val trimmed = password
        if (trimmed.isEmpty()) return false
        archiveSessions.setPassword(libraryId, trimmed)
        val outcome = scan(libraryId, "密码重试")
        return outcome is ScanOutcome.Success
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun queryTreeDisplayName(treeUri: Uri): String? = runCatching {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** 扫描并自动触发缩略图/EXIF 富化。 */
    suspend fun scan(libraryId: String, reason: String = "手动刷新"): ScanOutcome {
        AppLog.i("Scan", "开始扫描 library=$libraryId reason=$reason")
        val outcome = scanner.scan(libraryId, reason)
        AppLog.i("Scan", "扫描结束 library=$libraryId reason=$reason outcome=$outcome")
        if (outcome is ScanOutcome.Success) {
            enricher.enqueue(libraryId)
            propagateCoverToAncestors(libraryId)
        }
        return outcome
    }

    /** 子图库扫描出封面后，向上回填“只有子库”的容器节点封面。 */
    private suspend fun propagateCoverToAncestors(startId: String) {
        var currentId = startId
        repeat(64) {
            val child = db.libraryDao().getById(currentId) ?: return
            val parentId = child.parentId ?: return
            val parent = db.libraryDao().getById(parentId) ?: return
            val cover = child.coverItemId ?: db.mediaDao().firstPresent(child.id)?.id
            if (parent.coverItemId == null && cover != null) {
                db.libraryDao().updateCover(parentId, cover)
            }
            currentId = parentId
        }
    }

    /**
     * 修复“库里标记 thumbReady 但缩略图文件已丢失/从未生成”的条目并重新富化。
     * 升级旧版本、清缓存、或上次解码失败后都会用到。
     */
    suspend fun ensureThumbnails(libraryId: String) {
        val ready = db.mediaDao().thumbReadyItems(libraryId)
        var reset = 0
        for (entity in ready) {
            if (thumbCache.existing(libraryId, entity.id, entity.lastModified) == null) {
                db.mediaDao().setThumbReady(entity.id, false)
                reset++
            }
        }
        if (reset > 0 || db.mediaDao().itemsNeedingThumbnailsInLibrary(libraryId, 1).isNotEmpty()) {
            enricher.enqueue(libraryId)
        }
    }

    /** 首页启动时修复所有图库缺失的缩略图（一次进程生命周期内调用一次）。 */
    fun ensureAllThumbnails() {
        appScope.launch(Dispatchers.IO) {
            runCatching { db.libraryDao().getAllOnce() }.getOrDefault(emptyList())
                .forEach { entity -> runCatching { ensureThumbnails(entity.id) } }
        }
    }

    /** 清空缩略图缓存后重建：重置标记并重新富化。 */
    suspend fun regenerateThumbnails(libraryId: String) {
        db.mediaDao().resetThumbReady(libraryId)
        enricher.enqueue(libraryId)
    }

    suspend fun regenerateAllThumbnails() {
        db.mediaDao().resetAllThumbReady()
        runCatching { db.libraryDao().getAllOnce() }.getOrDefault(emptyList())
            .forEach { enricher.enqueue(it.id) }
    }

    /** 后台启动一次扫描，不阻塞调用方。 */
    fun startScan(libraryId: String, reason: String = "手动刷新") {
        appScope.launch(Dispatchers.IO) { scan(libraryId, reason) }
    }

    @Volatile
    private var lastFullRefreshAt: Long = 0L

    /** 启动/回首页时按最小间隔做一次全量刷新，避免频繁触发。 */
    fun refreshAllIfStale(minIntervalMs: Long = 60_000L) {
        val now = System.currentTimeMillis()
        if (now - lastFullRefreshAt < minIntervalMs) return
        lastFullRefreshAt = now
        refreshAll()
    }

    /** 首页/启动时刷新所有可用图库（串行，避免 IO 抖动）。 */
    fun refreshAll(reason: String = "自动刷新") {
        appScope.launch(Dispatchers.IO) {
            val all = runCatching { db.libraryDao().getAllOnce() }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            all.filter { it.isAvailable && now - it.lastScanAt > 10 * 60_000L }.forEach { entity ->
                scan(entity.id, reason)
            }
        }
    }

    suspend fun rename(libraryId: String, newName: String) {
        val name = newName.trim().ifEmpty { return }
        db.libraryDao().rename(libraryId, name)
    }

    suspend fun setPrivate(libraryId: String, isPrivate: Boolean) {
        // 级联到子图库，保证进入子图库时同样走门禁
        db.libraryDao().setPrivate(libraryId, isPrivate)
        db.libraryDao().getChildren(libraryId).forEach { child ->
            setPrivate(child.id, isPrivate)
        }
    }

    suspend fun setSortMode(libraryId: String, sortMode: SortMode) {
        db.libraryDao().setSortMode(libraryId, sortMode.name)
    }

    suspend fun delete(libraryId: String) {
        val subtree = collectSubtreeIds(libraryId)
        subtree.forEach { enricher.cancel(it) }
        db.withTransaction {
            subtree.forEach { id ->
                db.mediaDao().deleteByLibrary(id)
                db.progressDao().clear(id)
                db.libraryDao().delete(id)
            }
        }
        subtree.forEach { id ->
            thumbCache.deleteLibrary(id)
            archiveSessions.close(id)
            archiveStaging.deleteLibrary(id)
            archiveEntries.deleteLibrary(id)
            progressBus.clear(id)
        }
    }

    /** 自身 + 所有后代图库 id（删除/私密级联用）。 */
    private suspend fun collectSubtreeIds(rootId: String): List<String> {
        val result = ArrayList<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            result.add(id)
            db.libraryDao().getChildren(id).forEach { queue.add(it.id) }
        }
        return result
    }
}
