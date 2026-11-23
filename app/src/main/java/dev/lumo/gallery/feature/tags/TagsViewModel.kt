package dev.lumo.gallery.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.Tag
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagWithCount(val tag: Tag, val count: Int)

class TagsViewModel(private val media: MediaRepository) : ViewModel() {

    val tags: StateFlow<List<TagWithCount>> = combine(
        media.observeTags(),
        media.observeTagCounts()
    ) { tags, counts ->
        val countMap = counts.associate { it.tagId to it.count }
        tags.map { TagWithCount(it, countMap[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun createTag(name: String, color: Int) {
        viewModelScope.launch {
            val id = media.createTag(name, color)
            _messages.emit(if (id > 0) "已创建标签" else "标签名为空或已存在")
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            media.deleteTag(tagId)
            _messages.emit("已删除标签")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactoryOf { TagsViewModel(container.mediaRepository) }
    }
}

class TagDetailViewModel(
    private val tagId: Long,
    private val media: MediaRepository
) : ViewModel() {

    val items: StateFlow<List<MediaItem>> = media.observeItemsWithTag(tagId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(container: AppContainer, tagId: Long): ViewModelProvider.Factory =
            viewModelFactoryOf { TagDetailViewModel(tagId, container.mediaRepository) }
    }
}
