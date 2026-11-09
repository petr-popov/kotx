package io.kotx.dbq.service

import io.kotx.dbq.model.RescheduleTask
import io.kotx.dbq.model.RetriesTask

class TaskReschedulingInitializer(
    val dbQueue: DbQueue,
) : DataLoadInitizalizer {

    override suspend fun initialize() {
        if (!dbQueue.hasTaskForExecution(RescheduleTask.TABLE_NAME)) {
            dbQueue.addTaskNextDay(RescheduleTask())
        }
        if (!dbQueue.hasTaskForExecution(RetriesTask.TABLE_NAME)) {
            dbQueue.addTask(RetriesTask(), 60 * 1000)
        }
    }
}
