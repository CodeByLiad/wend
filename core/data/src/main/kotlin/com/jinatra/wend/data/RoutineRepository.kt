package com.jinatra.wend.data

import androidx.room.withTransaction
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.SchedulingEngine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject internal constructor(private val db: WendDatabase, private val schedules: ScheduleRepository, private val clock: Clock) {
    fun observe() = db.dao().observeRoutines().map { rows -> rows.map { it.stored() } }
    suspend fun get(id: String): StoredRoutine? = db.dao().routine(id)?.stored()
    suspend fun matching(date: LocalDate, dayType: String? = null): List<StoredRoutine> = db.dao().routines().map { it.stored() }.filter {
        it.draft.enabled && (dayType == null || it.draft.dayType == dayType) && it.draft.recurrence.matches(date)
    }

    /** null revision means create only, never overwrite another editor's work. */
    suspend fun save(draft: RoutineDraft, expectedRevision: Long? = null): RoutineSaveResult = db.withTransaction {
        validateRoutine(draft)?.let { return@withTransaction it }
        if (db.dao().templates(draft.activities.map { it.block.id }).any { it.routineId != draft.id }) {
            return@withTransaction RoutineSaveResult.Invalid(setOf(RoutineFieldError.TEMPLATE_IN_OTHER_ROUTINE), emptyList())
        }
        val old = db.dao().routine(draft.id)
        if ((old == null && expectedRevision != null) || (old != null && old.record.revision != expectedRevision)) return@withTransaction RoutineSaveResult.Stale
        val now = clock.millis()
        val record = RoutineEntity(draft.id, draft.name, draft.dayType, encodeRecurrence(draft.recurrence), draft.enabled, (old?.record?.revision ?: -1) + 1, old?.record?.createdAt ?: now, now)
        if (old == null) db.dao().insertRoutine(record) else db.dao().updateRoutine(record)
        val previous = old?.activities.orEmpty().associateBy { it.id }
        db.dao().deleteTemplates(draft.id)
        val templates = draft.activities.mapIndexed { position, template ->
            val before = previous[template.block.id]
            val row = ActivityTemplateEntity(template.block.id, draft.id, position, encodeTemplate(template), template.category, template.icon, before?.createdAt ?: now, now)
            if (before != null && row.copy(updatedAt = before.updatedAt) == before) before else row
        }
        db.dao().insertTemplates(templates)
        RoutineSaveResult.Saved(requireNotNull(db.dao().routine(draft.id)).stored())
    }

    suspend fun delete(id: String, expectedRevision: Long): Boolean = db.withTransaction { db.dao().deleteRoutine(id, expectedRevision) == 1 }

    /** Explicit selection permits a one-off date even when recurrence does not match it. */
    suspend fun previewGeneration(id: String, date: LocalDate, wake: Minute, bedtime: Minute, now: Minute): GenerationPreview? {
        val routine = get(id) ?: return null
        val ids = routine.draft.activities.associate { it.block.id to UUID.randomUUID().toString() }
        val seed = materialize(routine.draft, date, wake, bedtime, ids)
        val proposal = SchedulingEngine.propose(seed, Command.RebuildDay, now)
        return GenerationPreview(routine, date, wake, bedtime, now, proposal, ids)
    }

    /** Existing dates are never regenerated. Routine edits affect future instances only. */
    suspend fun applyGeneration(preview: GenerationPreview): GenerationResult = db.withTransaction {
        schedules.get(preview.date)?.let { return@withTransaction GenerationResult.Existing(it) }
        val current = get(preview.routine.draft.id) ?: return@withTransaction GenerationResult.Stale
        if (current != preview.routine) return@withTransaction GenerationResult.Stale
        val seed = materialize(current.draft, preview.date, preview.wake, preview.bedtime, preview.instanceIds)
        val proposal = SchedulingEngine.propose(seed, Command.RebuildDay, preview.now)
        if (proposal.status !in setOf(ProposalStatus.OK, ProposalStatus.INFEASIBLE)) return@withTransaction GenerationResult.NotApplicable(proposal)
        val stored = schedules.createFromEngine(proposal)
        val timestamp = clock.millis()
        db.dao().insertOrigins(
            current.draft.activities.map {
                BlockOriginEntity(preview.instanceIds.getValue(it.block.id), stored.id, it.block.id, current.draft.id, it.category, it.icon, timestamp, timestamp)
            },
        )
        GenerationResult.Generated(stored, proposal)
    }
}

private val templateDate = LocalDate.of(2000, 1, 1)
private fun encodeTemplate(template: ActivityTemplate): String = DayCodec.encode(Day(templateDate, Minute(0), Minute(2880), listOf(template.block)))

internal fun validateRoutine(draft: RoutineDraft): RoutineSaveResult.Invalid? {
    val fields = buildSet {
        if (draft.name.isBlank()) add(RoutineFieldError.BLANK_NAME)
        if (draft.dayType.isBlank()) add(RoutineFieldError.BLANK_DAY_TYPE)
        fun id(value: String) {
            if (runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false).not()) add(RoutineFieldError.INVALID_UUID)
        }
        id(draft.id)
        draft.activities.forEach {
            id(it.block.id)
            it.block.after?.let(::id)
            if (it.block.title.isBlank()) add(RoutineFieldError.BLANK_ACTIVITY_NAME)
            if (it.category.isBlank()) add(RoutineFieldError.BLANK_CATEGORY)
            if (it.icon.isBlank()) add(RoutineFieldError.BLANK_ICON)
            if (it.block.status != BlockStatus.PLANNED) add(RoutineFieldError.NON_PLANNED_TEMPLATE)
        }
    }
    val day = Day(templateDate, Minute(0), Minute(2880), draft.activities.map { it.block })
    val errors = SchedulingEngine.propose(day, Command.Undo(day), Minute(0)).errors
    return if (fields.isEmpty() && errors.isEmpty()) null else RoutineSaveResult.Invalid(fields, errors)
}

private fun RoutineWithActivities.stored() = StoredRoutine(
    RoutineDraft(
        record.id,
        record.name,
        record.dayType,
        decodeRecurrence(record.recurrenceJson),
        activities.sortedBy { it.position }.map {
            ActivityTemplate(DayCodec.decode(it.blockJson).blocks.single(), it.category, it.icon)
        },
        record.enabled,
    ),
    record.revision,
    record.createdAt,
    record.updatedAt,
)
