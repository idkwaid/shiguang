package dev.lumo.gallery.core.security

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * 私密界面防截屏：FLAG_SECURE（常规截屏/录屏显示黑屏）+ API 33 最近任务预览隐藏。
 */
@Composable
fun SecureScreen(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { activity?.setRecentsScreenshotEnabled(false) }
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { activity?.setRecentsScreenshotEnabled(true) }
            }
        }
    }
}
