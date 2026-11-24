package dev.lumo.gallery.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.LocalAppContainer
import java.io.File
import kotlinx.coroutines.launch

/** 网格缩略图：16:10 视觉重量、圆角、收藏角标、失效占位。 */
@Composable
fun PhotoTile(
    item: MediaItem,
    modifier: Modifier = Modifier,
    showFavoriteBadge: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val container = LocalAppContainer.current
    val thumb: File? = remember(item.id, item.lastModified, item.thumbReady, item.isPresent) {
        if (!item.isPresent) null
        else container.thumbCache.existing(item.libraryId, item.id, item.lastModified)
    }
    val ratio = remember(item.width, item.height, item.orientation) { item.displayAspectRatio() }
    var localFavorite by remember(item.id, item.isFavorite) { mutableStateOf(item.isFavorite) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .aspectRatioSafe(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            thumb != null -> AsyncImage(
                model = thumb,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            !item.isPresent -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "源文件已丢失",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(26.dp)
            )

            item.thumbReady -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "无法解码",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(26.dp)
            )

            else -> Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "生成缩略图中",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.size(26.dp)
            )
        }

        if (showFavoriteBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x4D000000))
                    .clickable {
                        val next = !localFavorite
                        localFavorite = next
                        scope.launch {
                            container.mediaRepository.setFavorite(item.id, next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (localFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (localFavorite) "取消收藏" else "收藏",
                    tint = if (localFavorite) Color(0xFFE45C7B) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun Modifier.aspectRatioSafe(ratio: Float): Modifier =
    this.aspectRatio(ratio.coerceIn(0.45f, 2.4f))

private fun MediaItem.displayAspectRatio(): Float {
    if (width <= 0 || height <= 0) return 1f
    val rotated = orientation == 6 || orientation == 8 || orientation == 90 || orientation == 270
    val w = if (rotated) height else width
    val h = if (rotated) width else height
    if (h <= 0) return 1f
    return w.toFloat() / h
}
