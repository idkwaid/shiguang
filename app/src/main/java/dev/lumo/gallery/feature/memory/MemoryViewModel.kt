package dev.lumo.gallery.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 那年今日：按“月-日”匹配今天、年份更早的照片，按年份倒序分组。 */
class MemoryViewModel(private val media: MediaRepository) : ViewModel() {

    private val _groups = MutableStateFlow<List<Pair<Int, List<MediaItem>>>>(emptyList())
    val groups: StateFlow<List<Pair<Int, List<MediaItem>>>> = _groups.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val result = withContext(Dispatchers.IO) {
                val now = Calendar.getInstance()
                val monthDay = "%02d-%02d".format(
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH)
                )
                val currentYear = now.get(Calendar.YEAR)
                val calendar = Calendar.getInstance()
                media.datedItems()
                    .mapNotNull { item ->
                        val timestamp = item.dateTaken ?: return@mapNotNull null
                        calendar.timeInMillis = timestamp
                        val md = "%02d-%02d".format(
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        val year = calendar.get(Calendar.YEAR)
                        if (md == monthDay && year < currentYear) year to item else null
                    }
                    .groupBy({ it.first }, { it.second })
                    .toSortedMap(compareByDescending { it })
                    .map { (year, items) -> year to items }
            }
            _groups.value = result
            _loading.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactoryOf { MemoryViewModel(container.mediaRepository) }
    }
}

/** 全局随机回忆：每次抽 60 张，可换一批。 */
class RandomViewModel(private val media: MediaRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        shuffle()
    }

    fun shuffle() {
        viewModelScope.launch {
            _loading.value = true
            _items.value = withContext(Dispatchers.IO) { media.randomSample(60) }
            _loading.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactoryOf { RandomViewModel(container.mediaRepository) }
    }
}
