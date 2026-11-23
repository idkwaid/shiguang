package dev.lumo.gallery.core.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.lumo.gallery.core.model.ScannedEntry
import dev.lumo.gallery.core.util.MimeTypes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import java.io.FileNotFoundException
import java.io.InputStream

class PermissionLostException(message: String) : FileNotFoundException(message)

/**
 * 用 `DocumentsContract` 批量查询来递归遍历 SAF 目录。
 * 比 DocumentFile 的逐文件查询快一个量级（一次 query 拿到一层目录）。
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
        onProgress: (scannedFiles: Int) -> Unit = {}
    ): List<ScannedEntry> = withContext(io) {
        val result = ArrayList<ScannedEntry>(128)
        val visitedDirs = HashSet<String>()
        var scanned = 0
        walkDir(
            treeUri = treeUri,
            dirDocId = DocumentsContract.getTreeDocumentId(treeUri),
            parentPath = "",
            depth = 0,
            out = result,
            visitedDirs = visitedDirs,
            onFile = { scanned++; if (scanned % 50 == 0) onProgress(scanned) }
        )
        onProgress(scanned)
        result
    }

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
        if (depth > 40 || !visitedDirs.add(dirDocId)) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val cursor = try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        } catch (e: SecurityException) {
            throw PermissionLostException("目录授权已失效：$e")
        } finally {
            // 游标在下方 use 中关闭
        } ?: throw PermissionLostException("无法读取目录：$childrenUri")

        try {
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: "application/octet-stream"
                val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                val lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                val locator = if (parentPath.isEmpty()) name else "$parentPath/$name"

                if (mime == Document.MIME_TYPE_DIR) {
                    walkDir(treeUri, docId, locator, depth + 1, out, visitedDirs, onFile)
                } else if (MimeTypes.isImage(name, mime)) {
                    out += ScannedEntry(
                        locator = locator,
                        displayName = name,
                        mime = MimeTypes.guessMimeOf(name, mime),
                        sizeBytes = size,
                        lastModified = lastModified,
                        docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                    )
                    onFile()
                }
            }
        } finally {
            cursor.close()
        }
    }

    /** 按 locator（相对路径）解析出当前有效的文档 URI；授权被重新选择后仍能兜底。 */
    suspend fun resolve(treeUri: Uri, locator: String): Uri? = withContext(io) {
        var currentTreeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val segments = locator.split('/')
        segments.forEachIndexed { index, segment ->
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentTreeDocId)
            var found: Pair<String, Boolean>? = null
            context.contentResolver.query(
                childrenUri,
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == segment) {
                        found = cursor.getString(0) to
                            (cursor.getString(2) == Document.MIME_TYPE_DIR)
                        break
                    }
                }
            }
            val hit = found ?: return@withContext null
            if (index == segments.lastIndex) {
                if (hit.second) return@withContext null
                return@withContext DocumentsContract.buildDocumentUriUsingTree(treeUri, hit.first)
            }
            if (!hit.second) return@withContext null
            currentTreeDocId = hit.first
        }
        null
    }

    fun openStream(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
}
