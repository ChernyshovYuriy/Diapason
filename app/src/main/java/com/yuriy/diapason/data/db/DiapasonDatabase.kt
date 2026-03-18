package com.yuriy.diapason.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single-table Room database for Diapason.
 *
 * Schema export is enabled so that schema JSON files are generated in
 * `app/schemas/`. Commit these alongside migrations so the schema history
 * is version-controlled and migrations can be validated automatically.
 *
 * Version history:
 *   1 — initial schema: sessions table
 */
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DiapasonDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {

        @Volatile
        private var INSTANCE: DiapasonDatabase? = null

        fun getInstance(context: Context): DiapasonDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiapasonDatabase::class.java,
                    "diapason.db",
                ).build().also { INSTANCE = it }
            }
    }
}
