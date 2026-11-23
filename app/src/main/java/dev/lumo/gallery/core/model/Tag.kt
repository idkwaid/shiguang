package dev.lumo.gallery.core.model

/** 星标之外的自定义标签。 */
data class Tag(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long
)

/** 首页/图库里展示用的组合项：图片 + 所属图库信息（收藏、搜索结果共用）。 */
data class MediaWithLibrary(
    val item: MediaItem,
    val library: Library
)

/** 全局搜索结果的聚合。 */
data class SearchResults(
    val libraries: List<Library> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val tags: List<Tag> = emptyList()
) {
    val isEmpty: Boolean get() = libraries.isEmpty() && items.isEmpty() && tags.isEmpty()
}
