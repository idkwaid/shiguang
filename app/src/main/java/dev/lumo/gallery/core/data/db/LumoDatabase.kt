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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()

        /** v1 → v2：文件夹图库支持“子树根路径”，用于按子目录拆分图库。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE libraries ADD COLUMN treeRootRelPath TEXT")
            }
        }
    }
}
