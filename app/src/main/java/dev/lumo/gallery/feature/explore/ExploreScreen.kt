package dev.lumo.gallery.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.lumo.gallery.core.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("探索") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = "探索功能即将上线",
                description = "标签聚合、那年今日、全局随机回忆会在后续里程碑里接进来。"
            )
        }
    }
}
