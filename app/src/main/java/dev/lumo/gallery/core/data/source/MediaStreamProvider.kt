package dev.lumo.gallery.core.data.source

import android.content.Context
import android.net.Uri
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import java.io.InputStream

/**
 * 统一“按条目打开输入流”的能力：文件夹走 SAF 文档流，压缩包走解压流。
 * 缩略图、全屏查看、编辑、分享都依赖它。
 */
interface MediaStreamProvider {
    suspend fun open(library: Library, item: MediaItem): InputStream?
}

class FolderStreamProvider(
    @Suppress("unused") private val context: Context,
    private val walker: FolderWalker
) : MediaStreamProvider {

    override suspend fun open(library: Library, item: MediaItem): InputStream? {
        val tree = library.treeUri?.let(Uri::parse) ?: return null
        val direct = item.docUri?.let(Uri::parse)
        if (direct != null) {
            walker.openStream(direct)?.let { return it }
        }
        val resolved = walker.resolve(tree, item.locator) ?: return null
        return walker.openStream(resolved)
    }
}
