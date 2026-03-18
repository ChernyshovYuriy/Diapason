package com.yuriy.diapason.data.repository

import com.yuriy.diapason.data.SessionRecord
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for reading and writing [SessionRecord]s.
 *
 * Backed by Room in production; can be replaced with an in-memory fake in tests
 * that exercise the ViewModel layer without a real database.
 */
interface SessionRepository {

    /**
     * Persist a completed session. Safe to call from any coroutine context;
     * the implementation dispatches to IO internally.
     */
    suspend fun save(session: SessionRecord)

    /**
     * Live stream of all sessions ordered by [SessionRecord.timestampMs] descending.
     * Emits a new list whenever the underlying table changes.
     * Intended for the History screen to observe.
     */
    fun observeAll(): Flow<List<SessionRecord>>

    /**
     * One-shot snapshot of all sessions, newest first.
     * Prefer [observeAll] for UI; use this for exports, tests, or one-time reads.
     */
    suspend fun getAll(): List<SessionRecord>

    /** Returns the session with the given [id], or null if not found. */
    suspend fun getById(id: String): SessionRecord?

    /** Deletes the session with the given [id]. No-op if [id] is unknown. */
    suspend fun deleteById(id: String)

    /** Total number of persisted sessions. */
    suspend fun count(): Int
}
