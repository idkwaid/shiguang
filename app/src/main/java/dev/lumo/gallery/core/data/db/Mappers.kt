package dev.lumo.gallery.core.data.db

import dev.lumo.gallery.core.model.ArchiveFormat
import dev.lumo.gallery.core.model.DateSource
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.LibraryType
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.SortMode
import dev.lumo.gallery.core.model.Tag

fun LibraryEntity.toModel(): Library = Library(
    id = id,
    type = runCatching { LibraryType.valueOf(type) }.getOrDefault(LibraryType.FOLDER),
    name = name,
    treeUri = treeUri,
    treeRootRelPath = treeRootRelPath,
    parentId = parentId,
    scanMode = runCatching { dev.lumo.gallery.core.model.ScanMode.valueOf(scanMode) }
        .getOrDefault(dev.lumo.gallery.core.model.ScanMode.RECURSIVE),
    isFavorite = isFavorite,
    archiveUri = archiveUri,
    archiveFormat = archiveFormat?.let { runCatching { ArchiveFormat.valueOf(it) }.getOrNull() },
    displayPath = displayPath,
    isPrivate = isPrivate,
    isAvailable = isAvailable,
    sortMode = runCatching { SortMode.valueOf(sortMode) }.getOrDefault(SortMode.NATURAL),
    createdAt = createdAt,
    lastScanAt = lastScanAt,
    coverItemId = coverItemId,
    itemCount = itemCount,
    scanStatus = runCatching { ScanStatus.valueOf(scanStatus) }.getOrDefault(ScanStatus.NEVER),
    lastError = lastError
)

fun Library.toEntity(): LibraryEntity = LibraryEntity(
    id = id,
    type = type.name,
    name = name,
    treeUri = treeUri,
    treeRootRelPath = treeRootRelPath,
    parentId = parentId,
    scanMode = scanMode.name,
    isFavorite = isFavorite,
    archiveUri = archiveUri,
    archiveFormat = archiveFormat?.name,
    displayPath = displayPath,
    isPrivate = isPrivate,
    isAvailable = isAvailable,
    sortMode = sortMode.name,
    createdAt = createdAt,
    lastScanAt = lastScanAt,
    coverItemId = coverItemId,
    itemCount = itemCount,
    scanStatus = scanStatus.name,
    lastError = lastError
)

fun MediaItemEntity.toModel(): MediaItem = MediaItem(
    id = id,
    libraryId = libraryId,
    locator = locator,
    docUri = docUri,
    displayName = displayName,
    mime = mime,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    dateTaken = dateTaken,
    dateSource = dateSource?.let { runCatching { DateSource.valueOf(it) }.getOrNull() },
    width = width,
    height = height,
    orientation = orientation,
    note = note,
    isFavorite = isFavorite,
    isPresent = isPresent,
    thumbReady = thumbReady,
    phash = phash,
    addedAt = addedAt
)

fun MediaItem.toEntity(sortKey: String): MediaItemEntity = MediaItemEntity(
    id = id,
    libraryId = libraryId,
    locator = locator,
    docUri = docUri,
    displayName = displayName,
    mime = mime,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    dateTaken = dateTaken,
    dateSource = dateSource?.name,
    width = width,
    height = height,
    orientation = orientation,
    note = note,
    isFavorite = isFavorite,
    isPresent = isPresent,
    thumbReady = thumbReady,
    phash = phash,
    addedAt = addedAt,
    sortKey = sortKey
)

fun TagEntity.toModel(): Tag = Tag(id = id, name = name, colorArgb = colorArgb, createdAt = createdAt)
