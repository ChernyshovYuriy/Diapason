package com.yuriy.diapason.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.diapason.data.db.DiapasonDatabase
import com.yuriy.diapason.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room migrations, validated against the exported schemas in `app/schemas/` (wired in as
 * test assets in build.gradle.kts). A failed migration on a published app either crashes
 * on launch or — with a destructive fallback — wipes every user's history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DiapasonDatabase::class.java,
    )

    @Test
    fun `migration 1 to 2 keeps existing sessions and leaves their voice group null`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO sessions (id, timestamp_ms, duration_s, detected_min_hz, detected_max_hz,
                    comfortable_low_hz, comfortable_high_hz, passaggio_hz, sample_count,
                    top_fach_key, top_fach_score, top_fach_max_score, is_partial)
                VALUES ('old-1', 1700000000000, 30.0, 110.0, 392.0, 130.0, 330.0, 300.0, 80,
                    'fach_name_lyric_baritone', 11, 14, 0)
                """.trimIndent()
            )
            close()
        }

        // Validates the migrated schema against 2.json and throws on any mismatch.
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT id, sample_count, voice_group FROM sessions").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("old-1", cursor.getString(0))
            assertEquals(80, cursor.getInt(1))
            assertNull("pre-v2 rows have no recorded choice", cursor.getString(2))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
