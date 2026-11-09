package io.kotx.dbq.service

interface DbqRetriesListener {
    suspend fun markForRetry(dbq: DbQueue)
}
