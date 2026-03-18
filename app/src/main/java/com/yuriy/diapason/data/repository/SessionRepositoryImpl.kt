package com.yuriy.diapason.data.repository

import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.db.SessionDao
import com.yuriy.diapason.data.toDomain
import com.yuriy.diapason.data.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production [SessionRepository] backed by Room via [SessionDao].
 *
 * All suspend functions run on whichever coroutine context the caller provides;
 * Room handles the IO thread switch internally for queries and inserts.
 */
class SessionRepositoryImpl(private val dao: SessionDao) : SessionRepository {

    override suspend fun save(session: SessionRecord) =
        dao.insert(session.toEntity())

    override fun observeAll(): Flow<List<SessionRecord>> =
        dao.observeAllOrderedByDate().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<SessionRecord> =
        dao.getAllOrderedByDate().map { it.toDomain() }

    override suspend fun getById(id: String): SessionRecord? =
        dao.getById(id)?.toDomain()

    override suspend fun deleteById(id: String) =
        dao.deleteById(id)

    override suspend fun count(): Int =
        dao.count()
}
