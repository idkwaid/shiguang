package dev.lumo.gallery.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.core.ui.components.EmptyState
import dev.lumo.gallery.core.ui.components.LibraryTile
import dev.lumo.gallery.core.ui.components.PhotoTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onOpenViewer: (libraryId: String, itemId: String) -> Unit,
    onOpenLibrary: (String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.factory(container))
    val items by vm.items.collectAsStateWithLifecycle()
    val libraries by vm.favoriteLibraries.collectAsStateWithLifecycle()
    val libraryCovers by vm.libraryCovers.collectAsStateWithLifecycle()
    val absentCount by vm.absentCount.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("收藏") },
                actions = {
                    if (absentCount > 0) {
                        IconButton(onClick = { vm.clearAbsent() }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清理失效收藏")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (items.isEmpty() && libraries.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "还没有收藏",
                    description = "点图库卡片或图片右下角的爱心，就会出现在这里。"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (libraries.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "收藏的图库 · ${libraries.size}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                        items(libraries, key = { "lib-${it.id}" }) { library ->
                            LibraryTile(
                                library = library,
                                cover = libraryCovers[library.coverItemId],
                                onFavoriteToggle = { vm.removeLibraryFavorite(library) },
                                onClick = { onOpenLibrary(library.id) }
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "收藏的图片 · ${items.size}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                        items(items, key = { "img-${it.id}" }) { item ->
                            PhotoTile(
                                item = item,
                                onClick = { onOpenViewer(item.libraryId, item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
