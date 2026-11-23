package dev.lumo.gallery.core.image

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class PHashTest {

    private fun checkerboard(seed: Int, size: Int = 32): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = kotlin.random.Random(seed)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val value = random.nextInt(256)
                bitmap.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        return bitmap
    }

    @Test
    fun identical_images_have_zero_distance() {
        val bitmap = checkerboard(1)
        val hash = PHash.dHash(bitmap)
        assertEquals(0, PHash.hamming(hash, PHash.dHash(bitmap)))
        bitmap.recycle()
    }

    @Test
    fun different_images_are_far_apart() {
        val a = PHash.dHash(checkerboard(1))
        val b = PHash.dHash(checkerboard(2))
        assertTrue(PHash.hamming(a, b) > PHash.DEFAULT_THRESHOLD)
        assertNotEquals(PHash.toHex(a), PHash.toHex(b))
    }

    @Test
    fun hex_round_trip() {
        val hash = -1234567890123L
        assertEquals(hash, PHash.fromHex(PHash.toHex(hash)))
        assertNull(PHash.fromHex(null))
        assertNull(PHash.fromHex("xyz"))
    }

    @Test
    fun hamming_is_symmetric() {
        assertEquals(3, PHash.hamming(0b1010L, 0b0001L))
        assertEquals(PHash.hamming(0b1010L, 0b0001L), PHash.hamming(0b0001L, 0b1010L))
    }
}
