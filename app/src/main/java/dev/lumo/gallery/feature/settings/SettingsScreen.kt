package dev.lumo.gallery.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lumo.gallery.BuildConfig
import dev.lumo.gallery.core.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf(0L) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { container.thumbCache.totalBytes() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("缩略图缓存") },
                supportingContent = { Text(formatBytes(cacheBytes)) },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { container.thumbCache.clearAll() }
                            cacheBytes = withContext(Dispatchers.IO) { container.thumbCache.totalBytes() }
                        }
                    }) { Text("清空") }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于拾光") },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME} · 本地运行，不上传任何图片") }
            )
            ListItem(
                headlineContent = { Text("数据位置") },
                supportingContent = {
                    Text("索引：应用私有数据库\n缩略图：应用缓存目录（可随时清空重建）")
                }
            )
            HorizontalDivider()
            Text(
                text = "私密图库、幻灯片间隔、动态取色等设置在后续里程碑开放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
