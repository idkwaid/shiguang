package dev.lumo.gallery.core.model

/** 图库来源类型。 */
enum class LibraryType { FOLDER, ARCHIVE }

/** 支持的压缩包格式。 */
enum class ArchiveFormat { ZIP, RAR, SEVEN_Z }

/** 图库内排序方式。 */
enum class SortMode { NATURAL, DATE_DESC, NAME_ASC }

/** 最近一次扫描结果，用于首页与图库页的状态提示。 */
enum class ScanStatus {
    NEVER,
    OK,
    EMPTY,
    PERMISSION_LOST,
    CORRUPT,
    PASSWORD_REQUIRED,
    ERROR
}

/** 时间来源：EXIF 拍摄时间优先，缺失时回退文件修改时间。 */
enum class DateSource { EXIF, FILE }
