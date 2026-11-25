package dev.lumo.gallery.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.AddLibraryResult
import dev.lumo.gallery.core.data.repo.GroupedImportResult
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

    val childCounts: StateFlow<Map<String, Int>> = libraries.observeChildCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val privateLibraries: StateFlow<List<Library>> = libraries.observePrivate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = media.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderLibraries: StateFlow<List<Library>> = libraries.observeFolderLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val applyToRealFiles: StateFlow<Boolean> = settings.settings
        .map { it.applyToRealFiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val hasPin: StateFlow<Boolean> = settings.settings
        .map { it.hasPin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        libraries.refreshAllIfStale()
        libraries.ensureAllThumbnails()
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

    fun setLibrariesFavorite(ids: Collection<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            libraries.setLibrariesFavorite(ids, favorite)
            _messages.emit(if (favorite) "已收藏 ${ids.size} 个图库" else "已取消收藏")
        }
    }

    fun moveLibraries(ids: Collection<String>, newParentId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = libraries.moveLibraries(
                libs = libraryList.value.filter { it.id in ids },
                newParentId = newParentId,
                applyToRealFiles = applyToRealFiles.value
            )
            _messages.emit(
                buildString {
                    append("已移动 ${result.logical + result.physical} 个图库")
                    if (result.physical > 0) append("（实际移动文件夹 ${result.physical}）")
                    if (result.failed > 0) append("，失败 ${result.failed}")
                }
            )
        }
    }

    fun deleteLibraries(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = libraries.deleteLibraries(
                libs = libraryList.value.filter { it.id in ids },
                applyToRealFiles = applyToRealFiles.value
            )
            _messages.emit(
                buildString {
                    append("已移除 ${ids.size} 个图库")
                    if (result.filesDeleted > 0) append("，实际删除文件夹 ${result.filesDeleted}")
                    if (result.indexOnly > 0) append("，${result.indexOnly} 个仅移除索引")
                }
            )
        }
    }

    fun onFolderPickedGrouped(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = libraries.addFolderGrouped(uri) { message ->
                        _importStatus.value = message
                    }) {
                    is GroupedImportResult.Created -> {
                        _messages.emit(
                            "已按目录结构创建 ${result.libraries.size} 个图库" +
                                "（文件夹 ${result.folderLibraries} / 压缩包 ${result.archiveLibraries}）"
                        )
                        _importStatus.value = "正在后台扫描 ${result.libraries.size} 个图库…"
                        libraries.startBatchScan(result.libraries.map { it.id })
                    }
                    is GroupedImportResult.AlreadyImported ->
                        _messages.emit("「${result.library.name}」已经导入过了；若是旧版整体导入，可先长按移除（不删源文件）再用分组导入")
                    GroupedImportResult.Empty ->
                        _messages.emit("没有发现图片或压缩包")
                    is GroupedImportResult.Failed -> _messages.emit(result.message)
                }
            } finally {
                _busy.value = false
                _importStatus.value = null
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

    fun toggleLibraryFavorite(library: Library) {
        viewModelScope.launch {
            libraries.setLibraryFavorite(library.id, !library.isFavorite)
        }
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
