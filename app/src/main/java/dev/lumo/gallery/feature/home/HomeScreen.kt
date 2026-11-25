package dev.lumo.gallery.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState
import dev.lumo.gallery.core.ui.components.LibraryCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLibrary: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPrivate: () -> Unit = {},
    onOpenTags: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val libraries by vm.libraryList.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val scanStates by vm.scanStates.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val privateLibraries by vm.privateLibraries.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val importStatus by vm.importStatus.collectAsStateWithLifecycle()
    val childCounts by vm.childCounts.collectAsStateWithLifecycle()
    var folderChoice by remember { mutableStateOf<Uri?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var actionTarget by remember { mutableStateOf<Library?>(null) }
    var renameTarget by remember { mutableStateOf<Library?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Library?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) folderChoice = uri }

    val archiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.onArchivePicked(uri) { id -> onOpenLibrary(id) } }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("拾光", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (privateLibraries.isNotEmpty()) {
                        IconButton(onClick = onOpenPrivate) {
                            Icon(Icons.Outlined.Lock, contentDescription = "私密空间")
                        }
                    }
                    IconButton(onClick = onOpenTags) {
                        Icon(Icons.Outlined.Sell, contentDescription = "标签")
                    }
                    IconButton(onClick = {
                        val pool = libraries
                        if (pool.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("还没有图库") }
                        } else {
                            val pick = pool.random()
                            onOpenLibrary(pick.id)
                        }
                    }) {
                        Icon(Icons.Outlined.Casino, contentDescription = "随机图库")
                    }
                    IconButton(onClick = { archiveLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderZip, contentDescription = "导入压缩包")
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { folderLauncher.launch(null) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(if (busy) "正在导入…" else "添加文件夹") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (importStatus != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = importStatus!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        if (libraries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FolderOpen,
                title = "还没有图库",
                description = "把手机里的相册文件夹加进来。图片只在本机读取，不会上传。",
                modifier = Modifier.weight(1f),
                action = {
                    TextButton(onClick = { folderLauncher.launch(null) }) {
                        Text("选择文件夹")
                    }
                }
            )
        } else {
            Column {
                if (tags.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tags.size) { index ->
                            val tag = tags[index]
                            androidx.compose.material3.Surface(
                                onClick = onOpenTags,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "# ${tag.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(libraries, key = { it.id }) { library ->
                    LibraryCard(
                        library = library,
                        cover = library.coverItemId?.let { covers[it] },
                        scanning = scanStates[library.id],
                        childCount = childCounts[library.id] ?: 0,
                        onFavoriteToggle = { vm.toggleLibraryFavorite(library) },
                        onClick = { onOpenLibrary(library.id) },
                        onLongClick = { actionTarget = library }
                    )
                }
            }
            }
        }
        }
    }

    folderChoice?.let { uri ->
        AlertDialog(
            onDismissRequest = { folderChoice = null },
            title = { Text("导入方式") },
            text = {
                Text(
                    "分组导入：每个含图片的子文件夹、以及文件夹里的每个压缩包，各建一个图库。\n" +
                        "整体导入：整个文件夹作为一个图库，子目录图片平铺在一起。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    folderChoice = null
                    vm.onFolderPickedGrouped(uri)
                }) { Text("分组导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    folderChoice = null
                    vm.onFolderPicked(uri)
                }) { Text("整体导入") }
            }
        )
    }

    actionTarget?.let { library ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            libraryActions(
                library = library,
                onRescan = {
                    actionTarget = null
                    vm.rescan(library.id)
                },
                onRename = {
                    actionTarget = null
                    renameTarget = library
                    renameText = library.name
                },
                onTogglePrivate = {
                    actionTarget = null
                    vm.setPrivate(library, !library.isPrivate)
                },
                onDelete = {
                    actionTarget = null
                    deleteTarget = library
                }
            )
        }
    }

    renameTarget?.let { library ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名图库") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(library.id, renameText)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { library ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("从图库移除「${library.name}」？") },
            text = { Text("只移除索引与缩略图，手机里的原图不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(library.id)
                    deleteTarget = null
                    scope.launch { snackbar.showSnackbar("已移除") }
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun libraryActions(
    library: Library,
    onRescan: () -> Unit,
    onRename: () -> Unit,
    onTogglePrivate: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp)
    ) {
        Text(
            text = library.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text(
            text = "${library.itemCount} 张 · ${if (library.isArchive) "压缩包" else "文件夹"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Box(Modifier.height(10.dp))
        androidx.compose.material3.TextButton(onClick = onRescan, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("重新扫描")
        }
        androidx.compose.material3.TextButton(onClick = onRename, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("重命名")
        }
        androidx.compose.material3.TextButton(onClick = onTogglePrivate, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(if (library.isPrivate) "取消私密" else "设为私密")
        }
        androidx.compose.material3.TextButton(onClick = onDelete, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("从图库移除", color = MaterialTheme.colorScheme.error)
        }
        if (library.lastError != null) {
            Text(
                text = library.lastError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
    }
}
