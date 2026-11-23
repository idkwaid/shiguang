package dev.lumo.gallery.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.lumo.gallery.LumoApp
import dev.lumo.gallery.core.ui.LocalAppContainer
import dev.lumo.gallery.feature.cleanup.CleanupScreen
import dev.lumo.gallery.feature.editor.EditorScreen
import dev.lumo.gallery.feature.explore.ExploreScreen
import dev.lumo.gallery.feature.memory.MemoryScreen
import dev.lumo.gallery.feature.memory.RandomScreen
import dev.lumo.gallery.feature.tags.TagDetailScreen
import dev.lumo.gallery.feature.tags.TagsScreen
import dev.lumo.gallery.feature.favorites.FavoritesScreen
import dev.lumo.gallery.feature.home.HomeScreen
import dev.lumo.gallery.feature.library.LibraryScreen
import dev.lumo.gallery.feature.logs.LogsScreen
import dev.lumo.gallery.feature.private.PrivateGateScreen
import dev.lumo.gallery.feature.search.SearchScreen
import dev.lumo.gallery.feature.settings.SettingsScreen
import dev.lumo.gallery.feature.viewer.ViewerScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    Tab(Routes.HOME, "图库", Icons.Outlined.PhotoLibrary),
    Tab(Routes.FAVORITES, "收藏", Icons.Outlined.FavoriteBorder),
    Tab(Routes.EXPLORE, "探索", Icons.Outlined.AutoAwesome),
    Tab(Routes.SETTINGS, "设置", Icons.Outlined.Settings)
)

@Composable
fun AppRoot() {
    val container = LumoApp.instance.container
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.tabs

    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    }

    CompositionLocalProvider(LocalAppContainer provides container) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val navPadding = if (showBottomBar) padding else PaddingValues(0.dp)
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(bottom = navPadding.calculateBottomPadding())
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenLibrary = { id -> navController.navigate(Routes.library(id)) },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenPrivate = { navController.navigate(Routes.PRIVATE) },
                        onOpenTags = { navController.navigate(Routes.TAGS) }
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        onOpenViewer = { libraryId, itemId ->
                            navController.navigate(Routes.viewer(libraryId, itemId))
                        }
                    )
                }
                composable(Routes.EXPLORE) {
                    ExploreScreen(
                        onOpenTags = { navController.navigate(Routes.TAGS) },
                        onOpenMemory = { navController.navigate(Routes.MEMORY) },
                        onOpenRandom = { navController.navigate(Routes.RANDOM) },
                        onOpenCleanup = { navController.navigate(Routes.CLEANUP) }
                    )
                }
                composable(Routes.TAGS) {
                    TagsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTag = { id, name -> navController.navigate(Routes.tagDetail(id, name)) }
                    )
                }
                composable(
                    route = Routes.TAG_DETAIL,
                    arguments = listOf(
                        navArgument("tagId") { type = NavType.LongType },
                        navArgument("tagName") { type = NavType.StringType }
                    )
                ) { entry ->
                    val tagId = entry.arguments?.getLong("tagId") ?: 0L
                    val tagName = android.net.Uri.decode(entry.arguments?.getString("tagName").orEmpty())
                    TagDetailScreen(
                        tagId = tagId,
                        tagName = tagName,
                        onBack = { navController.popBackStack() },
                        onOpenViewer = { libraryId, itemId ->
                            navController.navigate(Routes.viewer(libraryId, itemId))
                        }
                    )
                }
                composable(Routes.MEMORY) {
                    MemoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenViewer = { libraryId, itemId ->
                            navController.navigate(Routes.viewer(libraryId, itemId))
                        }
                    )
                }
                composable(Routes.PRIVATE) {
                    PrivateGateScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLibrary = { id -> navController.navigate(Routes.library(id)) }
                    )
                }
                composable(Routes.CLEANUP) {
                    CleanupScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.EDITOR,
                    arguments = listOf(
                        navArgument("libraryId") { type = NavType.StringType },
                        navArgument("itemId") { type = NavType.StringType }
                    )
                ) { entry ->
                    EditorScreen(
                        libraryId = entry.arguments?.getString("libraryId").orEmpty(),
                        itemId = entry.arguments?.getString("itemId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.RANDOM) {
                    RandomScreen(
                        onBack = { navController.popBackStack() },
                        onOpenViewer = { libraryId, itemId ->
                            navController.navigate(Routes.viewer(libraryId, itemId))
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenLogs = { navController.navigate(Routes.LOGS) })
                }
                composable(Routes.LOGS) {
                    LogsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLibrary = { id -> navController.navigate(Routes.library(id)) },
                        onOpenViewer = { libraryId, itemId ->
                            navController.navigate(Routes.viewer(libraryId, itemId))
                        },
                        onOpenTag = { id, name -> navController.navigate(Routes.tagDetail(id, name)) }
                    )
                }
                composable(
                    route = Routes.LIBRARY,
                    arguments = listOf(navArgument("libraryId") { type = NavType.StringType })
                ) { entry ->
                    val libraryId = entry.arguments?.getString("libraryId").orEmpty()
                    LibraryScreen(
                        libraryId = libraryId,
                        onBack = { navController.popBackStack() },
                        onOpenViewer = { lib, item ->
                            navController.navigate(Routes.viewer(lib, item))
                        },
                        onOpenPrivate = { navController.navigate(Routes.PRIVATE) },
                        onOpenEditor = { lib, item ->
                            navController.navigate(Routes.editor(lib, item))
                        }
                    )
                }
                composable(
                    route = Routes.VIEWER,
                    arguments = listOf(
                        navArgument("libraryId") { type = NavType.StringType },
                        navArgument("itemId") { type = NavType.StringType }
                    )
                ) { entry ->
                    ViewerScreen(
                        libraryId = entry.arguments?.getString("libraryId").orEmpty(),
                        itemId = entry.arguments?.getString("itemId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
