package dev.lumo.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0 冒烟测试：验证 JVM 单测链路与 BuildConfig 生成是否正常。
 */
class AppSmokeTest {

    @Test
    fun versionName_isNotNull() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
    }

    @Test
    fun applicationId_isStable() {
        assertEquals("dev.lumo.gallery", BuildConfig.APPLICATION_ID)
    }
}
