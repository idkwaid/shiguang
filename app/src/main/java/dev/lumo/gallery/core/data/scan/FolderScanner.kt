package dev.lumo.gallery.core.data.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.source.FolderWalker
import dev.lumo.gallery.core.data.source.PermissionLostException
import dev.lumo.gallery.core.data.source.SafPermissions
import dev.lumo.gallery.core.util.AppLog
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanMode
import dev.lumo.gallery.core.model.ScanStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * 文件夹图库扫描：遍历 → 增量 diff（交给 [MediaIndexer]）→ 事务写库。
 */
class FolderScanner(
    @Suppress("unused") private val context: Context,
    private val db: LumoDatabase,
    private val walker: FolderWalker,
    private val permissions: SafPermissions,
    private val progressBus: ScanProgressBus,
    private val indexer: MediaIndexer,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : LibraryScanner {

    override suspend fun scan(libraryId: String, reason: String): ScanOutcome = withContext(io) {
        val library = db.libraryDao().getById(libraryId)
            ?: return@withContext ScanOutcome.Failure("图库不存在")
        val treeUri: Uri = library.treeUri?.let(Uri::parse)
            ?: return@withContext ScanOutcome.Failure("图库缺少目录地址")

        AppLog.d("Scan", "FolderScanner library=$libraryId tree=$treeUri rel=${library.treeRootRelPath}")
        if (!permissions.hasReadPermission(treeUri)) {
            AppLog.w("Scan", "FolderScanner 授权失效 library=$libraryId")
            progressBus.update(
                ScanProgress(libraryId, ScanProgress.Phase.FAILED, message = "目录授权已失效")
            )
            db.libraryDao().updateScanResult(
                id = libraryId,
                lastScanAt = library.lastScanAt,
                itemCount = library.itemCount,
                coverItemId = library.coverItemId,
                scanStatus = ScanStatus.PERMISSION_LOST.name,
                lastError = "目录授权已失效，请重新授权",
                isAvailable = false
            )
            return@withContext ScanOutcome.PermissionLost
        }

        progressBus.update(
            ScanProgress(libraryId, ScanProgress.Phase.WALKING, message = "正在扫描文件夹…")
        )

        val startDocId = library.treeRootRelPath?.let { rel ->
            walker.resolveDocId(treeUri, rel)
        }
        if (library.treeRootRelPath != null && startDocId == null) {
            db.libraryDao().updateScanResult(
                libraryId, library.lastScanAt, library.itemCount, library.coverItemId,
                ScanStatus.PERMISSION_LOST.name, "子目录已不存在或授权失效", false
            )
            progressBus.update(
                ScanProgress(libraryId, ScanProgress.Phase.FAILED, message = "子目录不可用")
            )
            return@withContext ScanOutcome.PermissionLost
        }

        val entries = try {
            walker.walk(
                treeUri = treeUri,
                startDocId = startDocId ?: DocumentsContract.getTreeDocumentId(treeUri),
                startRelPath = library.treeRootRelPath.orEmpty(),
                maxDepth = if (library.scanMode == ScanMode.DIRECT.name) 0 else Int.MAX_VALUE
            ) { scanned ->
                progressBus.update(
                    ScanProgress(
                        libraryId, ScanProgress.Phase.WALKING,
                        current = scanned, message = "已发现 $scanned 张图片"
                    )
                )
            }
        } catch (e: PermissionLostException) {
            AppLog.e("Scan", "FolderScanner 遍历失败 library=$libraryId", e)
            db.libraryDao().updateScanResult(
                libraryId, library.lastScanAt, library.itemCount, library.coverItemId,
                ScanStatus.PERMISSION_LOST.name, e.message, false
            )
            progressBus.update(
                ScanProgress(libraryId, ScanProgress.Phase.FAILED, message = "目录授权已失效")
            )
            return@withContext ScanOutcome.PermissionLost
        } catch (e: FileNotFoundException) {
            db.libraryDao().updateScanResult(
                libraryId, library.lastScanAt, library.itemCount, library.coverItemId,
                ScanStatus.ERROR.name, e.message, false
            )
            progressBus.update(
                ScanProgress(libraryId, ScanProgress.Phase.FAILED, message = "目录读取失败")
            )
            return@withContext ScanOutcome.Failure(e.message ?: "目录读取失败")
        }

        if (entries.isEmpty()) {
            indexer.markEmpty(libraryId)
            val children = db.libraryDao().getChildren(libraryId)
            val cover = library.coverItemId
                ?: children.firstOrNull { it.coverItemId != null }?.coverItemId
            val hasChildren = children.isNotEmpty()
            db.libraryDao().updateScanResult(
                id = libraryId,
                lastScanAt = System.currentTimeMillis(),
                itemCount = 0,
                coverItemId = cover,
                scanStatus = if (hasChildren) ScanStatus.OK.name else ScanStatus.EMPTY.name,
                lastError = null,
                isAvailable = true
            )
            progressBus.clear(libraryId)
            return@withContext if (hasChildren) ScanOutcome.Success(0) else ScanOutcome.Empty
        }

        progressBus.update(
            ScanProgress(
                libraryId, ScanProgress.Phase.INDEXING,
                current = 0, total = entries.size, message = "正在建立索引…"
            )
        )

        val result = indexer.index(libraryId, entries)
        if (library.scanMode == ScanMode.DIRECT.name) {
            // 修正历史错误数据：分组节点不应包含子目录图片
            val purged = db.mediaDao().deleteAbsentNonFavorite(libraryId)
            if (purged > 0) AppLog.i("Scan", "清理分组图库里残留的子目录图片 library=$libraryId count=$purged")
        }
        var cover = result.coverItemId ?: library.coverItemId
        if (cover != null && db.mediaDao().getById(cover)?.isPresent != true) cover = null
        if (cover == null) {
            cover = db.mediaDao().firstPresent(libraryId)?.id
                ?: db.libraryDao().getChildren(libraryId).firstOrNull { it.coverItemId != null }?.coverItemId
        }

        db.libraryDao().updateScanResult(
            id = libraryId,
            lastScanAt = System.currentTimeMillis(),
            itemCount = result.count,
            coverItemId = cover,
            scanStatus = ScanStatus.OK.name,
            lastError = null,
            isAvailable = true
        )

        progressBus.update(
            ScanProgress(
                libraryId, ScanProgress.Phase.DONE,
                current = result.count, total = result.count,
                message = "扫描完成：${result.count} 张"
            )
        )
        ScanOutcome.Success(result.count)
    }
}
