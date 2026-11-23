package dev.lumo.gallery.core.data.scan

import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.model.LibraryType

/** 扫描器抽象：文件夹与压缩包各自实现，由 [ScanCoordinator] 按图库类型派发。 */
interface LibraryScanner {
    suspend fun scan(libraryId: String, reason: String = "手动刷新"): ScanOutcome
}

class ScanCoordinator(
    private val db: LumoDatabase,
    private val scanners: Map<LibraryType, LibraryScanner>
) : LibraryScanner {

    override suspend fun scan(libraryId: String, reason: String): ScanOutcome {
        val library = db.libraryDao().getById(libraryId)
            ?: return ScanOutcome.Failure("图库不存在")
        val type = runCatching { LibraryType.valueOf(library.type) }.getOrDefault(LibraryType.FOLDER)
        val scanner = scanners[type] ?: return ScanOutcome.Failure("暂不支持该图库类型")
        return scanner.scan(libraryId, reason)
    }
}
