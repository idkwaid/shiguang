package dev.lumo.gallery.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val media: MediaRepository,
    private val libraries: LibraryRepository
) : ViewModel() {

    val items: StateFlow<List<MediaItem>> = media.observeFavoriteItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteLibraries: StateFlow<List<Library>> = libraries.observeFavoriteLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryCovers: StateFlow<Map<String, MediaItem>> = libraries.observeFavoriteLibraries()
        .map { list -> list.mapNotNull { it.coverItemId } }
        .distinctUntilChanged()
        .mapLatest { ids ->
            if (ids.isEmpty()) emptyMap()
            else media.itemsByIds(ids).associateBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val absentCount: StateFlow<Int> = items
        .map { list -> list.count { !it.isPresent } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            media.setFavorite(item.id, false)
            _messages.emit("已取消收藏")
        }
    }

    fun removeLibraryFavorite(library: Library) {
        viewModelScope.launch {
            libraries.setLibraryFavorite(library.id, false)
            _messages.emit("已取消收藏图库")
        }
    }

    fun clearAbsent() {
        viewModelScope.launch {
            val removed = media.clearAbsentFavorites()
            _messages.emit(if (removed > 0) "已清理 $removed 条失效收藏" else "没有可清理的失效收藏")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactoryOf {
            FavoritesViewModel(container.mediaRepository, container.libraryRepository)
        }
    }
}
