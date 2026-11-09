package io.kotx.dbq.model

data class TaskMetadata(
    val tableName: String,
    val workersPerAppCluster: Int = 1,
    val workersPerAppNode: Int = 1,
    val pollingInterval: Long = 10_000,
    val shouldBeTakenByAnotherWorketAtInitialDelta: Long = 300_000,
    val heartbeatFlushInterval: Long = 10_000,
    val continueAfterError: Boolean = true,
    val batchSize: Int = 1,
    val rescheduleInterval: RescheduleTaskInterval? = null,
    val rescheduleDelayFromMidnightMillis: Long = 0,
    val maxRetries: Int = 3
)
