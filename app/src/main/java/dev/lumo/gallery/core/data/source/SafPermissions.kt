package dev.lumo.gallery.core.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * SAF 授权状态的读写与检查。所有需要跨重启使用的目录授权都走这里。
 */
class SafPermissions(private val context: Context) {

    fun hasReadPermission(uri: Uri): Boolean {
        val mode = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), mode) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    fun hasWritePermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    fun takePersistable(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun release(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun treeOf(uri: Uri): String = DocumentsContract.getTreeDocumentId(uri)
}
