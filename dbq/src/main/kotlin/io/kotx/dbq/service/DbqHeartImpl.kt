package io.kotx.dbq.service

import io.kotx.dbq.dal.TaskDao

class DbqHeartImpl(
    val taskDao: TaskDao,
    val flushInterval: Long
): DbqHeart {

    var lastFlushed = System.currentTimeMillis()

    @Suppress("UNUSED")
    override suspend fun beat() {
        throw RuntimeException("not supported")
        // val now = System.currentTimeMillis()
        // if (now - lastFlushed > flushInterval) {
        //     taskDao.increaseShouldBeTakenByAnotherWorketAt(task.getId(), flushInterval)
        //     lastFlushed = now
        // }
    }
}
