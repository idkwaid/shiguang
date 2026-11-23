package dev.lumo.gallery.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** 手写 DI 下的 ViewModel 工厂便捷函数。 */
inline fun <reified T : ViewModel> viewModelFactoryOf(
    crossinline create: () -> T
): ViewModelProvider.Factory = viewModelFactory { initializer { create() } }
