package dev.lumo.gallery.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LibraryEntity::class,
        MediaItemEntity::class,
        TagEntity::class,
        MediaTagEntity::class,
        ReadingProgressEntity::class
    ],
    version = 1,
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
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
    }
}
