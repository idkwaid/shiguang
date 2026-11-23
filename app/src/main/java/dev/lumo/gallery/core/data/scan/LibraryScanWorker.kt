package dev.lumo.gallery.core.data.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.lumo.gallery.LumoApp
import java.util.concurrent.TimeUnit

/**
 * 后台周期扫描：文件夹被外部修改时，即使 App 没打开也能在下次唤醒时收敛索引。
 * 周期 6 小时（WorkManager 最小 15 分钟）。
 */
class LibraryScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LumoApp).container
        val libraries = runCatching { container.database.libraryDao().getAllOnce() }
            .getOrDefault(emptyList())
        libraries.filter { it.isAvailable }.forEach { library ->
            runCatching { container.libraryRepository.scan(library.id, "后台刷新") }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "lumo-library-scan"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<LibraryScanWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
