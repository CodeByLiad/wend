package com.jinatra.wend.data

import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Change
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.Settings

data class StoredDay(val id: String, val revision: Long, val day: Day, val createdAt: Long, val updatedAt: Long)
enum class ChangeSource { USER, ENGINE, AI, REMINDER, SYSTEM }
enum class StatusAction { START, DONE }

class SchedulePreview internal constructor(
    val before: StoredDay,
    val command: Command,
    val now: Minute,
    val settings: Settings,
    val proposal: Proposal,
)

sealed interface ScheduleResult {
    data class Applied(val stored: StoredDay, val proposal: Proposal? = null) : ScheduleResult
    data class NoChange(val stored: StoredDay) : ScheduleResult
    data class NotApplicable(val proposal: Proposal) : ScheduleResult
    data object Stale : ScheduleResult
    data object Missing : ScheduleResult
    data object NoHistory : ScheduleResult
    data object InvalidStatus : ScheduleResult
}

data class ChangeHistory(
    val id: String,
    val revision: Long,
    val operation: String,
    val source: ChangeSource,
    val before: Day?,
    val after: Day,
    val undone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val changes: List<Change>,
)

/** Status-only boundary: every other Block field remains identical. */
internal fun statusDay(day: Day, id: String, action: StatusAction): Day? {
    val block = day.blocks.firstOrNull { it.id == id } ?: return null
    val status = when (action) {
        StatusAction.START -> {
            if (block.status != BlockStatus.PLANNED || day.blocks.any { it.status == BlockStatus.IN_PROGRESS }) return null
            BlockStatus.IN_PROGRESS
        }

        StatusAction.DONE -> {
            if (block.status != BlockStatus.PLANNED && block.status != BlockStatus.IN_PROGRESS) return null
            BlockStatus.DONE
        }
    }
    return day.copy(blocks = day.blocks.map { if (it.id == id) it.copy(status = status) else it })
}
