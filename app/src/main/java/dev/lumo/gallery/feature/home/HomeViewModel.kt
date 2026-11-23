package dev.lumo.gallery.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.AddLibraryResult
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.repo.SettingsRepository
import dev.lumo.gallery.core.data.scan.ScanProgressBus
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.Tag
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val libraries: LibraryRepository,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
    progressBus: ScanProgressBus
) : ViewModel() {

    val libraryList: StateFlow<List<Library>> = libraries.observeVisible()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 封面缩略图：图库 coverItemId → item。 */
    val covers: StateFlow<Map<String, MediaItem>> = libraries.observeVisible()
        .map { list -> list.mapNotNull { it.coverItemId } }
        .distinctUntilChanged()
        .mapLatest { ids ->
            if (ids.isEmpty()) emptyMap()
            else media.itemsByIds(ids).associateBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val scanStates: StateFlow<Map<String, ScanProgress>> = progressBus.states

    val privateLibraries: StateFlow<List<Library>> = libraries.observePrivate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = media.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasPin: StateFlow<Boolean> = settings.settings
        .map { it.hasPin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        libraries.refreshAllIfStale()
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = libraries.addFolder(uri)) {
                    is AddLibraryResult.Added -> {
                        _messages.emit("已添加「${result.library.name}」，开始扫描…")
                        libraries.startScan(result.library.id, "首次导入")
                    }
                    is AddLibraryResult.Duplicate ->
                        _messages.emit("「${result.library.name}」已经在图库里了")
                    is AddLibraryResult.Failed -> _messages.emit(result.message)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun onArchivePicked(uri: Uri, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = libraries.addArchive(uri)) {
                    is AddLibraryResult.Added -> {
                        _messages.emit("已导入「${result.library.name}」，开始扫描…")
                        onAdded(result.library.id)
                        libraries.startScan(result.library.id, "首次导入")
                    }
                    is AddLibraryResult.Duplicate -> {
                        _messages.emit("「${result.library.name}」已经在图库里了")
                        onAdded(result.library.id)
                    }
                    is AddLibraryResult.Failed -> _messages.emit(result.message)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun rescan(libraryId: String) {
        libraries.startScan(libraryId)
        viewModelScope.launch { _messages.emit("正在重新扫描…") }
    }

    fun setPrivate(library: Library, enable: Boolean) {
        viewModelScope.launch {
            if (enable && !hasPin.value) {
                _messages.emit("请先在「设置」里设置私密密码")
                return@launch
            }
            libraries.setPrivate(library.id, enable)
            _messages.emit(if (enable) "已隐藏「${library.name}」到私密空间" else "已取消私密")
        }
    }

    fun rename(libraryId: String, name: String) {
        viewModelScope.launch { libraries.rename(libraryId, name) }
    }

    fun delete(libraryId: String) {
        viewModelScope.launch {
            libraries.delete(libraryId)
            _messages.emit("已从图库移除（不会删除源文件）")
        }
    }

    companion object {
        fun factory(container: AppContainer): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactoryOf {
                HomeViewModel(
                    libraries = container.libraryRepository,
                    media = container.mediaRepository,
                    settings = container.settingsRepository,
                    progressBus = container.progressBus
                )
            }
    }
}
