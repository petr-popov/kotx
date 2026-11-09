package io.kotx.dbq.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(RetriesTask.TABLE_NAME)
class RetriesTask : Task {
    companion object {
        const val TABLE_NAME = "task_retries"
    }
}
