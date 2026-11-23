package dev.lumo.gallery.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import dev.lumo.gallery.core.archive.ArchiveEntryCache
import dev.lumo.gallery.core.archive.ArchiveReaderFactory
import dev.lumo.gallery.core.archive.ArchiveSessionStore
import dev.lumo.gallery.core.archive.ArchiveStagingCache
import dev.lumo.gallery.core.data.db.LumoDatabase
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.scan.ArchiveScanner
import dev.lumo.gallery.core.data.scan.FolderScanner
import dev.lumo.gallery.core.data.scan.MediaIndexer
import dev.lumo.gallery.core.data.scan.ScanCoordinator
import dev.lumo.gallery.core.data.scan.ScanProgressBus
import dev.lumo.gallery.core.data.source.ArchiveStreamProvider
import dev.lumo.gallery.core.data.source.CompositeStreamProvider
import dev.lumo.gallery.core.data.source.FolderStreamProvider
import dev.lumo.gallery.core.data.source.FolderWalker
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.data.source.SafPermissions
import dev.lumo.gallery.core.image.ImageEnricher
import dev.lumo.gallery.core.image.ThumbCache
import dev.lumo.gallery.core.image.Thumbnailer
import dev.lumo.gallery.core.model.LibraryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器（不用 Hilt，构建更快、AGP 兼容面更小）。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LumoDatabase = LumoDatabase.build(appContext)
    val permissions = SafPermissions(appContext)
    val walker = FolderWalker(appContext)
    val progressBus = ScanProgressBus()
    val thumbCache = ThumbCache(appContext)
    val thumbnailer = Thumbnailer(appContext, thumbCache)

    // 归档层
    private val archiveStaging = ArchiveStagingCache(appContext)
    private val archiveEntries = ArchiveEntryCache(appContext)
    private val archiveFactory = ArchiveReaderFactory(appContext, archiveStaging)
    private val archiveSessions = ArchiveSessionStore(archiveFactory)
    private val archiveStreamProvider = ArchiveStreamProvider(archiveSessions, archiveEntries)

    val streamProvider: MediaStreamProvider = CompositeStreamProvider(
        folder = FolderStreamProvider(appContext, walker),
        archive = archiveStreamProvider
    )

    val enricher = ImageEnricher(database, thumbnailer, streamProvider, appScope)

    private val indexer = MediaIndexer(database)
    private val folderScanner = FolderScanner(appContext, database, walker, permissions, progressBus, indexer)
    private val archiveScanner = ArchiveScanner(database, archiveSessions, progressBus, indexer)
    private val scanCoordinator = ScanCoordinator(
        database,
        mapOf(
            LibraryType.FOLDER to folderScanner,
            LibraryType.ARCHIVE to archiveScanner
        )
    )

    val libraryRepository = LibraryRepository(
        context = appContext,
        db = database,
        permissions = permissions,
        scanner = scanCoordinator,
        progressBus = progressBus,
        thumbCache = thumbCache,
        enricher = enricher,
        appScope = appScope,
        archiveSessions = archiveSessions,
        archiveStaging = archiveStaging,
        archiveEntries = archiveEntries
    )

    val mediaRepository = MediaRepository(database)

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(appContext, 0.15).build()
        }
        .build()
}
