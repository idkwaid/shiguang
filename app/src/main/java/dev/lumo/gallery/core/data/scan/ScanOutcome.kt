package dev.lumo.gallery.core.data.scan

/** 扫描结果，供 UI 决策提示。 */
sealed interface ScanOutcome {
    data class Success(val itemCount: Int) : ScanOutcome
    data object Empty : ScanOutcome
    data object PermissionLost : ScanOutcome
    data class Failure(val message: String) : ScanOutcome
}
