package dev.lumo.gallery.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.repo.SettingsRepository
import dev.lumo.gallery.core.data.scan.ScanOutcome
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanProgress
import android.net.Uri
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContinueInfo(
    val index: Int,
    val total: Int
)

class LibraryViewModel(
    private val libraryId: String,
    private val libraries: LibraryRepository,
    private val media: MediaRepository,
    private val containerSettings: SettingsRepository
) : ViewModel() {

    val library: StateFlow<Library?> = libraries.observeById(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<MediaItem>> = media.observeLibraryItems(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanProgress: StateFlow<ScanProgress?> = libraries.scanProgress(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val children: StateFlow<List<Library>> = libraries.observeChildren(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val childCovers: StateFlow<Map<String, MediaItem>> = libraries.observeChildren(libraryId)
        .map { list -> list.mapNotNull { it.coverItemId } }
        .distinctUntilChanged()
        .mapLatest { ids ->
            if (ids.isEmpty()) emptyMap()
            else media.itemsByIds(ids).associateBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val continueInfo: StateFlow<ContinueInfo?> = combine(
        items,
        media.observeProgress(libraryId)
    ) { list, progress ->
        if (progress == null || list.isEmpty()) return@combine null
        val index = list.indexOfFirst { it.id == progress.mediaId }
        if (index <= 0) null else ContinueInfo(index = index, total = list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            libraries.ensureThumbnails(libraryId, prioritize = true)
            val current = libraries.get(libraryId)
            if (current != null && current.lastScanAt == 0L) {
                libraries.scan(libraryId, "首次打开")
            } else if (current != null && current.scanMode == dev.lumo.gallery.core.model.ScanMode.DIRECT &&
                System.currentTimeMillis() - current.lastScanAt > 30_000L
            ) {
                // 打开分组节点时校准一次，修复旧版本可能残留的“父库混入子目录图片”
                libraries.startScan(libraryId, "打开时校准")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val outcome = libraries.scan(libraryId, "下拉刷新")) {
                is ScanOutcome.Success -> _messages.emit("扫描完成：${outcome.itemCount} 张")
                ScanOutcome.Empty -> _messages.emit("这个文件夹里还没有图片")
                ScanOutcome.PermissionLost -> _messages.emit("目录授权已失效，请重新添加文件夹")
                is ScanOutcome.Failure -> _messages.emit(outcome.message)
            }
        }
    }

    @Volatile
    private var lastAutoScanAt: Long = 0L

    @Volatile
    private var lastChangeAt: Long = 0L

    /** 回到前台/文件夹变化时按节流时间自动增量扫描。 */
    fun refreshIfStale(minIntervalMs: Long = 60_000L) {
        val now = System.currentTimeMillis()
        if (now - lastAutoScanAt < minIntervalMs) return
        lastAutoScanAt = now
        viewModelScope.launch { libraries.scan(libraryId, "自动刷新") }
    }

    /** ContentObserver 回调可能非常密集，合并成一次扫描。 */
    fun onFolderChanged() {
        val now = System.currentTimeMillis()
        if (now - lastChangeAt < 1_500L) return
        lastChangeAt = now
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            libraries.scan(libraryId, "文件夹变化")
        }
    }

    fun reauthorize(treeUri: Uri) {
        viewModelScope.launch {
            val ok = libraries.reauthorize(libraryId, treeUri)
            if (!ok) _messages.emit("重新授权失败")
        }
    }

    val folderLibraries: StateFlow<List<Library>> = libraries.observeFolderLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val applyToRealFiles: StateFlow<Boolean> = containerSettings.settings
        .map { it.applyToRealFiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setImagesFavorite(ids: Collection<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            libraries.setImagesFavorite(ids, favorite)
            _messages.emit(if (favorite) "已收藏 ${ids.size} 张图片" else "已取消收藏")
        }
    }

    fun setLibrariesFavorite(ids: Collection<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            libraries.setLibrariesFavorite(ids, favorite)
            _messages.emit(if (favorite) "已收藏 ${ids.size} 个图库" else "已取消收藏")
        }
    }

    fun moveImages(ids: Collection<String>, targetLibraryId: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = items.value.filter { it.id in ids }
            val result = libraries.moveImages(items, targetLibraryId, applyToRealFiles.value)
            _messages.emit(
                buildString {
                    append("已移动 ${result.physical + result.logical} 张图片")
                    if (result.physical > 0) append("（实际移动文件 ${result.physical}）")
                    if (result.logical > 0) append("（仅调整归属 ${result.logical}）")
                    if (result.failed > 0) append("，失败 ${result.failed}")
                }
            )
        }
    }

    fun moveLibraries(ids: Collection<String>, newParentId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val libs = children.value.filter { it.id in ids }
            val result = libraries.moveLibraries(libs, newParentId, applyToRealFiles.value)
            _messages.emit(
                buildString {
                    append("已移动 ${result.physical + result.logical} 个图库")
                    if (result.physical > 0) append("（实际移动文件夹 ${result.physical}）")
                    if (result.failed > 0) append("，失败 ${result.failed}")
                }
            )
        }
    }

    fun deleteImages(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val selected = items.value.filter { it.id in ids }
            val result = libraries.deleteImages(selected, applyToRealFiles.value)
            _messages.emit(
                buildString {
                    append("已移除 ${ids.size} 张图片")
                    if (result.filesDeleted > 0) append("，实际删除文件 ${result.filesDeleted}")
                    if (result.indexOnly > 0) append("，${result.indexOnly} 张仅移除索引")
                }
            )
        }
    }

    fun deleteLibraries(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val selected = children.value.filter { it.id in ids }
            val result = libraries.deleteLibraries(selected, applyToRealFiles.value)
            _messages.emit(
                buildString {
                    append("已移除 ${ids.size} 个图库")
                    if (result.filesDeleted > 0) append("，实际删除文件夹 ${result.filesDeleted}")
                    if (result.indexOnly > 0) append("，${result.indexOnly} 个仅移除索引")
                }
            )
        }
    }

    fun toggleLibraryFavorite(library: Library) {
        viewModelScope.launch { libraries.setLibraryFavorite(library.id, !library.isFavorite) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch { media.setFavorite(item.id, !item.isFavorite) }
    }

    fun deleteItem(item: MediaItem, deleteFromDisk: Boolean) {
        viewModelScope.launch {
            val deleted = media.deleteItem(item, deleteFromDisk)
            _messages.emit(
                if (deleteFromDisk && deleted) "已删除文件"
                else if (deleteFromDisk) "源文件删除失败，已移出索引"
                else "已移出索引（压缩包内文件不会改包）"
            )
        }
    }

    fun setNote(item: MediaItem, note: String) {
        viewModelScope.launch { media.setNote(item.id, note) }
    }

    fun regenerateThumbnails() {
        viewModelScope.launch {
            libraries.regenerateThumbnails(libraryId)
            _messages.emit("正在重新生成缩略图…")
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            val ok = libraries.provideArchivePassword(libraryId, password)
            if (!ok) _messages.emit("密码不对，或压缩包读取失败")
        }
    }

    fun saveProgress(itemId: String, index: Int) {
        viewModelScope.launch { media.saveProgress(libraryId, itemId, index) }
    }

    fun clearProgress() {
        viewModelScope.launch { media.clearProgress(libraryId) }
    }

    fun deleteLibraryTree(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = libraries.get(libraryId) ?: return@launch
            val result = libraries.deleteLibraries(listOf(current), applyToRealFiles.value)
            _messages.emit(
                buildString {
                    append("已移除「${current.name}」")
                    if (result.filesDeleted > 0) append("，实际删除文件夹 ${result.filesDeleted}")
                    if (result.indexOnly > 0) append("，仅移除索引")
                }
            )
            onDone()
        }
    }

    fun removeLibrary(onDone: () -> Unit) {
        viewModelScope.launch {
            libraries.delete(libraryId)
            onDone()
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            libraryId: String
        ): ViewModelProvider.Factory = viewModelFactoryOf {
            LibraryViewModel(
                libraryId = libraryId,
                libraries = container.libraryRepository,
                media = container.mediaRepository,
                containerSettings = container.settingsRepository
            )
        }
    }
}
