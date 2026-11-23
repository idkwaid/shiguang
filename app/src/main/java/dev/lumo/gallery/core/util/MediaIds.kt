package dev.lumo.gallery.core.util

import java.security.MessageDigest

object MediaIds {

    /** 稳定 ID：同一图库内同一路径永远得到同一个 id。 */
    fun of(libraryId: String, locator: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest("$libraryId\u0000$locator".toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
