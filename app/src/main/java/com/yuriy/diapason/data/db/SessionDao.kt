package com.yuriy.diapason.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /**
     * Insert or replace a session.
     *
     * REPLACE strategy means calling [save] twice with the same [SessionEntity.id]
     * is safe and idempotent — the newer row wins. In practice, UUIDs make
     * collisions impossible, but the strategy future-proofs retry logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    /**
     * Observable stream of all sessions, newest first.
     * The Flow emits automatically whenever the table changes — perfect for
     * driving a history list without manual refresh logic.
     */
    @Query("SELECT * FROM sessions ORDER BY timestamp_ms DESC")
    fun observeAllOrderedByDate(): Flow<List<SessionEntity>>

    /**
     * One-shot read of all sessions, newest first.
     * Use when you need a snapshot rather than a live stream (e.g. tests, exports).
     */
    @Query("SELECT * FROM sessions ORDER BY timestamp_ms DESC")
    suspend fun getAllOrderedByDate(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}
