package io.kotx.dbq.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(RescheduleTask.TABLE_NAME)
data class RescheduleTask(
    val interval: RescheduleTaskInterval = RescheduleTaskInterval.DAY
): Task {
    companion object {
        const val TABLE_NAME = "task_reschedule"
    }
}
