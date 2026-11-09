package io.kotx.tm.core

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

open class TransactionManager(
    private val transactionFactory: TransactionFactory,
    private val dispatcher: ExecutorCoroutineDispatcher,
    private val dbName: String = "DEFAULT",
) {

    private val logger = LoggerFactory.getLogger(TransactionManager::class.java)

    suspend fun <T> roTransaction(block: suspend () -> T): T {
        return transaction(TransactionType.RO, block)
    }

    suspend fun <T> rwTransaction(batch: Boolean = false, block: suspend () -> T): T {
        if (batch) {
            return transaction(TransactionType.RW_BATCH, block)
        } else {
            return transaction(TransactionType.RW, block)
        }
    }

    suspend fun <T> rwConnection(block: suspend () -> T): T {
        return transaction(TransactionType.RW, block)
    }

    private suspend fun <T> transaction(transactionType: TransactionType, block: suspend () -> T): T {
        val existingSession = currentCoroutineContext().get(CoroutineTransaction)?.transaction
        if (existingSession == null) {
            return coroutineScope {
                withContext (dispatcher + MDCContext()) {
                    return@withContext withNewContext(
                        newSession(transactionType),
                        0,
                        transactionType,
                        dbName,
                        { execInScope(block) }
                    )
                }
            }
        } else {
            validateDbAndType(dbName, transactionType)
            return withNewContext(
                existingSession,
                currentCoroutineContext().get(CoroutineDepth)?.depth!!,
                transactionType,
                dbName,
                { execInScope(block) }
            )
        }
    }

    private suspend fun <T> withNewContext(
        transaction: Transaction,
        depth: Int,
        transactionType: TransactionType,
        dbName: String,
        wrapped: suspend () -> T
    ): T {
        return coroutineScope {
            withContext(
                buildContext(transaction, depth, transactionType, dbName)
            ) {
                return@withContext wrapped()
            }
        }
    }

    private fun buildContext(
        session: Transaction,
        depth: Int,
        transactionType: TransactionType,
        dbName: String
    ) = (dispatcher
        + CoroutineTransaction(session)
        + CoroutineDepth(depth + 1)
        + CoroutineTransactionType(transactionType)
        + CoroutineDbName(dbName)
        + MDCContext())

    private fun newSession(
        transactionType: TransactionType
    ): Transaction {
        if (transactionType == TransactionType.RW_BATCH) {
            return transactionFactory.newBatch()
        } else if (transactionType == TransactionType.RW) {
            return transactionFactory.newRw()
        } else {
            return transactionFactory.newRo()
        }
    }

    private suspend fun <T> execInScope(block: suspend () -> T): T {
        var exceptionWasThrown = false
        try {
            val block1 = block()
            return block1
        } catch (e: Exception) {
            exceptionWasThrown = true
            throw e
        } finally {
            if (currentCoroutineContext().get(CoroutineDepth)?.depth == 1 || exceptionWasThrown) {
                val sessionType = currentCoroutineContext().get(CoroutineTransactionType)?.transactionType
                val toClose = currentCoroutineContext().get(CoroutineTransaction)?.transaction
                if (toClose == null) {
                    throw RuntimeException("No session")
                }
                if (sessionType == TransactionType.RW || sessionType == TransactionType.RW_BATCH) {
                    if (!exceptionWasThrown) {
                        toClose.commit()
                    } else {
                        toClose.rollback()
                    }
                }
                try {
                    toClose.close()
                } catch (e: Exception) {
                    logger.error("unable to close transaction", e)
                }
            }
        }
    }

    suspend fun currentConnection(): java.sql.Connection {
        val transaction = currentCoroutineContext().get(CoroutineTransaction)?.transaction
            ?: throw RuntimeException("currentConnection() called outside of transaction")
        return transaction.connection()
    }

    private suspend fun validateDbAndType(dbName: String, transactionType: TransactionType) {
        val existingDbName = currentCoroutineContext().get(CoroutineDbName)?.dbName
        if (existingDbName != dbName) {
            throw RuntimeException("attempting to open '$dbName' session while '$existingDbName' already opened")
        }

        val existingSessionType = currentCoroutineContext().get(CoroutineTransactionType)?.transactionType
        if (transactionType == TransactionType.RW_BATCH && existingSessionType == TransactionType.RW) {
            throw RuntimeException("attempting to open RW_BATCH while RW already opened")
        }
        if (transactionType != TransactionType.RO && existingSessionType == TransactionType.RO) {
            throw RuntimeException("attempting to open !RO while RO already opened")
        }
        if (transactionType != TransactionType.RO && existingSessionType == TransactionType.CONNECTION) {
            throw RuntimeException("attempting to open !CONNECTION while CONNECTION already opened")
        }
    }
}

class CoroutineTransaction(val transaction: Transaction) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineTransaction>

    override val key: CoroutineContext.Key<CoroutineTransaction>
        get() = Key
}

private class CoroutineDepth(val depth: Int) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineDepth>

    override val key: CoroutineContext.Key<CoroutineDepth>
        get() = Key
}

private class CoroutineTransactionType(val transactionType: TransactionType) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineTransactionType>

    override val key: CoroutineContext.Key<CoroutineTransactionType>
        get() = Key
}

private class CoroutineDbName(val dbName: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineDbName>

    override val key: CoroutineContext.Key<CoroutineDbName>
        get() = Key
}

private enum class TransactionType {
    CONNECTION,
    RO,
    RW,
    RW_BATCH
}