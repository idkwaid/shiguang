package dev.lumo.gallery.core.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.lumo.gallery.core.model.ScannedEntry
import dev.lumo.gallery.core.util.MimeTypes
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PermissionLostException(message: String) : FileNotFoundException(message)

/** SAF 一层子项。 */
data class FolderChild(
    val name: String,
    val relPath: String,
    val isDir: Boolean,
    val docId: String,
    val docUri: Uri,
    val mime: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val archiveFormat: String? = null
)

/** 目录结构摘要：哪些目录直接含图片、哪些位置有压缩包。 */
data class FolderSummary(
    val rootHasDirectImages: Boolean,
    val dirsWithDirectImages: List<String>,
    val archives: List<FolderChild>
)

/**
 * 用 `DocumentsContract` 批量查询递归遍历 SAF 目录。
 * 支持“从子树根开始”（treeRootRelPath）与“先扫描结构、再决定建哪些图库”。
 */
class FolderWalker(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED
    )

    suspend fun walk(
        treeUri: Uri,
        startDocId: String = DocumentsContract.getTreeDocumentId(treeUri),
        startRelPath: String = "",
        onProgress: (scannedFiles: Int) -> Unit = {}
    ): List<ScannedEntry> = withContext(io) {
        val result = ArrayList<ScannedEntry>(128)
        val visitedDirs = HashSet<String>()
        var scanned = 0
        walkDir(
            treeUri = treeUri,
            dirDocId = startDocId,
            parentPath = startRelPath,
            depth = 0,
            out = result,
            visitedDirs = visitedDirs,
            onFile = { scanned++; if (scanned % 50 == 0) onProgress(scanned) }
        )
        onProgress(scanned)
        result
    }

    /**
     * 一次性扫描目录结构：记录“直接包含图片的目录”和所有压缩包。
     * 用于分组导入时决定要创建哪些图库。
     */
    suspend fun summarize(
        treeUri: Uri,
        onProgress: (scannedDirs: Int) -> Unit = {}
    ): FolderSummary = withContext(io) {
        val archives = ArrayList<FolderChild>()
        val dirsWithImages = ArrayList<String>()
        var rootHasImages = false
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(DocumentsContract.getTreeDocumentId(treeUri) to "")
        var dirsSeen = 0

        while (queue.isNotEmpty() && dirsSeen < MAX_DIRS) {
            currentCoroutineContext().ensureActive()
            val (docId, relPath) = queue.removeFirst()
            if (!visited.add(docId)) continue
            dirsSeen++
            if (dirsSeen % 20 == 0) onProgress(dirsSeen)

            val children = queryChildren(treeUri, docId, relPath)
            var hasDirectImage = false
            for (child in children) {
                if (child.isDir) {
                    queue.add(child.docId to child.relPath)
                } else if (MimeTypes.isImage(child.name, child.mime)) {
                    hasDirectImage = true
                } else if (child.archiveFormat != null) {
                    archives.add(child)
                }
            }
            if (hasDirectImage) {
                if (relPath.isEmpty()) rootHasImages = true else dirsWithImages.add(relPath)
            }
        }
        onProgress(dirsSeen)
        FolderSummary(rootHasImages, dirsWithImages, archives)
    }

    /** 把相对路径解析成当前有效的文档 ID（重新授权后可能变化）。 */
    suspend fun resolveDocId(treeUri: Uri, relPath: String): String? = withContext(io) {
        var current = DocumentsContract.getTreeDocumentId(treeUri)
        var walked = ""
        for (segment in relPath.split('/').filter { it.isNotBlank() }) {
            walked = if (walked.isEmpty()) segment else "$walked/$segment"
            val child = queryChildren(treeUri, current, walked)
                .firstOrNull { it.name == segment && it.isDir }
                ?: return@withContext null
            current = child.docId
        }
        current
    }

    /** 按 locator（相对路径）解析出当前有效的文件 URI；授权被重新选择后仍能兜底。 */
    suspend fun resolve(treeUri: Uri, locator: String): Uri? = withContext(io) {
        val parent = locator.substringBeforeLast('/', "")
        val name = locator.substringAfterLast('/')
        val parentId = resolveDocId(treeUri, parent) ?: return@withContext null
        queryChildren(treeUri, parentId, parent)
            .firstOrNull { it.name == name && !it.isDir }
            ?.docUri
    }

    fun openStream(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private suspend fun walkDir(
        treeUri: Uri,
        dirDocId: String,
        parentPath: String,
        depth: Int,
        out: MutableList<ScannedEntry>,
        visitedDirs: MutableSet<String>,
        onFile: () -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > MAX_DEPTH || !visitedDirs.add(dirDocId)) return

        for (child in queryChildren(treeUri, dirDocId, parentPath)) {
            currentCoroutineContext().ensureActive()
            if (child.isDir) {
                walkDir(treeUri, child.docId, child.relPath, depth + 1, out, visitedDirs, onFile)
            } else if (MimeTypes.isImage(child.name, child.mime)) {
                out.add(
                    ScannedEntry(
                        locator = child.relPath,
                        displayName = child.name,
                        mime = MimeTypes.guessMimeOf(child.name, child.mime),
                        sizeBytes = child.sizeBytes,
                        lastModified = child.lastModified,
                        docUri = child.docUri.toString()
                    )
                )
                onFile()
            }
        }
    }

    private fun queryChildren(treeUri: Uri, dirDocId: String, parentPath: String): List<FolderChild> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val cursor = try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        } catch (e: SecurityException) {
            throw PermissionLostException("目录授权已失效：${e.message}")
        }
        if (cursor == null) {
            // 部分 provider 不支持子查询：当作空目录而不是崩溃
            return emptyList()
        }
        return cursor.use { c ->
            val list = ArrayList<FolderChild>(c.count)
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: "application/octet-stream"
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4)
                val isDir = mime == Document.MIME_TYPE_DIR
                val relPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                list.add(
                    FolderChild(
                        name = name,
                        relPath = relPath,
                        isDir = isDir,
                        docId = docId,
                        docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        mime = mime,
                        sizeBytes = size,
                        lastModified = modified,
                        archiveFormat = if (isDir) null else MimeTypes.archiveFormatOf(name)
                    )
                )
            }
            list
        }
    }

    private companion object {
        const val MAX_DEPTH = 40
        const val MAX_DIRS = 20000
    }
}
