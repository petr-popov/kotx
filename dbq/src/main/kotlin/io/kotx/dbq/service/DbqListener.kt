package io.kotx.dbq.service

import io.kotx.dbq.model.Task
import io.kotx.dbq.model.TaskMetadata
import kotlin.reflect.KClass

interface DbqListener<T: Task> {

    suspend fun handleTask(task: T, dbqHeart: DbqHeart)

    fun meta(): TaskMetadata

    fun taskClass(): KClass<T>

    fun setDbQueue(dbQueue: DbQueue) {}

    fun rescheduleTask(): T? {return null}
}
