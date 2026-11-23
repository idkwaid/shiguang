package dev.lumo.gallery.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.LibraryType
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.model.ScanStatus
import dev.lumo.gallery.core.model.SortMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归测试：inJustDecodeBounds 时 decodeStream 必然返回 null，
 * 旧实现把它当成“打开流失败”，导致所有缩略图/预览都为 null。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class ThumbnailerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun jpegBytes(width: Int = 400, height: Int = 300): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(200, 60, 60))
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun library() = Library(
        id = "lib-1",
        type = LibraryType.FOLDER,
        name = "测试图库",
        treeUri = "content://tree/lib-1",
        archiveUri = null,
        archiveFormat = null,
        displayPath = "测试图库",
        isPrivate = false,
        isAvailable = true,
        sortMode = SortMode.NATURAL,
        createdAt = 1L,
        lastScanAt = 1L,
        coverItemId = null,
        itemCount = 1,
        scanStatus = ScanStatus.OK,
        lastError = null
    )

    private fun item(imgBytes: ByteArray) = MediaItem(
        id = "item-1",
        libraryId = "lib-1",
        locator = "a.jpg",
        docUri = "content://doc/a.jpg",
        displayName = "a.jpg",
        mime = "image/jpeg",
        sizeBytes = imgBytes.size.toLong(),
        lastModified = 100L,
        dateTaken = null,
        dateSource = null,
        width = 0,
        height = 0,
        orientation = 0,
        note = null,
        isFavorite = false,
        isPresent = true,
        thumbReady = false,
        phash = null,
        addedAt = 1L
    )

    @Test
    fun generates_thumbnail_for_valid_jpeg() = runTest {
        val bytes = jpegBytes()
        val provider = object : MediaStreamProvider {
            override suspend fun open(library: Library, item: MediaItem): InputStream =
                ByteArrayInputStream(bytes)
        }
        val cache = ThumbCache(context)
        val thumbnailer = Thumbnailer(context, cache, Dispatchers.Unconfined)

        val result = thumbnailer.generate(library(), item(bytes), provider)

        assertNotNull("缩略图不应为 null（回归：inJustDecodeBounds 返回值被误判）", result)
        assertEquals(400, result!!.width)
        assertEquals(300, result.height)
        assertTrue(result.file.isFile && result.file.length() > 0)

        val decoded = BitmapFactory.decodeFile(result.file.absolutePath)
        assertNotNull(decoded)
        decoded?.recycle()
    }

}
