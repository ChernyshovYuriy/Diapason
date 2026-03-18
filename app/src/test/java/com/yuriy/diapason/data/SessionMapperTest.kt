package com.yuriy.diapason.data

import com.yuriy.diapason.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [toDomain] / [toEntity] mapper functions.
 *
 * No Android context is required here, so these run fast on the local JVM
 * with a plain `./gradlew test` invocation.
 */
class SessionMapperTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun fullEntity(
        id: String = "abc-123",
        topFachKey: String? = "Lyric Soprano",
        topFachScore: Int? = 11,
        topFachMaxScore: Int? = 14,
        isPartial: Boolean = false,
    ) = SessionEntity(
        id = id,
        timestampMs = 1_700_000_000_000L,
        durationSeconds = 42f,
        detectedMinHz = 196f,
        detectedMaxHz = 880f,
        comfortableLowHz = 247f,
        comfortableHighHz = 659f,
        passaggioHz = 392f,
        sampleCount = 850,
        topFachKey = topFachKey,
        topFachScore = topFachScore,
        topFachMaxScore = topFachMaxScore,
        isPartial = isPartial,
    )

    private fun fullRecord(
        id: String = "abc-123",
        topFachKey: String? = "Lyric Soprano",
        topFachScore: Int? = 11,
        topFachMaxScore: Int? = 14,
        isPartial: Boolean = false,
    ) = SessionRecord(
        id = id,
        timestampMs = 1_700_000_000_000L,
        durationSeconds = 42f,
        detectedMinHz = 196f,
        detectedMaxHz = 880f,
        comfortableLowHz = 247f,
        comfortableHighHz = 659f,
        passaggioHz = 392f,
        sampleCount = 850,
        topFachKey = topFachKey,
        topFachScore = topFachScore,
        topFachMaxScore = topFachMaxScore,
        isPartial = isPartial,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `toDomain maps id correctly`() {
        assertEquals("abc-123", fullEntity().toDomain().id)
    }

    @Test
    fun `toDomain maps timestampMs correctly`() {
        assertEquals(1_700_000_000_000L, fullEntity().toDomain().timestampMs)
    }

    @Test
    fun `toDomain maps durationSeconds correctly`() {
        assertEquals(42f, fullEntity().toDomain().durationSeconds)
    }

    @Test
    fun `toDomain maps detected range correctly`() {
        val domain = fullEntity().toDomain()
        assertEquals(196f, domain.detectedMinHz)
        assertEquals(880f, domain.detectedMaxHz)
    }

    @Test
    fun `toDomain maps comfortable range correctly`() {
        val domain = fullEntity().toDomain()
        assertEquals(247f, domain.comfortableLowHz)
        assertEquals(659f, domain.comfortableHighHz)
    }

    @Test
    fun `toDomain maps passaggioHz correctly`() {
        assertEquals(392f, fullEntity().toDomain().passaggioHz)
    }

    @Test
    fun `toDomain maps sampleCount correctly`() {
        assertEquals(850, fullEntity().toDomain().sampleCount)
    }

    @Test
    fun `toDomain maps topFachKey correctly`() {
        assertEquals("Lyric Soprano", fullEntity().toDomain().topFachKey)
    }

    @Test
    fun `toDomain maps topFachScore correctly`() {
        assertEquals(11, fullEntity().toDomain().topFachScore)
        assertEquals(14, fullEntity().toDomain().topFachMaxScore)
    }

    @Test
    fun `toDomain maps isPartial false`() {
        assertFalse(fullEntity(isPartial = false).toDomain().isPartial)
    }

    @Test
    fun `toDomain maps isPartial true`() {
        assertTrue(fullEntity(isPartial = true).toDomain().isPartial)
    }

    // ── null optional fields ──────────────────────────────────────────────────

    @Test
    fun `toDomain preserves null topFachKey`() {
        assertNull(
            fullEntity(
                topFachKey = null,
                topFachScore = null,
                topFachMaxScore = null
            ).toDomain().topFachKey
        )
    }

    @Test
    fun `toDomain preserves null topFachScore`() {
        assertNull(fullEntity(topFachScore = null).toDomain().topFachScore)
    }

    @Test
    fun `toDomain preserves null topFachMaxScore`() {
        assertNull(fullEntity(topFachMaxScore = null).toDomain().topFachMaxScore)
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    fun `toEntity maps id correctly`() {
        assertEquals("abc-123", fullRecord().toEntity().id)
    }

    @Test
    fun `toEntity maps all Hz fields correctly`() {
        val entity = fullRecord().toEntity()
        assertEquals(196f, entity.detectedMinHz)
        assertEquals(880f, entity.detectedMaxHz)
        assertEquals(247f, entity.comfortableLowHz)
        assertEquals(659f, entity.comfortableHighHz)
        assertEquals(392f, entity.passaggioHz)
    }

    @Test
    fun `toEntity maps isPartial true`() {
        assertTrue(fullRecord(isPartial = true).toEntity().isPartial)
    }

    @Test
    fun `toEntity preserves null fach fields`() {
        val entity = fullRecord(
            topFachKey = null,
            topFachScore = null,
            topFachMaxScore = null,
        ).toEntity()
        assertNull(entity.topFachKey)
        assertNull(entity.topFachScore)
        assertNull(entity.topFachMaxScore)
    }

    // ── roundtrip identity ────────────────────────────────────────────────────

    @Test
    fun `entity roundtrip toDomain toEntity is identity`() {
        val original = fullEntity()
        assertEquals(original, original.toDomain().toEntity())
    }

    @Test
    fun `record roundtrip toEntity toDomain is identity`() {
        val original = fullRecord()
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `roundtrip preserves null fach fields`() {
        val entity = fullEntity(topFachKey = null, topFachScore = null, topFachMaxScore = null)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `roundtrip preserves isPartial true`() {
        val entity = fullEntity(isPartial = true)
        assertTrue(entity.toDomain().toEntity().isPartial)
    }

    @Test
    fun `different ids produce different domain records`() {
        val a = fullEntity(id = "id-a").toDomain()
        val b = fullEntity(id = "id-b").toDomain()
        assertTrue(a != b)
    }
}
