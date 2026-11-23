package dev.lumo.gallery.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdsTest {

    @Test
    fun id_is_stable_and_library_scoped() {
        val a = MediaIds.of("lib-1", "a/b/1.jpg")
        val b = MediaIds.of("lib-1", "a/b/1.jpg")
        val c = MediaIds.of("lib-2", "a/b/1.jpg")
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(40, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun separator_avoids_collisions() {
        assertNotEquals(MediaIds.of("a", "b/c"), MediaIds.of("a/b", "c"))
    }

    @Test
    fun sha256_matches_known_vector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            MediaIds.sha256Hex(ByteArray(0))
        )
    }
}
