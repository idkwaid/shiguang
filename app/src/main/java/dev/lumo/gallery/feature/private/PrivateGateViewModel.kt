package dev.lumo.gallery.feature.private

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.SettingsRepository
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.security.BiometricHelper
import dev.lumo.gallery.core.security.PinHasher
import dev.lumo.gallery.core.security.PrivateAccess
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrivateGateViewModel(
    private val libraries: LibraryRepository,
    private val settings: SettingsRepository,
    private val access: PrivateAccess
) : ViewModel() {

    val unlocked: StateFlow<Boolean> = access.unlocked

    val privateLibraries: StateFlow<List<Library>> = libraries.observePrivate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasPin: StateFlow<Boolean> = settings.settings
        .map { it.hasPin }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val biometricEnabled: StateFlow<Boolean> = settings.settings
        .map { it.biometricUnlock }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setPin(pin: String) {
        viewModelScope.launch {
            if (pin.length < 4) {
                _messages.emit("密码至少 4 位")
                return@launch
            }
            val salt = PinHasher.newSalt()
            val hash = PinHasher.hash(pin, salt)
            settings.setPin(hash, salt)
            access.unlock()
            _messages.emit("私密密码已设置")
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val current = settings.settings.first()
                val hash = current?.pinHash
                val salt = current?.pinSalt
                if (hash == null || salt == null) {
                    _messages.emit("还没有设置私密密码")
                    return@launch
                }
                if (PinHasher.verify(pin, salt, hash)) {
                    access.unlock()
                    access.resetFailures()
                    _messages.emit("已解锁")
                } else {
                    val cooldown = access.registerFailure()
                    _messages.emit(
                        if (cooldown > 0) "失败次数过多，请 ${cooldown} 秒后再试"
                        else "密码不正确"
                    )
                }
            } finally {
                _busy.value = false
            }
        }
    }

    suspend fun unlockWithBiometric(activity: FragmentActivity): Boolean {
        val ok = BiometricHelper.authenticate(activity)
        if (ok) access.unlock()
        return ok
    }

    fun lockNow() = access.lock()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactoryOf {
            PrivateGateViewModel(
                libraries = container.libraryRepository,
                settings = container.settingsRepository,
                access = container.privateAccess
            )
        }
    }
}
