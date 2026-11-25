package dev.lumo.gallery.feature.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.security.SecureScreen
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.ZoomablePhoto
import dev.lumo.gallery.core.util.AppLog
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlin.math.roundToInt

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Ready(val file: File) : PreviewState
    data object Failed : PreviewState
}

/**
 * 沉浸式全屏查看（精简版）：
 * - 左右翻页、捏合/双击缩放、双指长按对比、左边缘右滑返回；
 * - 单击画面中央：底部升起左右滑杆，拖动可跳到任意页；
 * - 顶部仅保留页码；不再放编辑/幻灯片/信息/缩略图按钮。
 */
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
    val privateLibrary by vm.privateLibrary.collectAsStateWithLifecycle()
    val privateUnlocked by container.privateAccess.unlocked.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    ImmersiveViewer()
    SecureScreen(enabled = privateLibrary)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (privateLibrary && !privateUnlocked) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "已锁定",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(text = "私密空间已锁定", color = Color.White, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = onBack) { Text("返回", color = Color.White) }
            }
            return@Box
        }

        if (loading || items.isEmpty()) {
            CircularProgressIndicator(
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
        var pageZoom by remember { mutableFloatStateOf(1f) }
        var sliderVisible by remember { mutableStateOf(false) }
        var pendingPage by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .debounce(120)
                .collect { page -> vm.saveProgress(page) }
        }

        LaunchedEffect(pagerState.settledPage) {
            pageZoom = 1f
        }

        LaunchedEffect(pendingPage) {
            pendingPage?.let { target ->
                pagerState.scrollToPage(target.coerceIn(0, items.lastIndex))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pageZoom <= 1.01f,
            key = { page -> items[page].id }
        ) { page ->
            val item = items[page]
            var reloadToken by remember(item.id) { mutableIntStateOf(0) }
            val previewState by produceState<PreviewState>(
                initialValue = PreviewState.Loading,
                item.id,
                item.lastModified,
                reloadToken
            ) {
                value = PreviewState.Loading
                val file = vm.preview(item)
                value = if (file != null) PreviewState.Ready(file) else PreviewState.Failed
            }

            when (val state = previewState) {
                is PreviewState.Ready -> ZoomablePhoto(
                    file = state.file,
                    contentDescription = item.displayName,
                    resetKey = item.id,
                    onTap = { sliderVisible = !sliderVisible },
                    onZoomChanged = { pageZoom = it }
                )

                PreviewState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    Text(
                        text = "正在解码…",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                PreviewState.Failed -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "这张图片无法解码（格式不支持或文件损坏）",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    TextButton(onClick = {
                        AppLog.w("Viewer", "用户重试解码 id=${item.id}")
                        vm.invalidatePreview(item.id)
                        reloadToken++
                    }) { Text("重试", color = Color.White) }
                }
            }
        }

        // 顶部页码（始终可见，保持沉浸）
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0x52000000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
        ) {
            Text(
                text = "${(pendingPage ?: pagerState.currentPage) + 1} / ${items.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // 点击中央后从底部升起的页码滑杆
        AnimatedVisibility(
            visible = sliderVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                color = Color(0xE60D0D0D),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${(pendingPage ?: pagerState.currentPage) + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.width(34.dp)
                        )
                        Slider(
                            value = (pendingPage ?: pagerState.currentPage).toFloat(),
                            onValueChange = { value ->
                                sliderVisible = true
                                pendingPage = value.roundToInt()
                            },
                            onValueChangeFinished = { pendingPage = null },
                            valueRange = 0f..(items.lastIndex.coerceAtLeast(0)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "${items.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(34.dp)
                        )
                    }
                    Text(
                        text = "左右拖动跳到指定页",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 42.dp, top = 2.dp)
                    )
                }
            }
        }

        // 左边缘右滑返回（避免和图片缩放手势抢事件，只占最左侧窄条）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(pageZoom) {
                    if (pageZoom > 1.01f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f
                        var triggered = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dx += change.positionChange().x
                            if (!triggered && dx > 70.dp.toPx()) {
                                triggered = true
                                onBack()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
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
