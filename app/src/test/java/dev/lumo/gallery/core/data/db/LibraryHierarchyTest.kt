package dev.lumo.gallery.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 层级图库：首页只看顶层，子库/子库计数通过 parentId 查询。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class LibraryHierarchyTest {

    private lateinit var db: LumoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LumoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun library(id: String, name: String, parentId: String? = null, private: Boolean = false) =
        LibraryEntity(
            id = id,
            type = "FOLDER",
            name = name,
            treeUri = "content://tree/root",
            treeRootRelPath = name,
            parentId = parentId,
            scanMode = "DIRECT",
            archiveUri = null,
            archiveFormat = null,
            displayPath = name,
            isPrivate = private,
            createdAt = 1L
        )

    @Test
    fun roots_hide_children_and_track_counts() = runTest {
        db.libraryDao().upsert(library("root", "ben"))
        db.libraryDao().upsert(library("a", "xxx", parentId = "root"))
        db.libraryDao().upsert(library("b", "yyy", parentId = "root"))
        db.libraryDao().upsert(library("a1", "deep", parentId = "a"))

        val roots = db.libraryDao().observeVisibleRoots().first()
        assertEquals(1, roots.size)
        assertEquals("root", roots.first().id)

        val children = db.libraryDao().getChildren("root")
        assertEquals(2, children.size)
        assertTrue(children.all { it.parentId == "root" })

        val counts = db.libraryDao().observeChildCounts().first().associate { it.parentId to it.count }
        assertEquals(2, counts["root"])
        assertEquals(1, counts["a"])
    }

    @Test
    fun duplicate_detection_ignores_child_nodes() = runTest {
        db.libraryDao().upsert(library("root", "ben"))
        db.libraryDao().upsert(library("a", "xxx", parentId = "root"))

        assertNotNull(db.libraryDao().findAnyTopFolderByUri("content://tree/root"))
        // 子节点不是顶层，不应被当作“重复导入”的根
        val top = db.libraryDao().findAnyTopFolderByUri("content://tree/root")
        assertEquals("root", top?.id)
    }

    @Test
    fun private_hides_entire_root_from_home() = runTest {
        db.libraryDao().upsert(library("root", "私密", private = true))
        db.libraryDao().upsert(library("a", "child", parentId = "root"))

        assertEquals(0, db.libraryDao().observeVisibleRoots().first().size)
        assertEquals(1, db.libraryDao().observePrivate().first().size)
    }
}
