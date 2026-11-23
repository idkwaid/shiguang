package dev.lumo.gallery.core.data.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import dev.lumo.gallery.core.util.AppLog

/**
 * SAF 授权状态的读写与检查。所有需要跨重启使用的目录授权都走这里。
 *
 * 注意：刚通过系统选择器选完时拿到的是**临时授权**，`checkUriPermission` 会返回 GRANTED，
 * 但进程被杀后就失效。因此导入流程必须**无条件**调用 [takePersistable]，
 * 不能因为“当前有权限”就跳过。
 */
class SafPermissions(private val context: Context) {

    fun hasReadPermission(uri: Uri): Boolean {
        val mode = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), mode) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!persisted) {
            AppLog.w("SafPermission", "无读授权: $uri")
        }
        return persisted
    }

    fun hasWritePermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    /**
     * 持久化授权（幂等）。优先 READ|WRITE，失败则退化为只读；
     * 返回最终是否成功，并写入日志便于排查“重启后要重新授权”。
     */
    fun takePersistable(uri: Uri, write: Boolean): Boolean {
        val resolver = context.contentResolver
        val readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val writeFlags = if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        val result = runCatching {
            resolver.takePersistableUriPermission(uri, readFlags or writeFlags)
        }
        if (result.isFailure && write) {
            AppLog.w(
                "SafPermission",
                "READ|WRITE 持久化失败，尝试只读: $uri, ${result.exceptionOrNull()?.message}"
            )
            runCatching { resolver.takePersistableUriPermission(uri, readFlags) }
        }
        val persisted = resolver.persistedUriPermissions
        val entry = persisted.firstOrNull { it.uri == uri }
        AppLog.i(
            "SafPermission",
            "takePersistable uri=$uri write=$write ok=${entry != null} " +
                "permCount=${persisted.size} read=${entry?.isReadPermission} write=${entry?.isWritePermission}"
        )
        persisted.take(20).forEach { perm ->
            AppLog.d("SafPermission", "已持久化: ${perm.uri}")
        }
        return entry?.isReadPermission == true
    }

    fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            AppLog.i("SafPermission", "释放授权: $uri")
        }
    }

    fun treeOf(uri: Uri): String = DocumentsContract.getTreeDocumentId(uri)

    fun logCurrentPermissions() {
        val persisted = context.contentResolver.persistedUriPermissions
        AppLog.i("SafPermission", "当前持久化授权数量: ${persisted.size}")
        persisted.forEach { AppLog.d("SafPermission", "  ${it.uri}") }
    }
}
