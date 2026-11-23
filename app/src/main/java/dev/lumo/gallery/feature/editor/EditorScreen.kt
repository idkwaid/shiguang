package dev.lumo.gallery.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.lumo.gallery.core.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    libraryId: String,
    itemId: String,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: EditorViewModel = viewModel(
        key = "editor-$libraryId-$itemId",
        factory = EditorViewModel.factory(container, libraryId, itemId)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.save() }, enabled = !state.saving) {
                        Text(if (state.saving) "保存中…" else "保存副本")
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0B0B0B)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.preview == null -> Text(
                        text = "无法读取图片",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    else -> AsyncImage(
                        model = state.preview,
                        contentDescription = "编辑预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = state.rotationQuarter * 90f
                                scaleX = if (state.flipHorizontal) -1f else 1f
                                scaleY = if (state.flipVertical) -1f else 1f
                            }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::rotate) {
                        Icon(Icons.Outlined.RotateRight, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("旋转")
                    }
                    TextButton(onClick = vm::flipHorizontal) {
                        Icon(Icons.Outlined.Flip, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("水平翻转")
                    }
                    TextButton(onClick = vm::flipVertical) {
                        Icon(Icons.Outlined.Flip, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("垂直翻转")
                    }
                }

                Text("裁剪比例", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = state.cropRatio == ratio,
                            onClick = { vm.setCropRatio(ratio) },
                            label = { Text(ratio.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.watermark,
                    onValueChange = vm::setWatermark,
                    label = { Text("文字水印（留空不加水印）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.watermark.isNotBlank()) {
                    Text("水印位置与透明度", style = MaterialTheme.typography.labelMedium)
                    WatermarkPositionPicker(
                        selected = state.watermarkPosition,
                        onSelect = vm::setWatermarkPosition
                    )
                    Slider(
                        value = state.watermarkOpacity,
                        onValueChange = vm::setWatermarkOpacity,
                        valueRange = 0.1f..1f
                    )
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = vm::setNote,
                    label = { Text("备注（只写进本地索引）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WatermarkPositionPicker(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until 3) {
                    val index = row * 3 + column
                    FilterChip(
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        label = { Text("·") }
                    )
                }
            }
        }
    }
}
