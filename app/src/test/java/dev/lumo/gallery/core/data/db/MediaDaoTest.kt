package dev.lumo.gallery.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class MediaDaoTest {

    private lateinit var db: LumoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LumoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun libraryEntity(id: String, name: String, isPrivate: Boolean = false) = LibraryEntity(
        id = id,
        type = "FOLDER",
        name = name,
        treeUri = "content://tree/$id",
        archiveUri = null,
        archiveFormat = null,
        displayPath = name,
        isPrivate = isPrivate,
        createdAt = 1L
    )

    private fun mediaEntity(
        id: String,
        libraryId: String,
        locator: String,
        name: String,
        favorite: Boolean = false,
        present: Boolean = true,
        mtime: Long = 1L
    ) = MediaItemEntity(
        id = id,
        libraryId = libraryId,
        locator = locator,
        docUri = "content://doc/$id",
        displayName = name,
        mime = "image/jpeg",
        sizeBytes = 10L,
        lastModified = mtime,
        dateTaken = null,
        dateSource = null,
        width = 0,
        height = 0,
        orientation = 0,
        note = null,
        isFavorite = favorite,
        isPresent = present,
        thumbReady = false,
        phash = null,
        addedAt = 1L,
        sortKey = dev.lumo.gallery.core.util.NaturalSortKey.of(name)
    )

    @Test
    fun favorites_exclude_private_libraries_but_keep_absent_items() = runTest {
        db.libraryDao().upsert(libraryEntity("pub", "公开"))
        db.libraryDao().upsert(libraryEntity("priv", "私密", isPrivate = true))
        db.mediaDao().upsertAll(
            listOf(
                mediaEntity("m1", "pub", "a/1.jpg", "1.jpg", favorite = true),
                mediaEntity("m2", "priv", "a/2.jpg", "2.jpg", favorite = true),
                mediaEntity("m3", "pub", "a/3.jpg", "3.jpg", favorite = true, present = false)
            )
        )

        val favorites = db.mediaDao().observeFavorites().first()
        assertEquals(2, favorites.size)
        assertTrue(favorites.any { it.id == "m1" })
        assertTrue(favorites.any { it.id == "m3" })
    }

    @Test
    fun scan_marks_missing_as_absent_and_preserves_favorite() = runTest {
        db.libraryDao().upsert(libraryEntity("lib", "相册"))
        db.mediaDao().upsertAll(
            listOf(
                mediaEntity("m1", "lib", "a/1.jpg", "1.jpg", favorite = true),
                mediaEntity("m2", "lib", "a/2.jpg", "2.jpg")
            )
        )

        db.mediaDao().markAllAbsent("lib")
        db.mediaDao().upsertAll(listOf(mediaEntity("m1", "lib", "a/1.jpg", "1.jpg", favorite = true)))

        val present = db.mediaDao().presentItems("lib")
        assertEquals(1, present.size)
        assertEquals("m1", present.first().id)
        assertTrue(present.first().isFavorite)
        assertEquals(1, db.mediaDao().countAbsentFavorites())
    }

    @Test
    fun cleanup_removes_only_absent_favorites() = runTest {
        db.libraryDao().upsert(libraryEntity("lib", "相册"))
        db.mediaDao().upsertAll(
            listOf(
                mediaEntity("ok", "lib", "a/1.jpg", "1.jpg", favorite = true),
                mediaEntity("lost", "lib", "a/2.jpg", "2.jpg", favorite = true, present = false)
            )
        )

        val removed = db.mediaDao().deleteAbsentFavorites()
        assertEquals(1, removed)
        assertNull(db.mediaDao().getById("lost"))
        assertEquals("ok", db.mediaDao().getById("ok")?.id)
    }

    @Test
    fun search_matches_chinese_and_escapes_wildcards() = runTest {
        db.libraryDao().upsert(libraryEntity("lib", "旅行相册"))
        db.mediaDao().upsertAll(
            listOf(
                mediaEntity("m1", "lib", "a/1.jpg", "海边日落.jpg"),
                mediaEntity("m2", "lib", "a/2.jpg", "100%纯色.jpg"),
                mediaEntity("m3", "lib", "a/3.jpg", "城市夜景.jpg")
            )
        )

        assertEquals(1, db.mediaDao().search("日落").first().size)
        assertEquals(1, db.mediaDao().search(dev.lumo.gallery.core.data.repo.MediaRepository.escapeLike("100%")).first().size)
        assertEquals(0, db.mediaDao().search(dev.lumo.gallery.core.data.repo.MediaRepository.escapeLike("100_")).first().size)
    }

    @Test
    fun natural_sort_key_orders_numbers() = runTest {
        db.libraryDao().upsert(libraryEntity("lib", "相册"))
        db.mediaDao().upsertAll(
            listOf(
                mediaEntity("m10", "lib", "a/10.jpg", "10.jpg"),
                mediaEntity("m2", "lib", "a/2.jpg", "2.jpg"),
                mediaEntity("m1", "lib", "a/1.jpg", "1.jpg")
            )
        )
        val items = db.mediaDao().presentItems("lib").map { it.displayName }
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), items)
    }

    @Test
    fun library_search_excludes_private() = runTest {
        db.libraryDao().upsert(libraryEntity("pub", "旅行"))
        db.libraryDao().upsert(libraryEntity("priv", "旅行私密", isPrivate = true))
        val hits = db.libraryDao().search("旅行").first()
        assertEquals(1, hits.size)
        assertEquals("pub", hits.first().id)
    }
}
