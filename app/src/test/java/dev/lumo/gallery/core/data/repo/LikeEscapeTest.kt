package dev.lumo.gallery.core.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class LikeEscapeTest {

    @Test
    fun escapes_wildcards_and_backslash() {
        assertEquals("100%", "100\\%".replace("\\%", "%"))
        assertEquals("100\\%", MediaRepository.escapeLike("100%"))
        assertEquals("a\\_b", MediaRepository.escapeLike("a_b"))
        assertEquals("a\\\\b", MediaRepository.escapeLike("a\\b"))
    }

    @Test
    fun keeps_normal_text() {
        assertEquals("照片 2024", MediaRepository.escapeLike("照片 2024"))
        assertEquals("", MediaRepository.escapeLike(""))
    }
}
