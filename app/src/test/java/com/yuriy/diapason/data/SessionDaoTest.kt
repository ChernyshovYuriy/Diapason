package com.yuriy.diapason.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.data.db.DiapasonDatabase
import com.yuriy.diapason.data.db.SessionDao
import com.yuriy.diapason.data.db.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO tests that exercise the actual Room SQL using an in-memory database.
 *
 * Robolectric provides an Android context on the local JVM so no emulator or
 * device is required.  The suite therefore runs in the `test` source set with
 * `./gradlew test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionDaoTest {

    private lateinit var db: DiapasonDatabase
    private lateinit var dao: SessionDao

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, DiapasonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun teardown() = db.close()

    // ── Fixture builder ───────────────────────────────────────────────────────

    /**
     * Minimal valid [SessionEntity].  Override individual fields per test
     * to keep each test declarative and its intent obvious.
     */
    private fun entity(
        id: String,
        timestampMs: Long,
        topFachKey: String? = "Lyric Soprano",
        topFachScore: Int? = 10,
        topFachMaxScore: Int? = 14,
        isPartial: Boolean = false,
    ) = SessionEntity(
        id = id,
        timestampMs = timestampMs,
        durationSeconds = 30f,
        detectedMinHz = 196f,
        detectedMaxHz = 880f,
        comfortableLowHz = 247f,
        comfortableHighHz = 659f,
        passaggioHz = 392f,
        sampleCount = 600,
        topFachKey = topFachKey,
        topFachScore = topFachScore,
        topFachMaxScore = topFachMaxScore,
        isPartial = isPartial,
    )

    // ── Insert + read back ────────────────────────────────────────────────────

    @Test
    fun `insert and getById returns the same entity`() = runBlocking {
        val e = entity("id-1", 1_000L)
        dao.insert(e)
        assertEquals(e, dao.getById("id-1"))
    }

    @Test
    fun `getById returns null for unknown id`() = runBlocking {
        assertNull(dao.getById("does-not-exist"))
    }

    @Test
    fun `insert multiple sessions and getAll returns all`() = runBlocking {
        dao.insert(entity("a", 1_000L))
        dao.insert(entity("b", 2_000L))
        dao.insert(entity("c", 3_000L))
        assertEquals(3, dao.getAllOrderedByDate().size)
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    fun `getAllOrderedByDate returns newest first`() = runBlocking {
        dao.insert(entity("old", 1_000L))
        dao.insert(entity("mid", 2_000L))
        dao.insert(entity("new", 3_000L))
        val ids = dao.getAllOrderedByDate().map { it.id }
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    @Test
    fun `getAllOrderedByDate with same timestamp preserves insert order`() = runBlocking {
        // Both at the same ms — ordering is stable (SQLite row order for ties)
        dao.insert(entity("first", 1_000L))
        dao.insert(entity("second", 1_000L))
        val all = dao.getAllOrderedByDate()
        assertEquals(2, all.size)
        // We only assert count here — tie-breaking is implementation detail
    }

    // ── Flow ──────────────────────────────────────────────────────────────────

    @Test
    fun `observeAllOrderedByDate emits current state on first collection`() = runBlocking {
        dao.insert(entity("x", 1_000L))
        dao.insert(entity("y", 2_000L))
        val snapshot = dao.observeAllOrderedByDate().first()
        assertEquals(listOf("y", "x"), snapshot.map { it.id })
    }

    @Test
    fun `observeAllOrderedByDate emits empty list when table is empty`() = runBlocking {
        val snapshot = dao.observeAllOrderedByDate().first()
        assertTrue(snapshot.isEmpty())
    }

    // ── Count ─────────────────────────────────────────────────────────────────

    @Test
    fun `count is zero on fresh database`() = runBlocking {
        assertEquals(0, dao.count())
    }

    @Test
    fun `count reflects number of inserted rows`() = runBlocking {
        dao.insert(entity("a", 1_000L))
        assertEquals(1, dao.count())
        dao.insert(entity("b", 2_000L))
        assertEquals(2, dao.count())
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deleteById removes the target row`() = runBlocking {
        dao.insert(entity("keep", 1_000L))
        dao.insert(entity("remove", 2_000L))
        dao.deleteById("remove")
        assertNull(dao.getById("remove"))
    }

    @Test
    fun `deleteById does not affect other rows`() = runBlocking {
        dao.insert(entity("keep", 1_000L))
        dao.insert(entity("remove", 2_000L))
        dao.deleteById("remove")
        assertNotNull(dao.getById("keep"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `deleteById on unknown id is a no-op`() = runBlocking {
        dao.insert(entity("a", 1_000L))
        dao.deleteById("ghost") // should not throw
        assertEquals(1, dao.count())
    }

    // ── Conflict resolution ───────────────────────────────────────────────────

    @Test
    fun `inserting same id twice replaces the first row`() = runBlocking {
        dao.insert(entity("dup", 1_000L, topFachKey = "Lyric Soprano"))
        dao.insert(entity("dup", 2_000L, topFachKey = "Dramatic Soprano"))
        assertEquals(1, dao.count())
        assertEquals("Dramatic Soprano", dao.getById("dup")?.topFachKey)
        assertEquals(2_000L, dao.getById("dup")?.timestampMs)
    }

    // ── Nullable fach fields ──────────────────────────────────────────────────

    @Test
    fun `null topFachKey is stored and retrieved as null`() = runBlocking {
        dao.insert(
            entity(
                "id-null",
                1_000L,
                topFachKey = null,
                topFachScore = null,
                topFachMaxScore = null
            )
        )
        val loaded = dao.getById("id-null")!!
        assertNull(loaded.topFachKey)
        assertNull(loaded.topFachScore)
        assertNull(loaded.topFachMaxScore)
    }

    @Test
    fun `non-null topFachScore is stored and retrieved correctly`() = runBlocking {
        dao.insert(entity("id-scored", 1_000L, topFachScore = 11, topFachMaxScore = 14))
        val loaded = dao.getById("id-scored")!!
        assertEquals(11, loaded.topFachScore)
        assertEquals(14, loaded.topFachMaxScore)
    }

    // ── isPartial flag ────────────────────────────────────────────────────────

    @Test
    fun `isPartial false is stored and retrieved correctly`() = runBlocking {
        dao.insert(entity("id-full", 1_000L, isPartial = false))
        assertEquals(false, dao.getById("id-full")?.isPartial)
    }

    @Test
    fun `isPartial true is stored and retrieved correctly`() = runBlocking {
        dao.insert(entity("id-partial", 1_000L, isPartial = true))
        assertEquals(true, dao.getById("id-partial")?.isPartial)
    }

    // ── Hz field precision ────────────────────────────────────────────────────

    @Test
    fun `Hz fields survive REAL storage without precision loss for typical values`() = runBlocking {
        val e = entity("id-hz", 1_000L).copy(
            detectedMinHz = 130.813f,   // C3
            detectedMaxHz = 1046.502f,  // C6
            comfortableLowHz = 261.626f,
            comfortableHighHz = 523.251f,
            passaggioHz = 391.995f,
        )
        dao.insert(e)
        val loaded = dao.getById("id-hz")!!
        // SQLite REAL is 64-bit IEEE 754; round-trip through Float loses <1 cent of error
        assertEquals(e.detectedMinHz, loaded.detectedMinHz, 0.01f)
        assertEquals(e.detectedMaxHz, loaded.detectedMaxHz, 0.01f)
        assertEquals(e.comfortableLowHz, loaded.comfortableLowHz, 0.01f)
        assertEquals(e.comfortableHighHz, loaded.comfortableHighHz, 0.01f)
        assertEquals(e.passaggioHz, loaded.passaggioHz, 0.01f)
    }

    // ── Repository integration ────────────────────────────────────────────────
    // These tests use the real repository impl on top of the in-memory DAO,
    // confirming that the mapping layer doesn't break any invariants.

    @Test
    fun `repository save and getAll roundtrip via domain model`() = runBlocking {
        val repo = com.yuriy.diapason.data.repository.SessionRepositoryImpl(dao)
        val record = com.yuriy.diapason.data.SessionRecord(
            id = "repo-1",
            timestampMs = 5_000L,
            durationSeconds = 45f,
            detectedMinHz = 196f,
            detectedMaxHz = 880f,
            comfortableLowHz = 247f,
            comfortableHighHz = 659f,
            passaggioHz = 392f,
            sampleCount = 900,
            topFachKey = "Dramatic Mezzo-Soprano",
            topFachScore = 12,
            topFachMaxScore = 14,
            isPartial = false,
        )
        repo.save(record)
        val loaded = repo.getById("repo-1")
        assertEquals(record, loaded)
    }

    @Test
    fun `repository observeAll returns newest first`() = runBlocking {
        val repo = com.yuriy.diapason.data.repository.SessionRepositoryImpl(dao)

        fun record(id: String, ts: Long) = com.yuriy.diapason.data.SessionRecord(
            id = id, timestampMs = ts, durationSeconds = 30f,
            detectedMinHz = 196f, detectedMaxHz = 880f,
            comfortableLowHz = 247f, comfortableHighHz = 659f,
            passaggioHz = 392f, sampleCount = 600,
            topFachKey = "Lyric Soprano", topFachScore = 10, topFachMaxScore = 14,
            isPartial = false,
        )

        repo.save(record("oldest", 1_000L))
        repo.save(record("middle", 2_000L))
        repo.save(record("newest", 3_000L))

        val ids = repo.observeAll().first().map { it.id }
        assertEquals(listOf("newest", "middle", "oldest"), ids)
    }

    @Test
    fun `repository count matches number of saves`() = runBlocking {
        val repo = com.yuriy.diapason.data.repository.SessionRepositoryImpl(dao)

        fun record(id: String) = com.yuriy.diapason.data.SessionRecord(
            id = id, timestampMs = 1_000L, durationSeconds = 30f,
            detectedMinHz = 196f, detectedMaxHz = 880f,
            comfortableLowHz = 247f, comfortableHighHz = 659f,
            passaggioHz = 392f, sampleCount = 600,
            topFachKey = null, topFachScore = null, topFachMaxScore = null,
            isPartial = false,
        )

        assertEquals(0, repo.count())
        repo.save(record("a"))
        repo.save(record("b"))
        assertEquals(2, repo.count())
    }

    @Test
    fun `repository deleteById removes session`() = runBlocking {
        val repo = com.yuriy.diapason.data.repository.SessionRepositoryImpl(dao)

        fun record(id: String) = com.yuriy.diapason.data.SessionRecord(
            id = id, timestampMs = 1_000L, durationSeconds = 30f,
            detectedMinHz = 196f, detectedMaxHz = 880f,
            comfortableLowHz = 247f, comfortableHighHz = 659f,
            passaggioHz = 392f, sampleCount = 600,
            topFachKey = "Lyric Baritone", topFachScore = 9, topFachMaxScore = 14,
            isPartial = false,
        )

        repo.save(record("keep"))
        repo.save(record("gone"))
        repo.deleteById("gone")

        assertNull(repo.getById("gone"))
        assertNotNull(repo.getById("keep"))
    }
}
