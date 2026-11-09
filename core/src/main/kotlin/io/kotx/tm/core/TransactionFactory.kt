package io.kotx.tm.core

interface TransactionFactory {
    fun newRw(autoCommit: Boolean = false): Transaction
    fun newRo(autoCommit: Boolean = false): Transaction
    fun newBatch(): Transaction
}