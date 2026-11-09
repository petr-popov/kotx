package io.kotx.dbq.service

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import kotlinx.serialization.serializer
import org.apache.ibatis.exceptions.PersistenceException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import io.kotx.dbq.dal.TaskDao
import io.kotx.dbq.model.Task
import io.kotx.dbq.model.TaskMetadata
import io.kotx.tm.core.TransactionManager
import java.lang.RuntimeException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

open class DbQueue(
    val listeners: List<DbqListener<Task>>,
    val transactionManager: TransactionManager,
    val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(DbQueue::class.java)

    private var started: Boolean = false
    private var pollingScheduled: Boolean = false
    private val listenersByTableName = HashMap<String, DbqListener<Task>>()
    private val daosByClass = HashMap<KClass<Task>, TaskDao>()
    private val daosByTableName = HashMap<String, TaskDao>()
    private val dbqHeartManagersByTableName = HashMap<String, DbqHeart>()
    protected val json = createJson()

    suspend fun addTask(task: Task, delayMillis: Long = 0L) {
        if (!started) throw RuntimeException("DBQueue wasn't started")
        val dao = daosByClass.get(task::class)
        if (dao == null)
            throw RuntimeException(
                "Unable to find DAO for ${task::class}," +
                    " probably listener for this class wasn't configured properly"
            )
        dao.saveTask(json.encodeToString(task), delayMillis)
    }

    suspend fun addTasks(tasks: List<Task>) {
        if (!started) throw RuntimeException("DBQueue wasn't started")
        if (tasks.isEmpty()) throw RuntimeException("Empty tasks list")
        val taskClass = tasks.get(0)::class
        val dao = daosByClass.get(taskClass)
        if (dao == null)
            throw RuntimeException(
                "Unable to find DAO for ${taskClass}," +
                    " probably listener for this class wasn't configured properly"
            )
        val payloads = tasks.map { json.encodeToString(it) }
            .toList()
        dao.saveTasks(payloads)
    }

    suspend fun addTaskNextDay(task: Task, millisFromMidnignt: Long = 0L) {
        if (!started) throw RuntimeException("DBQueue wasn't started")
        val dao = daosByClass.get(task::class)
        if (dao == null)
            throw RuntimeException(
                "Unable to find DAO for ${task::class}," +
                    " probably listener for this class wasn't configured properly"
            )
        dao.saveTaskNextDay(json.encodeToString(task), millisFromMidnignt)
    }

    suspend fun hasTaskForExecution(tableName: String): Boolean {
        return daosByTableName.get(tableName)!!.hasTaskForExecution()
    }

    suspend fun hasMoreScheduledTasks(tableName: String): Boolean {
        return daosByTableName.get(tableName)!!.hasMoreScheduledTasks()
    }

    suspend fun start(schedulePolling: Boolean) {
        listeners
            .forEach { listener ->
                listener.setDbQueue(this)
                val meta = listener.meta()
                logger.info("started ${meta.tableName} listener")
                val dao = TaskDao(meta, transactionManager, json)
                dao.createTable()
                listenersByTableName.put(meta.tableName, listener)
                daosByClass.put(listener.taskClass(), dao)
                daosByTableName.put(meta.tableName, dao)
                dbqHeartManagersByTableName.put(meta.tableName, DbqHeartImpl(dao, meta.heartbeatFlushInterval))
                if (schedulePolling) schedulePolling(meta)
            }
        started = true
        pollingScheduled = schedulePolling
    }

    open protected fun schedulePolling(
        meta: TaskMetadata
    ) {
        val scope = CoroutineScope(Executors.newFixedThreadPool(meta.workersPerAppNode).asCoroutineDispatcher())
        val warmingUpCounter = AtomicInteger(meta.workersPerAppNode)
        for (workerIndex in 1..meta.workersPerAppNode) {
            scope.launch {
                logger.info("Starting worker ${workerIndex} for '${meta.tableName}'")
                delay(10000 + Math.round(Math.random() * 10000))
                while (true) {
                    try {
                        getTaskAndInvokeListener(meta.tableName, meta.batchSize)
                        if (warmingUpCounter.get() >= 0 && warmingUpCounter.decrementAndGet() > 0) {
                            delay(Math.round(Math.random() * 10 * 60 * 1000))
                        }
                    } catch (e: Exception) {
                        logger.error("Error while getTaskAndInvokeListener", e)
                    }
                    delay(meta.pollingInterval)
                }
            }
        }
    }

    open suspend fun getTaskAndInvokeListener(
        tableName: String,
        batchSize: Int,
    ): Boolean {
        val dao = daosByTableName.get(tableName)!!
        val listener = listenersByTableName.get(tableName)!!
        val heartbeatManager = dbqHeartManagersByTableName.get(tableName)!!

        val start = System.currentTimeMillis()
        val tasks: List<Pair<Long, Task>> = dao.takeTasks()
        meterRegistry.timer("db-queue-${tableName}-get-task")
            .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)

        val handledSuccessfully = ArrayList<Long>()
        val handledWithErrors = ArrayList<Long>()
        tasks.forEach { idAndPayload ->
            val id = idAndPayload.first
            val payload = idAndPayload.second
            try {
                MDC.put("task_id", "$id")
                MDC.put("task", tableName)
                MDC.put("traceId", "${tableName}:${id}")
                try {
                    invokeListenerWIthMetrics(listener, payload, heartbeatManager, tableName)
                } catch (e: PersistenceException) {
                    if (e.message?.contains("duplicate key value violates") == true) {
                        logger.warn("retry '${tableName}:${id} in 1min because of", e)
                        delay(60 * 1000)
                        invokeListenerWIthMetrics(listener, payload, heartbeatManager, tableName)
                    } else {
                        throw e
                    }
                }

                handledSuccessfully.add(id)
            } catch (e: IOException) {
                logger.warn("exception during '${tableName}:${id} execution", e)
                handledWithErrors.add(id)
            } catch (e: Exception) {
                logger.error("exception during '${tableName}:${id} execution", e)
                handledWithErrors.add(id)
            } finally {
                MDC.clear()
            }
        }

        if (!handledSuccessfully.isEmpty()) {
            dao.removeTasks(handledSuccessfully)
        }

        if (!handledWithErrors.isEmpty()) {
            dao.tasksFailure(handledWithErrors)
        }

        return !handledSuccessfully.isEmpty()
    }

    private suspend fun invokeListenerWIthMetrics(
        listener: DbqListener<Task>,
        payload: Task,
        dbqHeart: DbqHeart,
        tableName: String
    ) {
        val start = System.currentTimeMillis()
        listener.handleTask(payload, dbqHeart)
        meterRegistry.timer("db-queue-${tableName}-handle-task")
            .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
    }

    protected fun createJson(): Json {
        val module = SerializersModule {
            polymorphic(Task::class) {
                listeners.forEach {
                    val taskKClass = it.taskClass()
                    val serializer = serializer(taskKClass.createType())
                    @Suppress("UNCHECKED_CAST")
                    serializer as KSerializer<Task>
                    subclass(taskKClass, serializer)
                }
            }
        }

        return Json { serializersModule = module }
    }
}
