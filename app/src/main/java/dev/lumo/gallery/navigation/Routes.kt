package dev.lumo.gallery.navigation

object Routes {
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val EXPLORE = "explore"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val LIBRARY = "library/{libraryId}"
    const val TAGS = "tags"
    const val TAG_DETAIL = "tag/{tagId}/{tagName}"
    const val MEMORY = "memory"
    const val RANDOM = "random"
    const val VIEWER = "viewer/{libraryId}/{itemId}"

    fun library(libraryId: String) = "library/$libraryId"
    fun viewer(libraryId: String, itemId: String) = "viewer/$libraryId/$itemId"
    fun tagDetail(tagId: Long, name: String) = "tag/$tagId/${android.net.Uri.encode(name)}"

    val tabs = setOf(HOME, FAVORITES, EXPLORE, SETTINGS)
}
