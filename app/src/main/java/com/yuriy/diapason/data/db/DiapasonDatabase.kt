package com.yuriy.diapason.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the per-session voice-group choice. Existing rows keep NULL ("not recorded"). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `voice_group` TEXT")
    }
}

/**
 * Single-table Room database for Diapason.
 *
 * Schema export is enabled so that schema JSON files are generated in
 * `app/schemas/`. Commit these alongside migrations so the schema history
 * is version-controlled and migrations can be validated automatically.
 *
 * Version history:
 *   1 — initial schema: sessions table
 *   2 — sessions.voice_group (nullable TEXT): the Male · Female · Not sure choice each
 *       session was recorded with; NULL for rows that predate it
 */
@Database(
    entities = [SessionEntity::class],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
