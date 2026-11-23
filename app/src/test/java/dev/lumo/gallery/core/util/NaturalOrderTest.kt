package dev.lumo.gallery.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {

    private fun assertOrdered(vararg names: String) {
        val sorted = names.toList().shuffled(kotlin.random.Random(42)).sortedWith(NaturalOrder)
        assertEquals(names.toList(), sorted)
    }

    @Test
    fun numbers_compare_numerically() {
        assertOrdered("1", "2", "10")
        assertOrdered("1.jpg", "2.jpg", "10.jpg")
        assertOrdered("img2", "img10", "img20")
    }

    @Test
    fun multi_number_names() {
        assertOrdered("a1b2", "a1b10", "a2", "a10")
        assertOrdered("第1章2节", "第1章10节", "第2章1节")
    }

    @Test
    fun leading_zeros_fewer_first() {
        assertOrdered("1", "01", "001")
        assertOrdered("a1", "a01", "a001")
    }

    @Test
    fun chinese_names() {
        assertOrdered("照片1", "照片2", "照片10", "照片100")
    }

    @Test
    fun case_insensitive_but_stable() {
        val list = listOf("B", "a", "A", "b").sortedWith(NaturalOrder)
        // 小写后 a<b，同字母大写在前
        assertEquals(listOf("A", "a", "B", "b"), list)
    }

    @Test
    fun prefix_shorter_first() {
        assertOrdered("cat", "cat1", "cat2", "cat10")
    }

    @Test
    fun extension_compare() {
        assertOrdered("1.jpeg", "1.jpg", "2.jpg")
    }

    @Test
    fun sort_key_agrees_with_comparator() {
        val names = listOf(
            "1", "01", "001", "2", "10", "img2", "img10", "IMG3",
            "a1b2", "a1b10", "照片1", "照片2", "照片10", "cat", "cat1",
            "cat2", "cat10", "1.jpeg", "1.jpg", "2.jpg", "B", "a", "A", "b"
        )
        val byComparator = names.sortedWith(NaturalOrder)
        val byKey = names.sortedWith(
            compareBy<String>({ NaturalSortKey.of(it) }, { it })
        )
        assertEquals(byComparator, byKey)
    }

    @Test
    fun sort_key_prefix_order() {
        assertTrue(NaturalSortKey.of("a1") < NaturalSortKey.of("a2"))
        assertTrue(NaturalSortKey.of("a2") < NaturalSortKey.of("a10"))
        assertTrue(NaturalSortKey.of("照片2") < NaturalSortKey.of("照片10"))
        assertTrue(NaturalSortKey.of("Cat") < NaturalSortKey.of("cat1"))
    }

    @Test
    fun empty_and_null_are_stable() {
        val list = listOf("b", "", "a").sortedWith(NaturalOrder)
        assertEquals(listOf("", "a", "b"), list)
        assertTrue(NaturalOrder.compare(null, "a") < 0)
        assertEquals(0, NaturalOrder.compare(null, null))
    }
}
