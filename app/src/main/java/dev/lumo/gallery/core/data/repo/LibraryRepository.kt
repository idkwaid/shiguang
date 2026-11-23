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
import dev.lumo.gallery.core.data.source.SafPermissions
import dev.lumo.gallery.core.archive.ArchiveEntryCache
import dev.lumo.gallery.core.archive.ArchiveSessionStore
import dev.lumo.gallery.core.archive.ArchiveStagingCache
import dev.lumo.gallery.core.image.ImageEnricher
import dev.lumo.gallery.core.image.ThumbCache
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.ArchiveFormat
import dev.lumo.gallery.core.model.LibraryType
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.SortMode
import dev.lumo.gallery.core.util.MimeTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AddLibraryResult {
    data class Added(val library: Library) : AddLibraryResult
    data class Duplicate(val library: Library) : AddLibraryResult
    data class Failed(val message: String) : AddLibraryResult
}

class LibraryRepository(
    private val context: Context,
    private val db: LumoDatabase,
    private val permissions: SafPermissions,
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
        db.libraryDao().observeVisible().map { list -> list.map(LibraryEntity::toModel) }

    fun observeAll(): Flow<List<Library>> =
        db.libraryDao().observeAll().map { list -> list.map(LibraryEntity::toModel) }

    fun observePrivate(): Flow<List<Library>> =
        db.libraryDao().observePrivate().map { list -> list.map(LibraryEntity::toModel) }

    fun observeById(id: String): Flow<Library?> =
        db.libraryDao().observeById(id).map { it?.toModel() }

    fun observeSearch(query: String): Flow<List<Library>> =
        db.libraryDao().search(MediaRepository.escapeLike(query)).map { list -> list.map(LibraryEntity::toModel) }

    suspend fun get(id: String): Library? = db.libraryDao().getById(id)?.toModel()

    fun scanProgress(libraryId: String): Flow<ScanProgress?> = progressBus.progressOf(libraryId)

    fun allScanProgress(): Flow<Map<String, ScanProgress>> = progressBus.states

    /**
     * 添加文件夹图库：申请持久化授权 → 去重 → 建库 → 首次扫描。
     */
    suspend fun addFolder(treeUri: Uri): AddLibraryResult {
        if (!permissions.hasReadPermission(treeUri)) {
            permissions.takePersistable(treeUri, write = true)
        }
        if (!permissions.hasReadPermission(treeUri)) {
            return AddLibraryResult.Failed("无法获得该文件夹的读取权限")
        }
        db.libraryDao().findFolderByUri(treeUri.toString())?.let {
            return AddLibraryResult.Duplicate(it.toModel())
        }
        val name = queryTreeDisplayName(treeUri) ?: treeUri.lastPathSegment?.substringAfterLast('/')
            ?: "未命名文件夹"
        val library = Library(
            id = UUID.randomUUID().toString(),
            type = LibraryType.FOLDER,
            name = name,
            treeUri = treeUri.toString(),
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
        if (!permissions.hasReadPermission(uri)) {
            permissions.takePersistable(uri, write = false)
        }
        if (!permissions.hasReadPermission(uri)) {
            return AddLibraryResult.Failed("无法读取该压缩包，请重新选择")
        }
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
        val outcome = scanner.scan(libraryId, reason)
        if (outcome is ScanOutcome.Success) enricher.enqueue(libraryId)
        return outcome
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
            all.filter { it.isAvailable }.forEach { entity ->
                scan(entity.id, reason)
            }
        }
    }

    suspend fun rename(libraryId: String, newName: String) {
        val name = newName.trim().ifEmpty { return }
        db.libraryDao().rename(libraryId, name)
    }

    suspend fun setPrivate(libraryId: String, isPrivate: Boolean) {
        db.libraryDao().setPrivate(libraryId, isPrivate)
    }

    suspend fun setSortMode(libraryId: String, sortMode: SortMode) {
        db.libraryDao().setSortMode(libraryId, sortMode.name)
    }

    suspend fun delete(libraryId: String) {
        enricher.cancel(libraryId)
        db.withTransaction {
            db.mediaDao().deleteByLibrary(libraryId)
            db.progressDao().clear(libraryId)
            db.libraryDao().delete(libraryId)
        }
        thumbCache.deleteLibrary(libraryId)
        archiveSessions.close(libraryId)
        archiveStaging.deleteLibrary(libraryId)
        archiveEntries.deleteLibrary(libraryId)
        progressBus.clear(libraryId)
    }
}
