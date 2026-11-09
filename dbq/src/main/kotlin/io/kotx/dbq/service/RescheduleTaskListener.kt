package io.kotx.dbq.service

import io.kotx.dbq.model.RescheduleTask
import io.kotx.dbq.model.TaskMetadata
import kotlin.reflect.KClass

class RescheduleTaskListener : DbqListener<RescheduleTask> {

    var dbq: DbQueue? = null

    override suspend fun handleTask(task: RescheduleTask, dbqHeart: DbqHeart) {
        dbq!!.listeners.filter { l -> l.meta().rescheduleInterval != null }
            .forEach { listener ->
                val meta = listener.meta()
                if (meta.rescheduleInterval != null && !dbq!!.hasTaskForExecution(meta.tableName)) {
                    dbq!!.addTask(listener.rescheduleTask()!!, meta.rescheduleDelayFromMidnightMillis)
                }
            }
        dbq!!.addTaskNextDay(RescheduleTask())
    }

    override fun meta(): TaskMetadata {
        return TaskMetadata(
            RescheduleTask.TABLE_NAME
        )
    }

    override fun taskClass(): KClass<RescheduleTask> {
        return RescheduleTask::class
    }

    override fun setDbQueue(dbQueue: DbQueue) {
        dbq = dbQueue
    }
}
