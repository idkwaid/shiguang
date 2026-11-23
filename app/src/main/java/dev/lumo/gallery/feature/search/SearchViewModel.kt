package dev.lumo.gallery.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.model.SearchResults
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val libraries: LibraryRepository,
    private val media: MediaRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isEmpty()) {
                flowOf(SearchResults())
            } else {
                combine(
                    libraries.observeSearch(q),
                    media.observeSearch(q),
                    media.observeTagSearch(q)
                ) { libs, items, tags ->
                    SearchResults(libraries = libs, items = items, tags = tags)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(value: String) {
        _query.value = value
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactoryOf {
            SearchViewModel(container.libraryRepository, container.mediaRepository)
        }
    }
}
