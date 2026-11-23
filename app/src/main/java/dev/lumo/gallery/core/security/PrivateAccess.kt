package dev.lumo.gallery.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 私密空间的解锁状态：只存在内存，进程被杀或 App 退到后台即上锁。
 */
class PrivateAccess {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var failedAttempts = 0
    private var lockedUntil = 0L

    val isUnlocked: Boolean get() = _unlocked.value

    fun unlock() {
        failedAttempts = 0
        lockedUntil = 0L
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    fun remainingLockSeconds(): Int {
        val remain = lockedUntil - System.currentTimeMillis()
        return if (remain <= 0) 0 else ((remain + 999) / 1000).toInt()
    }

    /** 返回 true 表示允许尝试；false 表示冷却中。 */
    fun registerFailure(): Int {
        failedAttempts++
        if (failedAttempts >= 5) {
            lockedUntil = System.currentTimeMillis() + 30_000L
            failedAttempts = 0
        }
        return remainingLockSeconds()
    }

    fun resetFailures() {
        failedAttempts = 0
        lockedUntil = 0L
    }
}
