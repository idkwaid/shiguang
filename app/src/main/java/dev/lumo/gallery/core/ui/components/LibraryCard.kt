package dev.lumo.gallery.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.ui.LocalAppContainer

/** 首页图库卡片：封面 + 名称 + 右上角照片数 + 右下角收藏 + 状态徽标。 */
@Composable
fun LibraryCard(
    library: Library,
    cover: MediaItem?,
    scanning: ScanProgress?,
    modifier: Modifier = Modifier,
    childCount: Int = 0,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val container = LocalAppContainer.current
    val coverFile = cover?.let {
        container.thumbCache.existing(it.libraryId, it.id, it.lastModified)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = library.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (library.isArchive) Icons.Outlined.FolderZip else Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x66000000))
                            )
                        )
                )

                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )

                LibraryCountBadge(
                    count = library.itemCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )

                StatusBadge(library.scanStatus, Modifier.align(Alignment.TopStart))

                LibraryFavoriteButton(
                    favorite = library.isFavorite,
                    onToggle = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (library.isPrivate) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "私密图库",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            library.isPrivate -> "私密"
                            library.isArchive -> library.archiveFormat?.name ?: "压缩包"
                            else -> "文件夹"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (childCount > 0) {
                    Text(
                        text = "$childCount 个子图库",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (scanning != null && scanning.phase != ScanProgress.Phase.DONE) {
                val fraction = if (scanning.total > 0) scanning.fraction else null
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 图库网格里的图库卡片（首页/子图库/收藏图库共用）：
 * 封面右上角照片数、右下角收藏，点卡片进入图库。
 */
@Composable
fun LibraryTile(
    library: Library,
    cover: MediaItem?,
    modifier: Modifier = Modifier,
    childCount: Int = 0,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit
) {
    val container = LocalAppContainer.current
    val coverFile = cover?.let {
        container.thumbCache.existing(it.libraryId, it.id, it.lastModified)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.12f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (coverFile != null) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = library.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (library.isArchive) Icons.Outlined.FolderZip else Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            }

            LibraryCountBadge(
                count = library.itemCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
            LibraryFavoriteButton(
                favorite = library.isFavorite,
                onToggle = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
            if (library.isPrivate) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "私密图库",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(14.dp)
                )
            }
        }
        Text(
            text = library.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            text = if (childCount > 0) {
                "${library.itemCount} 张 · $childCount 个子图库"
            } else {
                "${library.itemCount} 张"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun LibraryCountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xB3000000),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = "$count",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun LibraryFavoriteButton(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x4D000000))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (favorite) "取消收藏图库" else "收藏图库",
            tint = if (favorite) Color(0xFFE45C7B) else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatusBadge(status: ScanStatus, modifier: Modifier = Modifier) {
    val (icon, text) = when (status) {
        ScanStatus.PERMISSION_LOST -> Icons.Outlined.WarningAmber to "需授权"
        ScanStatus.CORRUPT -> Icons.Outlined.WarningAmber to "损坏"
        ScanStatus.PASSWORD_REQUIRED -> Icons.Outlined.Lock to "待解锁"
        ScanStatus.EMPTY -> Icons.Outlined.Image to "空"
        ScanStatus.ERROR -> Icons.Outlined.WarningAmber to "异常"
        else -> return
    }
    Row(
        modifier = modifier
            .padding(8.dp)
            .background(Color(0xCC000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
