package dev.lumo.gallery.core.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("lumo-log-test").toFile()
    }

    @Test
    fun writes_reads_and_clears() {
        AppLog.initForTest(dir, maxBytesLimit = 1_000_000)
        AppLog.i("Test", "hello-there")
        val tail = AppLog.readTail(50)
        assertTrue("应包含写入内容: $tail", tail.contains("hello-there"))

        AppLog.clear()
        assertEquals("还没有日志", AppLog.readTail())
        assertTrue(AppLog.file()?.exists() != true)
    }

    @Test
    fun rotates_when_file_exceeds_limit() {
        AppLog.initForTest(dir, maxBytesLimit = 300)
        repeat(40) { AppLog.i("Test", "line-$it-" + "x".repeat(40)) }

        assertTrue(File(dir, "lumo.1.log").isFile)
        val tail = AppLog.readTail(20)
        assertTrue(tail.contains("line-39"))
    }

    @Test
    fun throwable_is_included_in_line() {
        AppLog.initForTest(dir, maxBytesLimit = 1_000_000)
        AppLog.e("Test", "boom", IllegalStateException("bad-state"))
        val tail = AppLog.readTail(300)
        assertTrue(tail.contains("boom"))
        assertTrue(tail.contains("bad-state"))
    }
}
