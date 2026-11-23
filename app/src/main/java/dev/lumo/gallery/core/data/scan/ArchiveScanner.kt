package dev.lumo.gallery.core.data.scan

import androidx.room.withTransaction
import dev.lumo.gallery.core.archive.ArchiveOpenException
import dev.lumo.gallery.core.archive.ArchivePasswordRequiredException
import dev.lumo.gallery.core.archive.ArchiveSessionStore
import dev.lumo.gallery.core.archive.CorruptArchiveException
import dev.lumo.gallery.core.archive.WrongArchivePasswordException
import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.db.toModel
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.ScannedEntry
import dev.lumo.gallery.core.util.MimeTypes
import dev.lumo.gallery.core.util.NaturalSortKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 压缩包图库扫描：列条目 → 过滤图片 → 平铺写入索引（按目录自然序 + 文件名自然序）。
 */
class ArchiveScanner(
    private val db: LumoDatabase,
    private val sessions: ArchiveSessionStore,
    private val progressBus: ScanProgressBus,
    private val indexer: MediaIndexer,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : LibraryScanner {

    override suspend fun scan(libraryId: String, reason: String): ScanOutcome = withContext(io) {
        val entity = db.libraryDao().getById(libraryId)
            ?: return@withContext ScanOutcome.Failure("图库不存在")
        val library = entity.toModel()

        progressBus.update(
            ScanProgress(libraryId, ScanProgress.Phase.WALKING, message = "正在读取压缩包…")
        )

        val reader = try {
            sessions.reader(library)
        } catch (e: ArchivePasswordRequiredException) {
            fail(libraryId, ScanStatus.PASSWORD_REQUIRED, "需要输入密码后继续")
            return@withContext ScanOutcome.Failure("需要密码")
        } catch (e: WrongArchivePasswordException) {
            sessions.forgetPassword(libraryId)
            fail(libraryId, ScanStatus.PASSWORD_REQUIRED, "密码不正确，请重新输入")
            return@withContext ScanOutcome.Failure("密码不正确")
        } catch (e: ArchiveOpenException) {
            val status = if (e is CorruptArchiveException) ScanStatus.CORRUPT else ScanStatus.ERROR
            fail(libraryId, status, e.message ?: "压缩包读取失败")
            return@withContext ScanOutcome.Failure(e.message ?: "压缩包读取失败")
        } catch (e: Exception) {
            fail(libraryId, ScanStatus.ERROR, "压缩包读取失败：${e.message}")
            return@withContext ScanOutcome.Failure("压缩包读取失败")
        }

        val allEntries = runCatching { reader.entries }.getOrElse { e ->
            fail(libraryId, ScanStatus.CORRUPT, "压缩包条目读取失败：${e.message}")
            return@withContext ScanOutcome.Failure("压缩包条目读取失败")
        }

        val images = allEntries.filter { MimeTypes.isImage(it.name, null) }
        progressBus.update(
            ScanProgress(
                libraryId, ScanProgress.Phase.INDEXING,
                current = 0, total = images.size,
                message = "发现 ${images.size} 张图片，正在建立索引…"
            )
        )

        if (images.isEmpty()) {
            indexer.markEmpty(libraryId)
            db.withTransaction {
                db.libraryDao().updateScanResult(
                    id = libraryId,
                    lastScanAt = System.currentTimeMillis(),
                    itemCount = 0,
                    coverItemId = null,
                    scanStatus = ScanStatus.EMPTY.name,
                    lastError = "压缩包里没有图片",
                    isAvailable = true
                )
            }
            progressBus.clear(libraryId)
            return@withContext ScanOutcome.Empty
        }

        val entries = images.map { entry ->
            ScannedEntry(
                locator = entry.path,
                displayName = entry.name,
                mime = MimeTypes.guessMimeOf(entry.name),
                sizeBytes = entry.sizeBytes,
                lastModified = entry.lastModified,
                docUri = null
            )
        }

        val result = indexer.index(
            libraryId = libraryId,
            entries = entries,
            sortKeyOf = { NaturalSortKey.ofPath(it.locator) }
        )

        db.libraryDao().updateScanResult(
            id = libraryId,
            lastScanAt = System.currentTimeMillis(),
            itemCount = result.count,
            coverItemId = result.coverItemId,
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

    private suspend fun fail(libraryId: String, status: ScanStatus, message: String) {
        progressBus.update(
            ScanProgress(libraryId, ScanProgress.Phase.FAILED, message = message)
        )
        val entity = db.libraryDao().getById(libraryId) ?: return
        db.libraryDao().updateScanResult(
            id = libraryId,
            lastScanAt = entity.lastScanAt,
            itemCount = entity.itemCount,
            coverItemId = entity.coverItemId,
            scanStatus = status.name,
            lastError = message,
            isAvailable = status != ScanStatus.PERMISSION_LOST
        )
    }
}
