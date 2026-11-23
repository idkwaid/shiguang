package dev.lumo.gallery.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.lumo.gallery.di.AppContainer

/** 全局依赖容器，UI 层用 LocalAppContainer.current 取仓储/缓存。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供：请在 AppRoot 用 CompositionLocalProvider 注入")
}
