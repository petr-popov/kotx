package io.kotx.dbq.dal

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import io.kotx.dbq.model.TaskMetadata
import io.kotx.dbq.model.Task
import io.kotx.tm.core.TransactionManager
import io.kotx.tm.mybatis.SqlSession

class TaskDao(
    val taskMeta: TaskMetadata,
    val transactionManager: TransactionManager,
    val json: Json
) {

    val logger = LoggerFactory.getLogger(TaskDao::class.qualifiedName + "_${taskMeta.tableName}")

    val pickSql = """
        WITH in_progress AS (
            SELECT count(*) AS count
            FROM ${taskMeta.tableName}
            WHERE next_processing_at < now()
              AND state = 'IN_PROGRESS'
              AND should_be_taken_by_another_worker_at > now()
        ),
        candidate AS (
            SELECT id FROM (
                SELECT id, next_processing_at
                FROM ${taskMeta.tableName}
                CROSS JOIN in_progress
                WHERE in_progress.count < ${taskMeta.workersPerAppCluster} * ${taskMeta.batchSize}
                  AND next_processing_at < now()
                  AND state = 'WAITING_FOR_WORKER'
                
                UNION ALL
                
                SELECT id, next_processing_at
                FROM ${taskMeta.tableName}
                CROSS JOIN in_progress
                WHERE in_progress.count < ${taskMeta.workersPerAppCluster} * ${taskMeta.batchSize}
                  AND next_processing_at < now()
                  AND state = 'IN_PROGRESS'
                  AND should_be_taken_by_another_worker_at < now()
            ) t
            ORDER BY next_processing_at
            LIMIT ${taskMeta.batchSize}
        )
        UPDATE ${taskMeta.tableName}
        SET state = 'IN_PROGRESS',
            should_be_taken_by_another_worker_at = now() + INTERVAL '${taskMeta.shouldBeTakenByAnotherWorketAtInitialDelta} millisecond'
        FROM candidate
        WHERE candidate.id = ${taskMeta.tableName}.id
        RETURNING ${taskMeta.tableName}.id, payload
    """

    suspend fun createTable() {
        transactionManager.rwTransaction {
            SqlSession.get().connection.createStatement().executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ${taskMeta.tableName} (
                        id bigserial primary key ,
                        next_processing_at timestamp default now() - INTERVAL '1 millisecond',
                        state dbq_state default 'WAITING_FOR_WORKER',
                        should_be_taken_by_another_worker_at timestamp,
                        payload text,
                        retries int default 0
                    )
                """)
            SqlSession.get().connection.createStatement().executeUpdate("""
                CREATE INDEX IF NOT EXISTS ${taskMeta.tableName}_next_processing_at_state_idx
                    ON ${taskMeta.tableName} (next_processing_at, state)
            """)
        }
    }

    suspend inline fun <reified T : Task> takeTasks(): List<Pair<Long, T>> {
        val result = ArrayList<Pair<Long, T>>()
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            stmt.executeUpdate("LOCK TABLE ${taskMeta.tableName}")
            val sql = if (TaskDaoDevHack.id == null)
                pickSql
            else
                "SELECT id, payload FROM ${taskMeta.tableName} WHERE id = ${TaskDaoDevHack.id}"
            val resultSet = stmt.executeQuery(sql)
            while (resultSet.next()) {
                val payload = resultSet.getString("payload")
                val id = resultSet.getLong("id")
                val task: T = json.decodeFromString(payload)
                result.add(Pair(id, task))
            }
            if (result.size > 0) {
                logger.debug("obtained ${result.size} tasks, ids: ${result.map { it.first }.joinToString(" ")}")
            }
        }
        return result
    }

    suspend fun removeTasks(ids: List<Long>) {
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            stmt.executeUpdate("""
                DELETE FROM ${taskMeta.tableName} where id in (${ids.joinToString()})
            """.trimIndent())
        }
    }

    suspend fun tasksFailure(ids: List<Long>) {
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            stmt.executeUpdate("UPDATE ${taskMeta.tableName} set state = 'ERROR' where id in (${ids.joinToString()})")
        }
    }

    suspend fun increaseShouldBeTakenByAnotherWorketAt(id: Long, flushInterval: Long) {
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            stmt.executeUpdate("UPDATE ${taskMeta.tableName} set should_be_taken_by_another_worker_at += $flushInterval where id = $id")
        }
    }

    suspend fun saveTask(taskPayload: String, delayMillis: Long) { //todo add saveTasks method
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.prepareStatement("""
                INSERT INTO ${taskMeta.tableName} (payload, next_processing_at)
                VALUES (?, now() + INTERVAL '$delayMillis millisecond')
            """.trimIndent())
            stmt.setString(1, taskPayload)
            stmt.executeUpdate()
        }
    }

    suspend fun saveTasks(payloads: List<String>) {
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.prepareStatement(
                "INSERT INTO  ${taskMeta.tableName} (payload) values (?)"
            )
            payloads.chunked(1000).forEach { chunk ->
                chunk.forEach {
                    stmt.clearParameters()
                    stmt.setString(1, it)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    suspend fun saveTaskNextDay(taskPayload: String, millisFromMidnignt: Long = 0L) { //todo add saveTasks method
        transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.prepareStatement("""
                INSERT INTO ${taskMeta.tableName} (payload, next_processing_at)
                VALUES (?, date_trunc('day', now() + interval '24 hour') + INTERVAL '$millisFromMidnignt millisecond')
            """.trimIndent())
            stmt.setString(1, taskPayload)
            stmt.executeUpdate()
        }
    }

    suspend fun hasTaskForExecution(): Boolean {
        return transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            val resultSet = stmt.executeQuery(
                "SELECT count(*) > 0 FROM ${taskMeta.tableName} " +
                "WHERE NOT (state = 'ERROR' AND retries >= ${taskMeta.maxRetries})"
            )
            resultSet.next() && resultSet.getBoolean(1)
        }
    }

    suspend fun hasMoreScheduledTasks(): Boolean {
        return transactionManager.rwTransaction {
            val stmt = SqlSession.get().connection.createStatement()
            val resultSet = stmt.executeQuery("SELECT count(*) > 1 FROM  ${taskMeta.tableName} ")
            resultSet.next() && resultSet.getBoolean(1)
        }
    }
}
