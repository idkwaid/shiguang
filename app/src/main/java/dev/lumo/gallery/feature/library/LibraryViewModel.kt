package dev.lumo.gallery.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContinueInfo(
    val index: Int,
    val total: Int
)

class LibraryViewModel(
    private val libraryId: String,
    private val libraries: LibraryRepository,
    private val media: MediaRepository
) : ViewModel() {

    val library: StateFlow<Library?> = libraries.observeById(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<MediaItem>> = media.observeLibraryItems(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanProgress: StateFlow<ScanProgress?> = libraries.scanProgress(libraryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            libraries.ensureThumbnails(libraryId)
            val current = libraries.get(libraryId)
            if (current != null && current.lastScanAt == 0L) {
                libraries.scan(libraryId, "首次打开")
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
                media = container.mediaRepository
            )
        }
    }
}
