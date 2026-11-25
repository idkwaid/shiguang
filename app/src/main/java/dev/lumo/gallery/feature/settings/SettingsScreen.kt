package dev.lumo.gallery.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lumo.gallery.BuildConfig
import dev.lumo.gallery.core.model.ThumbnailQuality
import dev.lumo.gallery.core.security.PinHasher
import dev.lumo.gallery.core.ui.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenLogs: () -> Unit = {}, onOpenCleanup: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = dev.lumo.gallery.core.data.repo.AppSettings()
    )
    var cacheBytes by remember { mutableStateOf(0L) }
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
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
                headlineContent = { Text("私密空间") },
                supportingContent = {
                    Text(if (settings.hasPin) "已设置密码 · 退到后台自动上锁" else "未设置密码")
                },
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { pinDialog = true }) {
                        Text(if (settings.hasPin) "修改密码" else "设置密码")
                    }
                }
            )
            if (settings.hasPin) {
                ListItem(
                    headlineContent = { Text("指纹 / 面容解锁") },
                    supportingContent = { Text("使用系统生物识别或锁屏密码") },
                    trailingContent = {
                        Switch(
                            checked = settings.biometricUnlock,
                            onCheckedChange = { enabled ->
                                scope.launch { container.settingsRepository.setBiometricUnlock(enabled) }
                            }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("立即上锁") },
                    supportingContent = { Text("清除本次运行的私密解锁状态") },
                    trailingContent = {
                        TextButton(onClick = { container.privateAccess.lock() }) { Text("上锁") }
                    }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("缩略图缓存") },
                supportingContent = { Text(formatBytes(cacheBytes)) },
                trailingContent = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                container.thumbCache.clearAll()
                                container.libraryRepository.regenerateAllThumbnails()
                            }
                            cacheBytes = withContext(Dispatchers.IO) { container.thumbCache.totalBytes() }
                        }
                    }) { Text("清空") }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("缩略图清晰度") },
                supportingContent = {
                    Text("${settings.thumbnailQuality.label} · 越低加载越快，修改后会重新生成缩略图")
                }
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThumbnailQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = settings.thumbnailQuality == quality,
                        onClick = {
                            if (settings.thumbnailQuality != quality) {
                                scope.launch {
                                    container.settingsRepository.setThumbnailQuality(quality)
                                    withContext(Dispatchers.IO) { container.thumbCache.clearAll() }
                                    container.libraryRepository.regenerateAllThumbnails()
                                }
                            }
                        },
                        label = { Text(quality.label) }
                    )
                }
            }
            ListItem(
                headlineContent = { Text("移动/删除应用到实际文件夹") },
                supportingContent = {
                    Text(
                        if (settings.applyToRealFiles) {
                            "开启：移动/删除会修改手机里的文件（压缩包内文件仍只改索引；顶层图库不会被物理删除）"
                        } else {
                            "关闭：只调整 App 内的归属/索引，不动源文件"
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.applyToRealFiles,
                        onCheckedChange = { enabled ->
                            scope.launch { container.settingsRepository.setApplyToRealFiles(enabled) }
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("智能清理") },
                supportingContent = { Text("重复图、相似图、大体积 GIF/HEIC") },
                leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                modifier = Modifier.clickable { onOpenCleanup() }
            )
            ListItem(
                headlineContent = { Text("运行日志") },
                supportingContent = { Text("排查问题用；可查看/分享最近日志，崩溃堆栈也会记录") },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                modifier = Modifier.clickable { onOpenLogs() }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于拾光") },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME} · 本地运行，不上传任何图片") }
            )
            ListItem(
                headlineContent = { Text("数据位置") },
                supportingContent = {
                    Text("索引：应用私有数据库\n缩略图 / 压缩包解压：应用缓存目录（可随时清空重建）")
                }
            )
            HorizontalDivider()
            Text(
                text = "私密图库是应用内隐藏 + 门禁 + 防截屏，不做文件级加密；忘记密码只能重置并清空私密索引。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp)
            )
        }
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text(if (settings.hasPin) "修改私密密码" else "设置私密密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("至少 4 位") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin.length >= 4 && pin == pinConfirm) {
                        val salt = PinHasher.newSalt()
                        val hash = PinHasher.hash(pin, salt)
                        scope.launch {
                            container.settingsRepository.setPin(hash, salt)
                            container.privateAccess.unlock()
                        }
                        pin = ""
                        pinConfirm = ""
                        pinDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { pinDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
