package dev.lumo.gallery.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState
import dev.lumo.gallery.core.ui.components.PhotoTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryId: String,
    onBack: () -> Unit,
    onOpenViewer: (libraryId: String, itemId: String) -> Unit
) {
    val container = LocalAppContainer.current
    val vm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(container, libraryId)
    )
    val library by vm.library.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    val continueInfo by vm.continueInfo.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var passwordText by remember { mutableStateOf("") }

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
            when {
                library == null -> Unit
                items.isEmpty() -> EmptyState(
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
                        if (library?.scanStatus == ScanStatus.PASSWORD_REQUIRED) {
                            TextButton(onClick = { passwordDialog = true }) { Text("输入密码") }
                        } else {
                            TextButton(onClick = { vm.refresh() }) { Text("重新扫描") }
                        }
                    }
                )
                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 90.dp),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            PhotoTile(
                                item = item,
                                onClick = { onOpenViewer(libraryId, item.id) }
                            )
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
