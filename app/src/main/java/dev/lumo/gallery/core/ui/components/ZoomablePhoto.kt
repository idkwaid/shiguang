package dev.lumo.gallery.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/**
 * 全屏查看里的缩放容器：
 * - 双指捏合 1x–6x、拖拽查看局部；
 * - 双击在 1x / 2.5x 间切换；
 * - 1x 时不消费横向手势，交给 Pager 翻页；
 * - 不再做“双指长按回原图”，避免缩放过程中被误触重置。
 */
@Composable
fun ZoomablePhoto(
    file: File?,
    contentDescription: String?,
    resetKey: Any,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onZoomChanged: (Float) -> Unit
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(value: Offset, s: Float): Offset {
        if (s <= 1.01f || containerSize.width == 0) return Offset.Zero
        val maxX = containerSize.width * (s - 1f) / 2f
        val maxY = containerSize.height * (s - 1f) / 2f
        return Offset(
            value.x.coerceIn(-maxX, maxX),
            value.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoomChanged(scale)
                    }
                )
            }
            .pointerInput(resetKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                // 注意：这里不再触发任何“长按对比”，缩放中手指移动只更新缩放/位移
                                val next = (scale * zoom).coerceIn(1f, 6f)
                                scale = next
                                offset = if (next <= 1.01f) Offset.Zero else clamp(offset + pan, next)
                                onZoomChanged(next)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (scale > 1.01f) {
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) {
                                offset = clamp(offset + pan, scale)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    Text(
                        text = "正在解码…",
                        color = Color.White.copy(alpha = 0.7f),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
