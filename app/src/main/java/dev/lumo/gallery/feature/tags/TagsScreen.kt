package dev.lumo.gallery.feature.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState
import dev.lumo.gallery.core.ui.components.PhotoTile

val TagColors = listOf(
    Color(0xFF4F8F84),
    Color(0xFFD9A441),
    Color(0xFFE45C7B),
    Color(0xFF6C7BC7),
    Color(0xFF9B7EBD),
    Color(0xFFB8865B)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onOpenTag: (Long, String) -> Unit
) {
    val container = LocalAppContainer.current
    val vm: TagsViewModel = viewModel(factory = TagsViewModel.factory(container))
    val tags by vm.tags.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("标签") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建标签")
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
            if (tags.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Sell,
                    title = "还没有标签",
                    description = "给图片打上「壁纸」「灵感」这样的标签，之后可以在这里按标签翻图。"
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(tags, key = { it.tag.id }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenTag(item.tag.id, item.tag.name) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(Color(item.tag.colorArgb), CircleShape)
                                )
                            },
                            headlineContent = { Text(item.tag.name) },
                            supportingContent = { Text("${item.count} 张") },
                            trailingContent = {
                                IconButton(onClick = { vm.deleteTag(item.tag.id) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "删除标签",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建标签") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("名称") }
                    )
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TagColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color, CircleShape)
                                    .clickable { colorIndex = index }
                            ) {
                                if (index == colorIndex) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color.White.copy(alpha = 0.35f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    creating = false
                    vm.createTag(newName, TagColors[colorIndex].toArgb())
                    newName = ""
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailScreen(
    tagId: Long,
    tagName: String,
    onBack: () -> Unit,
    onOpenViewer: (String, String) -> Unit
) {
    val container = LocalAppContainer.current
    val vm: TagDetailViewModel = viewModel(
        key = "tag-$tagId",
        factory = TagDetailViewModel.factory(container, tagId)
    )
    val items by vm.items.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("标签 · $tagName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Sell,
                    title = "这个标签下还没有图片",
                    description = "在图片长按菜单里选择「标签」即可添加。"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items, key = { it.id }) { item: MediaItem ->
                        PhotoTile(
                            item = item,
                            onClick = { onOpenViewer(item.libraryId, item.id) }
                        )
                    }
                }
            }
        }
    }
}

/** 长按菜单里的标签编辑弹窗。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    mediaId: String,
    onDismiss: () -> Unit
) {
    val container = LocalAppContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var allTags by remember { mutableStateOf<List<dev.lumo.gallery.core.model.Tag>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var newName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(mediaId) {
        val repo = container.mediaRepository
        allTags = repo.observeTags().first()
        selected = repo.tagsFor(mediaId).map { it.id }.toSet()
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            Column {
                if (loading) {
                    Text("加载中…")
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            val isSelected = selected.contains(tag.id)
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        if (isSelected) {
                                            container.mediaRepository.removeTagFromMedia(mediaId, tag.id)
                                            selected = selected - tag.id
                                        } else {
                                            container.mediaRepository.addTagToMedia(mediaId, tag.id)
                                            selected = selected + tag.id
                                        }
                                    }
                                },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("新标签名") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (newName.isNotBlank()) {
                        val id = container.mediaRepository.createTag(newName, TagColors[0].toArgb())
                        if (id > 0) container.mediaRepository.addTagToMedia(mediaId, id)
                    }
                    onDismiss()
                }
            }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
