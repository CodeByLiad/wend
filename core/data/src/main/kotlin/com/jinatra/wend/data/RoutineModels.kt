package com.jinatra.wend.data

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.ValidationError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface Recurrence {
    data object Daily : Recurrence
    data class Weekdays(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty())
        }
    }
    data class EveryNDays(val interval: Int, val anchor: LocalDate) : Recurrence {
        init {
            require(interval > 0)
        }
    }
    data class Monthly(val day: Int) : Recurrence {
        init {
            require(day in 1..31)
        }
    }
    fun matches(date: LocalDate): Boolean = when (this) {
        Daily -> true
        is Weekdays -> date.dayOfWeek in days
        is EveryNDays -> !date.isBefore(anchor) && ChronoUnit.DAYS.between(anchor, date) % interval == 0L
        is Monthly -> date.dayOfMonth == day
    }
}

/** Block ids/dependencies here name templates, not dated instances. */
data class ActivityDetails(val templateId: String, val routineId: String, val category: String, val icon: String)

data class ActivityTemplate(val block: Block, val category: String, val icon: String)
data class RoutineDraft(
    val id: String,
    val name: String,
    val dayType: String,
    val recurrence: Recurrence,
    val activities: List<ActivityTemplate>,
    val enabled: Boolean = true,
)
data class StoredRoutine(val draft: RoutineDraft, val revision: Long, val createdAt: Long, val updatedAt: Long)

enum class RoutineFieldError { INVALID_UUID, TEMPLATE_IN_OTHER_ROUTINE, BLANK_NAME, BLANK_DAY_TYPE, BLANK_ACTIVITY_NAME, BLANK_CATEGORY, BLANK_ICON, NON_PLANNED_TEMPLATE }
sealed interface RoutineSaveResult {
    data class Saved(val routine: StoredRoutine) : RoutineSaveResult
    data object Stale : RoutineSaveResult
    data class Invalid(val fields: Set<RoutineFieldError>, val engineErrors: List<ValidationError>) : RoutineSaveResult
}

class GenerationPreview internal constructor(
    val routine: StoredRoutine,
    val date: LocalDate,
    val wake: Minute,
    val bedtime: Minute,
    val now: Minute,
    val proposal: Proposal,
    internal val instanceIds: Map<String, String>,
)
sealed interface GenerationResult {
    data class Generated(val stored: StoredDay, val proposal: Proposal) : GenerationResult
    data class Existing(val stored: StoredDay) : GenerationResult
    data object Stale : GenerationResult
    data class NotApplicable(val proposal: Proposal) : GenerationResult
}

internal fun materialize(routine: RoutineDraft, date: LocalDate, wake: Minute, bedtime: Minute, ids: Map<String, String>): Day = Day(
    date,
    wake,
    bedtime,
    routine.activities.map { template ->
        template.block.copy(id = ids.getValue(template.block.id), after = template.block.after?.let(ids::getValue))
    },
)
