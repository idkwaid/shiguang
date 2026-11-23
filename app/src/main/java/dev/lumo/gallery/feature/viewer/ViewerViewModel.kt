package dev.lumo.gallery.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.image.Thumbnailer
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ViewerViewModel(
    private val libraryId: String,
    private val startItemId: String,
    private val libraries: LibraryRepository,
    private val media: MediaRepository,
    private val thumbnailer: Thumbnailer,
    private val streamProvider: MediaStreamProvider
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _startIndex = MutableStateFlow(0)
    val startIndex: StateFlow<Int> = _startIndex.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val previewCache = mutableMapOf<String, File?>()

    init {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { media.libraryItems(libraryId) }
            _items.value = list
            _startIndex.value = list.indexOfFirst { it.id == startItemId }.coerceAtLeast(0)
            _favoriteIds.value = list.filter { it.isFavorite }.map { it.id }.toSet()
            _loading.value = false
        }
    }

    fun toggleFavorite(item: MediaItem) {
        val nowFavorite = ! _favoriteIds.value.contains(item.id)
        _favoriteIds.value = _favoriteIds.value.toMutableSet().apply {
            if (nowFavorite) add(item.id) else remove(item.id)
        }
        viewModelScope.launch { media.setFavorite(item.id, nowFavorite) }
    }

    fun saveProgress(index: Int) {
        val item = _items.value.getOrNull(index) ?: return
        viewModelScope.launch { media.saveProgress(libraryId, item.id, index) }
    }

    suspend fun libraryOf(item: MediaItem): dev.lumo.gallery.core.model.Library? =
        libraries.get(item.libraryId)

    /** 1600px 预览图（M4 会换成真正按需解码的全尺寸图）。 */
    suspend fun preview(item: MediaItem): File? = withContext(Dispatchers.IO) {
        previewCache.getOrPut(item.id) {
            val library = libraries.get(item.libraryId) ?: return@withContext null
            thumbnailer.generate(
                library = library,
                item = item,
                provider = streamProvider,
                maxSize = 1600,
                kind = "previews"
            )?.file
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            libraryId: String,
            itemId: String
        ): ViewModelProvider.Factory = viewModelFactoryOf {
            ViewerViewModel(
                libraryId = libraryId,
                startItemId = itemId,
                libraries = container.libraryRepository,
                media = container.mediaRepository,
                thumbnailer = container.thumbnailer,
                streamProvider = container.streamProvider
            )
        }
    }
}
