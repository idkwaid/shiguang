package dev.lumo.gallery.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.image.PHash
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanupState(
    val working: Boolean = false,
    val phase: String = "",
    val exactGroups: List<List<MediaItem>> = emptyList(),
    val similarGroups: List<List<MediaItem>> = emptyList(),
    val gifs: List<MediaItem> = emptyList(),
    val heics: List<MediaItem> = emptyList()
)

/**
 * 智能清理：精确重复（size 分桶 + SHA-256）、相似图（pHash 分带 + 并查集）、按格式筛选。
 */
class CleanupViewModel(
    private val media: MediaRepository,
    private val libraries: LibraryRepository,
    private val provider: MediaStreamProvider
) : ViewModel() {

    private val _state = MutableStateFlow(CleanupState())
    val state: StateFlow<CleanupState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun scanAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, phase = "正在查找重复图片…")
            val exact = withContext(Dispatchers.IO) { findExactDuplicates() }
            _state.value = _state.value.copy(
                phase = "正在分析相似图片…",
                exactGroups = exact
            )
            val similar = withContext(Dispatchers.IO) { findSimilar() }
            _state.value = _state.value.copy(
                phase = "正在按格式筛选…",
                similarGroups = similar
            )
            val gifs = withContext(Dispatchers.IO) { media.itemsByMime("image/gif%") }
            val heics = withContext(Dispatchers.IO) { media.itemsByMime("image/heic%") }
            _state.value = CleanupState(
                working = false,
                exactGroups = exact,
                similarGroups = similar,
                gifs = gifs,
                heics = heics
            )
        }
    }

    private suspend fun findExactDuplicates(): List<List<MediaItem>> {
        val result = mutableListOf<List<MediaItem>>()
        for (group in media.duplicateSizeGroups()) {
            val items = media.itemsBySize(group.sizeBytes)
            if (items.size < 2) continue
            val byHash = mutableMapOf<String, MutableList<MediaItem>>()
            for (item in items) {
                val hash = sha256Of(item) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(item)
            }
            byHash.values.filter { it.size > 1 }.forEach { result.add(it) }
            if (result.size >= 80) break
        }
        return result
    }

    private suspend fun findSimilar(): List<List<MediaItem>> {
        val items = media.itemsWithPhash().mapNotNull { item ->
            PHash.fromHex(item.phash)?.let { item to it }
        }
        if (items.size < 2) return emptyList()

        val parent = IntArray(items.size) { it }
        fun find(x: Int): Int {
            var index = x
            while (parent[index] != index) {
                parent[index] = parent[parent[index]]
                index = parent[index]
            }
            return index
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        // 按 4 个 16-bit 分带取候选，避免 O(n^2)
        val bands = HashMap<Int, MutableList<Int>>(items.size * 4)
        items.forEachIndexed { index, (_, hash) ->
            for (band in 0 until 4) {
                val key = ((hash ushr (band * 16)) and 0xFFFF).toInt()
                bands.getOrPut(key) { mutableListOf() }.add(index)
            }
        }
        for (bucket in bands.values) {
            if (bucket.size < 2) continue
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    if (find(a) != find(b) && PHash.hamming(items[a].second, items[b].second) <= PHash.DEFAULT_THRESHOLD) {
                        union(a, b)
                    }
                }
            }
        }

        return items.indices
            .groupBy { find(it) }
            .values
            .filter { it.size > 1 }
            .map { indexes -> indexes.map { items[it].first } }
            .sortedByDescending { it.size }
            .take(80)
    }

    private suspend fun sha256Of(item: MediaItem): String? {
        val library = libraries.get(item.libraryId) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                provider.open(library, item)?.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                } ?: return@runCatching null
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
    }

    fun deleteItems(items: List<MediaItem>, keepFirst: Boolean = true) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val targets = if (keepFirst) items.drop(1) else items
            var deleted = 0
            var indexOnly = 0
            for (item in targets) {
                val library = libraries.get(item.libraryId)
                val isFolder = library?.type == dev.lumo.gallery.core.model.LibraryType.FOLDER
                if (isFolder) {
                    if (media.deleteItem(item, deleteFromDisk = true)) deleted++ else indexOnly++
                } else {
                    media.removeFromIndex(item)
                    indexOnly++
                }
            }
            _messages.emit(
                if (deleted > 0) "已删除 $deleted 个文件" + (if (indexOnly > 0) "，$indexOnly 项仅移出索引" else "")
                else "已移出索引 $indexOnly 项（压缩包内文件无法单独删除）"
            )
            scanAll()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactoryOf {
            CleanupViewModel(
                media = container.mediaRepository,
                libraries = container.libraryRepository,
                provider = container.streamProvider
            )
        }
    }
}
