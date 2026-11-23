package dev.lumo.gallery.feature.viewer

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.image.ExifDates
import dev.lumo.gallery.core.image.PaletteExtractor
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.ZoomablePhoto
import dev.lumo.gallery.core.util.ShareHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 沉浸式全屏查看：左右翻页、捏合缩放、双击缩放、双指长按对比、
 * 单击隐藏 UI、边缘右滑/下拉退出、底部页码、缩略图导航、幻灯片、动态取色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    libraryId: String,
    itemId: String,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: ViewerViewModel = viewModel(
        key = "viewer-$libraryId-$itemId",
        factory = ViewerViewModel.factory(container, libraryId, itemId)
    )
    val items by vm.items.collectAsStateWithLifecycle()
    val startIndex by vm.startIndex.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)
    ImmersiveViewer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loading || items.isEmpty()) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
            )
            return@Box
        }

        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            pageCount = { items.size }
        )
        var chromeVisible by remember { mutableStateOf(true) }
        var pageZoom by remember { mutableFloatStateOf(1f) }
        var slideshow by remember { mutableStateOf(false) }
        var showThumbnails by remember { mutableStateOf(false) }
        var showInfo by remember { mutableStateOf(false) }
        var comparing by remember { mutableStateOf(false) }
        var accent by remember { mutableStateOf<Color?>(null) }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .debounce(120)
                .collect { page -> vm.saveProgress(page) }
        }

        LaunchedEffect(pagerState.settledPage) {
            pageZoom = 1f
            val item = items.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            accent = withContext(Dispatchers.IO) { PaletteExtractor.fromFile(vm.preview(item)) }
        }

        LaunchedEffect(slideshow, items.size) {
            if (!slideshow || items.size <= 1) return@LaunchedEffect
            while (true) {
                delay(3_000)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pageZoom <= 1.01f && !comparing,
            key = { page -> items[page].id }
        ) { page ->
            val item = items[page]
            val file by produceState<File?>(initialValue = null, item.id, item.lastModified) {
                value = vm.preview(item)
            }
            ZoomablePhoto(
                file = file,
                contentDescription = item.displayName,
                resetKey = item.id,
                onTap = { chromeVisible = !chromeVisible },
                onZoomChanged = { pageZoom = it },
                onCompareChanged = { comparing = it }
            )
        }

        // 顶部栏
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val current = items.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                    .padding(top = 12.dp, start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current?.displayName.orEmpty(),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageZoom > 1.01f) {
                        Text(
                            text = "${"%.1f".format(Locale.US, pageZoom)}x",
                            color = Color.White.copy(alpha = 0.6f),
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                }
                val favorite = current != null && favoriteIds.contains(current.id)
                IconButton(onClick = { current?.let(vm::toggleFavorite) }) {
                    Icon(
                        imageVector = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) Color(0xFFE45C7B) else Color.White
                    )
                }
                IconButton(onClick = {
                    current?.let { item ->
                        scope.launch {
                            val library = vm.libraryOf(item)
                            if (library != null) {
                                val ok = ShareHelper.share(context, container.streamProvider, library, item)
                                if (!ok) Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "分享", tint = Color.White)
                }
            }
        }

        // 底部栏 + 页码
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (slideshow) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x66000000),
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "自动播放中 · 3 秒/张",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
            Surface(
                onClick = { showThumbnails = true },
                shape = RoundedCornerShape(50),
                color = Color(0x66000000),
                contentColor = Color.White
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerAction(
                        icon = if (slideshow) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        label = if (slideshow) "暂停" else "幻灯片"
                    ) {
                        slideshow = !slideshow
                        if (slideshow) chromeVisible = true
                    }
                    ViewerAction(Icons.Outlined.ViewModule, "缩略图") {
                        showThumbnails = true
                    }
                    ViewerAction(Icons.Outlined.Info, "信息") {
                        showInfo = true
                    }
                    ViewerAction(Icons.Outlined.Edit, "编辑") {
                        Toast.makeText(context, "编辑功能在 M6 里接进来", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 屏幕边缘右滑返回 / 下拉返回（1x 时启用）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    Modifier.pointerInput(pageZoom) {
                        if (pageZoom > 1.01f) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dx = 0f
                            var dy = 0f
                            var triggered = false
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                dx += change.positionChange().x
                                dy += change.positionChange().y
                                if (!triggered) {
                                    if (down.position.x < 36.dp.toPx() && dx > 80.dp.toPx()) {
                                        triggered = true
                                        onBack()
                                    } else if (dy > 130.dp.toPx() && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                        triggered = true
                                        onBack()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                )
        )

        if (comparing) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0x88000000),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 400.dp)
            ) {
                Text(
                    text = "原图",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (showThumbnails) {
            ThumbnailSheet(
                items = items,
                currentIndex = pagerState.currentPage,
                onDismiss = { showThumbnails = false },
                onSelect = { index ->
                    showThumbnails = false
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        }
        if (showInfo) {
            items.getOrNull(pagerState.currentPage)?.let { item ->
                InfoSheet(item = item, onDismiss = { showInfo = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ImmersiveViewer() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailSheet(
    items: List<MediaItem>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val container = LocalAppContainer.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        runCatching { gridState.scrollToItem((currentIndex - 4).coerceAtLeast(0)) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414)
    ) {
        Text(
            text = "缩略图 · ${items.size} 张",
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val thumb = container.thumbCache.existing(item.libraryId, item.id, item.lastModified)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF222222))
                        .then(
                            if (index == currentIndex) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                            } else Modifier
                        )
                        .clickable { onSelect(index) }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            Text(
                text = item.displayName,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            InfoRow("尺寸", if (item.width > 0) "${item.width} × ${item.height}" else "未知")
            InfoRow("大小", formatBytes(item.sizeBytes))
            InfoRow("拍摄/修改", ExifDates.formatDateTime(item.dateTaken ?: item.lastModified))
            InfoRow("格式", item.mime)
            InfoRow("路径", item.locator)
            InfoRow("收藏", if (item.isFavorite) "是" else "否")
            if (item.note != null) InfoRow("备注", item.note)
            HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "完整 EXIF（相机、光圈、ISO、GPS）在 M6 接入。",
                color = Color.White.copy(alpha = 0.45f),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.size(width = 84.dp, height = 20.dp)
        )
        Text(
            text = value,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
