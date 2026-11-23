package dev.lumo.gallery.core.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanProgress
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.ui.LocalAppContainer

/** 首页图库卡片：封面 + 名称 + 数量 + 状态徽标。 */
@Composable
fun LibraryCard(
    library: Library,
    cover: MediaItem?,
    scanning: ScanProgress?,
    modifier: Modifier = Modifier,
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
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (library.isArchive) Icons.Outlined.FolderZip else Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 底部信息渐变
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color(0x66000000))
                            )
                        )
                )

                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )

                if (library.isPrivate) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "私密图库",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp)
                    )
                }

                StatusBadge(library.scanStatus, Modifier.align(Alignment.TopStart))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${library.itemCount} 张",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (library.isArchive) {
                    Text(
                        text = library.archiveFormat?.name ?: "压缩包",
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
            .background(
                androidx.compose.ui.graphics.Color(0xCC000000),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}
