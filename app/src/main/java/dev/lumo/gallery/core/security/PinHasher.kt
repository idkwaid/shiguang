package dev.lumo.gallery.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN 派生：PBKDF2-HMAC-SHA256，150k 迭代，16 字节盐，256 位输出。
 * 只存盐与哈希，不存明文；忘记密码只能重置（清空私密索引）。
 */
object PinHasher {

    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = java.util.Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(pin: String, saltBase64: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(pin, saltBase64).toByteArray(),
            expectedHash.toByteArray()
        )
}
