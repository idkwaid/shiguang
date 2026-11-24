package dev.lumo.gallery.feature.library

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.LibraryType
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.security.SecureScreen
import dev.lumo.gallery.core.util.ShareHelper
import dev.lumo.gallery.feature.tags.TagEditorDialog
import kotlinx.coroutines.launch
import dev.lumo.gallery.core.ui.components.ChildLibraryCard
import dev.lumo.gallery.core.ui.components.EmptyState
import dev.lumo.gallery.core.ui.components.PhotoTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryId: String,
    onBack: () -> Unit,
    onOpenViewer: (libraryId: String, itemId: String) -> Unit,
    onOpenPrivate: () -> Unit = onBack,
    onOpenEditor: (libraryId: String, itemId: String) -> Unit = { _, _ -> },
    onOpenLibrary: (String) -> Unit = { _ -> onBack() }
) {
    val container = LocalAppContainer.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(container, libraryId)
    )
    val library by vm.library.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    val continueInfo by vm.continueInfo.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val childCovers by vm.childCovers.collectAsStateWithLifecycle()

    val unlocked by container.privateAccess.unlocked.collectAsStateWithLifecycle()
    SecureScreen(enabled = library?.isPrivate == true)
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var passwordText by remember { mutableStateOf("") }
    var actionItem by remember { mutableStateOf<MediaItem?>(null) }
    var tagItem by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    var infoTarget by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.reauthorize(uri) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(library?.treeUri) {
        val tree = library?.treeUri?.let(Uri::parse)
        if (tree == null) {
            onDispose { }
        } else {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    vm.onFolderChanged()
                }
            }
            runCatching { context.contentResolver.registerContentObserver(tree, true, observer) }
            onDispose {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(library?.scanStatus) {
        if (library?.scanStatus == ScanStatus.PASSWORD_REQUIRED) {
            passwordText = ""
            passwordDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        text = library?.name ?: "图库",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val childList = children
                        val itemList = items
                        val total = childList.size + itemList.size
                        if (total == 0) {
                            scope.launch { snackbar.showSnackbar("这个图库还没有可随机的图片或子库") }
                        } else {
                            val index = kotlin.random.Random.nextInt(total)
                            if (index < childList.size) {
                                onOpenLibrary(childList[index].id)
                            } else {
                                itemList.getOrNull(index - childList.size)?.let { item ->
                                    onOpenViewer(libraryId, item.id)
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Casino, contentDescription = "随机本图库")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("重新扫描") },
                                onClick = {
                                    menuOpen = false
                                    vm.refresh()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重新生成缩略图") },
                                onClick = {
                                    menuOpen = false
                                    vm.regenerateThumbnails()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("从图库移除") },
                                onClick = {
                                    menuOpen = false
                                    confirmRemove = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val refreshing = scanProgress?.phase == ScanProgress.Phase.WALKING ||
                scanProgress?.phase == ScanProgress.Phase.INDEXING
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                library == null -> Unit
                library?.isPrivate == true && !unlocked -> EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = "私密图库",
                    description = "这个图库已从首页隐藏，解锁后才能查看。",
                    action = {
                        TextButton(onClick = onOpenPrivate) { Text("去解锁") }
                    }
                )
                items.isEmpty() && children.isEmpty() -> EmptyState(
                    icon = when (library?.scanStatus) {
                        ScanStatus.PERMISSION_LOST -> Icons.Outlined.WarningAmber
                        ScanStatus.EMPTY -> Icons.Outlined.FolderOpen
                        ScanStatus.CORRUPT, ScanStatus.ERROR -> Icons.Outlined.BrokenImage
                        else -> Icons.Outlined.FolderOpen
                    },
                    title = when (library?.scanStatus) {
                        ScanStatus.PERMISSION_LOST -> "目录授权已失效"
                        ScanStatus.EMPTY -> "这个文件夹还是空的"
                        ScanStatus.CORRUPT -> "压缩包读取失败"
                        ScanStatus.PASSWORD_REQUIRED -> "压缩包已加密"
                        ScanStatus.ERROR -> "读取失败"
                        else -> "还没有图片"
                    },
                    description = library?.lastError
                        ?: "添加图片到该文件夹后，下拉刷新或点右上角刷新即可重新扫描。",
                    action = {
                        when (library?.scanStatus) {
                            ScanStatus.PASSWORD_REQUIRED ->
                                TextButton(onClick = { passwordDialog = true }) { Text("输入密码") }
                            ScanStatus.PERMISSION_LOST ->
                                TextButton(onClick = { reauthLauncher.launch(null) }) { Text("重新授权") }
                            else ->
                                TextButton(onClick = { vm.refresh() }) { Text("重新扫描") }
                        }
                    }
                )
                else -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        if (children.isNotEmpty()) {
                            Text(
                                text = "子图库 · ${children.size}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                            )
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(children, key = { it.id }) { child ->
                                    ChildLibraryCard(
                                        library = child,
                                        cover = childCovers[child.coverItemId],
                                        onClick = { onOpenLibrary(child.id) }
                                    )
                                }
                            }
                        }

                        if (items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "这个图库只有子图库，点上方卡片继续浏览",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 90.dp),
                                verticalItemSpacing = 8.dp,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    PhotoTile(
                                        item = item,
                                        onClick = { onOpenViewer(libraryId, item.id) },
                                        onLongClick = { actionItem = item }
                                    )
                                }
                            }
                        }
                    }

                    if (continueInfo != null) {
                        val info = continueInfo!!
                        Surface(
                            onClick = {
                                items.getOrNull(info.index)?.let { onOpenViewer(libraryId, it.id) }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp)
                        ) {
                            Text(
                                text = "继续浏览 · 第 ${info.index + 1} 张",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                    val progress = scanProgress
                    if (progress != null && progress.phase != dev.lumo.gallery.core.model.ScanProgress.Phase.DONE) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = progress.message,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
            }
        }
    }

    actionItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { actionItem = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                TextButton(
                    onClick = {
                        vm.toggleFavorite(item)
                        actionItem = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (item.isFavorite) "取消收藏" else "收藏")
                }
                TextButton(
                    onClick = {
                        tagItem = item
                        actionItem = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.Sell, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("标签")
                }
                TextButton(
                    onClick = {
                        actionItem = null
                        onOpenEditor(libraryId, item.id)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = {
                        actionItem = null
                        infoTarget = item
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("信息")
                }
                TextButton(
                    onClick = {
                        val librarySnapshot = library
                        actionItem = null
                        if (librarySnapshot != null) {
                            scope.launch {
                                val ok = ShareHelper.share(context, container.streamProvider, librarySnapshot, item)
                                if (!ok) snackbar.showSnackbar("分享失败")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("分享")
                }
                TextButton(
                    onClick = {
                        deleteTarget = item
                        actionItem = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    tagItem?.let { item ->
        TagEditorDialog(mediaId = item.id, onDismiss = { tagItem = null })
    }

    infoTarget?.let { item ->
        ImageInfoDialog(item = item, library = library, onDismiss = { infoTarget = null })
    }

    deleteTarget?.let { item ->
        val isArchive = library?.type == LibraryType.ARCHIVE
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这张图片？") },
            text = {
                Text(
                    if (isArchive) "压缩包里的文件无法单独删除，只会从索引里移除。"
                    else "会同时删除手机里的源文件，删除后不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(item, deleteFromDisk = !isArchive)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = { passwordDialog = false },
            title = { Text("压缩包已加密") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("输入密码继续。密码只保存在本次运行的内存里，不会写盘。")
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        singleLine = true,
                        label = { Text("密码") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    passwordDialog = false
                    vm.submitPassword(passwordText)
                }) { Text("解锁") }
            },
            dismissButton = {
                TextButton(onClick = { passwordDialog = false }) { Text("取消") }
            }
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("从图库移除？") },
            text = { Text("只移除索引与缩略图，手机里的原图不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeLibrary(onDone = onBack)
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("取消") }
            }
        )
    }
}


private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun ImageInfoDialog(
    item: MediaItem,
    library: dev.lumo.gallery.core.model.Library?,
    onDismiss: () -> Unit
) {
    val container = LocalAppContainer.current
    val exif by androidx.compose.runtime.produceState<dev.lumo.gallery.core.image.ExifInfo?>(
        initialValue = null,
        item.id,
        item.lastModified
    ) {
        value = if (library == null) null
        else dev.lumo.gallery.core.image.ExifReader.read(container.streamProvider, library, item)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片信息") },
        text = {
            Column {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val camera = listOfNotNull(exif?.make, exif?.model).joinToString(" ").trim()
                if (camera.isNotEmpty()) Text("相机：$camera")
                if (!exif?.lens.isNullOrBlank()) Text("镜头：${exif?.lens}")
                if (!exif?.iso.isNullOrBlank()) Text("ISO：${exif?.iso}")
                if (!exif?.aperture.isNullOrBlank()) Text("光圈：${exif?.aperture}")
                if (!exif?.shutter.isNullOrBlank()) Text("快门：${exif?.shutter}")
                if (!exif?.focalLength.isNullOrBlank()) Text("焦距：${exif?.focalLength}")
                Text("尺寸：${if (item.width > 0) "${item.width} × ${item.height}" else "未知"}")
                Text("大小：${formatSize(item.sizeBytes)}")
                Text(
                    "拍摄/修改：${
                        dev.lumo.gallery.core.image.ExifDates.formatDateTime(
                            item.dateTaken ?: item.lastModified
                        )
                    }"
                )
                if (exif?.hasGps == true) {
                    Text("位置：${"%.5f, %.5f".format(exif?.latitude, exif?.longitude)}")
                }
                Text("格式：${item.mime}")
                Text("路径：${item.locator}")
                if (!item.note.isNullOrBlank()) Text("备注：${item.note}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
