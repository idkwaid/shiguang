package dev.lumo.gallery

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.SingletonImageLoader
import dev.lumo.gallery.core.data.scan.LibraryScanWorker
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
        LibraryScanWorker.schedule(this)
        // 私密空间：App 退到后台即上锁
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.privateAccess.lock()
            }
        })
    }

    companion object {
        lateinit var instance: LumoApp
            private set
    }
}
