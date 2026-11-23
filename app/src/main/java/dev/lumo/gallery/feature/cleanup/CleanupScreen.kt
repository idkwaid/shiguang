package dev.lumo.gallery.feature.cleanup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: CleanupViewModel = viewModel(factory = CleanupViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        if (!state.working && state.exactGroups.isEmpty() && state.similarGroups.isEmpty()) vm.scanAll()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("智能清理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.scanAll() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新分析")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.phase,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(
                    "重复 ${state.exactGroups.size}",
                    "相似 ${state.similarGroups.size}",
                    "格式 ${state.gifs.size + state.heics.size}"
                ).forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (tab) {
                0 -> GroupList(
                    groups = state.exactGroups,
                    emptyTitle = "没有精确重复的图片",
                    emptyDesc = "同尺寸同内容的图片会出现在这里。",
                    onDeleteRest = { group -> vm.deleteItems(group) }
                )

                1 -> GroupList(
                    groups = state.similarGroups,
                    emptyTitle = "没有高度相似的图片",
                    emptyDesc = "连拍、缩略图近似会被识别出来（汉明距离 ≤ 8）。",
                    onDeleteRest = { group -> vm.deleteItems(group) }
                )

                else -> FormatList(
                    gifs = state.gifs,
                    heics = state.heics,
                    onDelete = { item -> vm.deleteItems(listOf(item), keepFirst = false) }
                )
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<List<MediaItem>>,
    emptyTitle: String,
    emptyDesc: String,
    onDeleteRest: (List<MediaItem>) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState(icon = Icons.Outlined.CleaningServices, title = emptyTitle, description = emptyDesc)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups.size) { index ->
            val group = groups[index]
            GroupCard(group = group, onDeleteRest = { onDeleteRest(group) })
        }
    }
}

@Composable
private fun GroupCard(group: List<MediaItem>, onDeleteRest: () -> Unit) {
    val container = LocalAppContainer.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.take(4).forEach { item ->
                    val thumb = container.thumbCache.existing(item.libraryId, item.id, item.lastModified)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${group.size} 张 · 单张 ${formatBytes(group.first().sizeBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDeleteRest, modifier = Modifier.align(Alignment.End)) {
                Text("保留第一张，删除其余")
            }
        }
    }
}

@Composable
private fun FormatList(
    gifs: List<MediaItem>,
    heics: List<MediaItem>,
    onDelete: (MediaItem) -> Unit
) {
    val container = LocalAppContainer.current
    val all = (gifs.map { "GIF" to it } + heics.map { "HEIC" to it }).sortedByDescending { it.second.sizeBytes }
    if (all.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Image,
            title = "没有可清理的格式",
            description = "GIF / HEIC 等大体积格式会显示在这里，方便按体积清理。"
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        itemsIndexed(all) { _, pair ->
            val (label, item) = pair
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val thumb = container.thumbCache.existing(item.libraryId, item.id, item.lastModified)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        text = "$label · ${formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onDelete(item) }) { Text("删除") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
