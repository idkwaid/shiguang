package dev.lumo.gallery.core.data.scan

import dev.lumo.gallery.core.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 全局扫描进度：首页、图库页、压缩包导入弹窗共用。
 */
class ScanProgressBus {

    private val _states = MutableStateFlow<Map<String, ScanProgress>>(emptyMap())
    val states: StateFlow<Map<String, ScanProgress>> = _states.asStateFlow()

    fun update(progress: ScanProgress) {
        _states.value = _states.value.toMutableMap().apply { put(progress.libraryId, progress) }
    }

    fun clear(libraryId: String) {
        if (libraryId in _states.value) {
            _states.value = _states.value.toMutableMap().apply { remove(libraryId) }
        }
    }

    fun progressOf(libraryId: String): Flow<ScanProgress?> =
        states.map { it[libraryId] }.distinctUntilChanged()
}
