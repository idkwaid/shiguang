package dev.lumo.gallery.feature.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import java.io.File
import kotlinx.coroutines.flow.debounce

/**
 * 沉浸式全屏查看：黑底、隐藏系统栏、左右滑动、页码指示。
 * 缩放/手势/幻灯片/缩略图导航在 M4 补齐。
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
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    ImmersiveViewer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .debounce(120)
                .collect { page -> vm.saveProgress(page) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> items[page].id }
        ) { page ->
            ViewerPage(item = items[page], vm = vm)
        }

        // 顶部渐变 + 返回/收藏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .padding(top = 12.dp, start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = items.getOrNull(pagerState.currentPage)?.displayName.orEmpty(),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            val current = items.getOrNull(pagerState.currentPage)
            val favorite = current != null && favoriteIds.contains(current.id)
            IconButton(
                onClick = { current?.let(vm::toggleFavorite) }
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) Color(0xFFE45C7B) else Color.White
                )
            }
        }

        // 底部页码指示
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0x52000000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ViewerPage(item: MediaItem, vm: ViewerViewModel) {
    val fileState = produceState<File?>(initialValue = null, item.id, item.lastModified) {
        value = vm.preview(item)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val file = fileState.value
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    text = if (item.isPresent) "正在解码…" else "源文件已丢失",
                    color = Color.White.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
        }
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
