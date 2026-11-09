package io.kotx.dbq.service

import org.slf4j.LoggerFactory
import io.kotx.dbq.model.RetriesTask
import io.kotx.dbq.model.TaskMetadata
import io.kotx.tm.mybatis.SqlSession
import kotlin.reflect.KClass

class RetriesTaskListener(
    val interval: Long,
    val dbqRetriesListener: DbqRetriesListener? = null
) : DbqListener<RetriesTask> {

    val logger = LoggerFactory.getLogger(RetriesTaskListener::class.java)

    var dbq: DbQueue? = null

    override suspend fun handleTask(task: RetriesTask, dbqHeart: DbqHeart) {
        try {
            markForRetry()
        } catch (e: Exception) {
            logger.error("error while markForRetry", e)
        }

        dbq!!.addTask(RetriesTask(), interval)
    }

    private suspend fun markForRetry() {
        dbq!!.listeners
            .filter { l -> l.meta().maxRetries > 0 }
            .map { it.meta() }
            .forEach { meta ->
                dbq!!.transactionManager.rwTransaction {
                    val stmt = SqlSession.get().connection.prepareStatement(
                        "UPDATE ${meta.tableName} SET state = 'WAITING_FOR_WORKER', retries = retries + 1 " +
                            "WHERE state = 'ERROR' AND retries < ?"
                    )
                    stmt.setInt(1, meta.maxRetries)
                    stmt.executeUpdate()
                }
            }

        dbqRetriesListener?.markForRetry(dbq!!)
    }

    override fun meta(): TaskMetadata {
        return TaskMetadata(
            RetriesTask.TABLE_NAME,
            maxRetries = 0
        )
    }

    override fun taskClass(): KClass<RetriesTask> {
        return RetriesTask::class
    }

    override fun setDbQueue(dbQueue: DbQueue) {
        dbq = dbQueue
    }
}
