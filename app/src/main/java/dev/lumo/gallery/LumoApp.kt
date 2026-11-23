package dev.lumo.gallery

import android.app.Application
import coil3.SingletonImageLoader
import dev.lumo.gallery.di.AppContainer

/**
 * 应用入口：初始化依赖容器与全局图片加载器。
 */
class LumoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        SingletonImageLoader.setUnsafe(container.imageLoader)
    }

    companion object {
        lateinit var instance: LumoApp
            private set
    }
}
