package dev.lumo.gallery.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LibraryEntity::class,
        MediaItemEntity::class,
        TagEntity::class,
        MediaTagEntity::class,
        ReadingProgressEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class LumoDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun progressDao(): ProgressDao

    companion object {
        private const val NAME = "lumo.db"

        fun build(context: Context): LumoDatabase =
            Room.databaseBuilder(context, LumoDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()

        /** v3 → v4：图库收藏标记。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE libraries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：层级图库（parentId）与扫描范围（scanMode）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE libraries ADD COLUMN parentId TEXT")
                db.execSQL("ALTER TABLE libraries ADD COLUMN scanMode TEXT NOT NULL DEFAULT 'RECURSIVE'")
            }
        }

        /** v1 → v2：文件夹图库支持“子树根路径”，用于按子目录拆分图库。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE libraries ADD COLUMN treeRootRelPath TEXT")
            }
        }
    }
}
