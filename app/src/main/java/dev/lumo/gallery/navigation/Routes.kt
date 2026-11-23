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
    const val CLEANUP = "cleanup"
    const val EDITOR = "editor/{libraryId}/{itemId}"
    const val PRIVATE = "private"
    const val LOGS = "logs"
    const val VIEWER = "viewer/{libraryId}/{itemId}"

    fun library(libraryId: String) = "library/$libraryId"
    fun viewer(libraryId: String, itemId: String) = "viewer/$libraryId/$itemId"
    fun tagDetail(tagId: Long, name: String) = "tag/$tagId/${android.net.Uri.encode(name)}"
    fun editor(libraryId: String, itemId: String) = "editor/$libraryId/$itemId"

    val tabs = setOf(HOME, FAVORITES, EXPLORE, SETTINGS)
}
