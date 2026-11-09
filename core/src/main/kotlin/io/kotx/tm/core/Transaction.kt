package io.kotx.tm.core

interface Transaction {
    fun commit(force: Boolean = false)
    fun rollback(force: Boolean = false)
    fun close(force: Boolean = false)
    fun connection(): java.sql.Connection
}