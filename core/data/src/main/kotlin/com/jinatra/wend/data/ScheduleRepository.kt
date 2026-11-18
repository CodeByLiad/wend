package com.jinatra.wend.data

import androidx.room.withTransaction
import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.SchedulingEngine
import com.jinatra.wend.engine.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject internal constructor(private val db: WendDatabase, private val clock: Clock) {
    fun observe(date: LocalDate): Flow<StoredDay?> = db.dao().observeDay(date.toString()).map { it?.stored() }
    suspend fun get(date: LocalDate): StoredDay? = db.dao().day(date.toString())?.stored()

    /** Initial empty container only; activities enter through AddBlock engine proposals. */
    suspend fun create(date: LocalDate, wake: Minute, bedtime: Minute): StoredDay = db.withTransaction {
        db.dao().day(date.toString())?.stored()?.let { return@withTransaction it }
        val day = Day(date, wake, bedtime, emptyList())
        require(SchedulingEngine.propose(day, Command.Undo(day), Minute(0)).status == ProposalStatus.OK) { "Invalid day bounds" }
        val now = clock.millis()
        val record = DayEntity(uuid(), date.toString(), wake.value, bedtime.value, 0, now, now)
        db.dao().insertDay(record)
        db.dao().insertChange(ChangeLogEntity(uuid(), record.id, 0, "CREATE_DAY", ChangeSource.SYSTEM.name, null, DayCodec.encode(day), "[]", false, null, now, now))
        StoredDay(record.id, record.revision, day, now, now)
    }

    suspend fun preview(date: LocalDate, command: Command, now: Minute, settings: Settings = Settings()): SchedulePreview? {
        require(command !is Command.Undo) { "Undo must use persisted history" }
        val before = get(date) ?: return null
        val proposal = SchedulingEngine.propose(before.day, command, now, settings)
        if (proposal.status != ProposalStatus.REJECTED) validateIds(proposal.day)
        return SchedulePreview(before, command, now, settings, proposal)
    }

    /** Recompute, rather than trusting a caller-supplied edited Day, inside the write transaction. */
    suspend fun apply(preview: SchedulePreview, source: ChangeSource = ChangeSource.USER): ScheduleResult = db.withTransaction {
        val current = db.dao().day(preview.before.day.date.toString()) ?: return@withTransaction ScheduleResult.Missing
        if (current.record.id != preview.before.id || current.record.revision != preview.before.revision || current.stored().day != preview.before.day) {
            return@withTransaction ScheduleResult.Stale
        }
        val proposal = SchedulingEngine.propose(current.stored().day, preview.command, preview.now, preview.settings)
        if (proposal.status != ProposalStatus.OK && proposal.status != ProposalStatus.INFEASIBLE) return@withTransaction ScheduleResult.NotApplicable(proposal)
        if (proposal.day == proposal.previousDay) return@withTransaction ScheduleResult.NoChange(current.stored())
        save(current, proposal.day, operation(preview.command), source, proposal)
    }

    suspend fun setStatus(date: LocalDate, revision: Long, id: String, action: StatusAction): ScheduleResult = db.withTransaction {
        val current = db.dao().day(date.toString()) ?: return@withTransaction ScheduleResult.Missing
        if (current.record.revision != revision) return@withTransaction ScheduleResult.Stale
        val after = statusDay(current.stored().day, id, action) ?: return@withTransaction ScheduleResult.InvalidStatus
        save(current, after, action.name, ChangeSource.USER)
    }

    suspend fun rollover(date: LocalDate, today: LocalDate): ScheduleResult = db.withTransaction {
        require(date < today) { "Only an earlier day may roll over" }
        val current = db.dao().day(date.toString()) ?: return@withTransaction ScheduleResult.Missing
        val before = current.stored().day
        val after = before.copy(
            blocks = before.blocks.map {
                if (it.status == BlockStatus.PLANNED || it.status == BlockStatus.IN_PROGRESS) it.copy(status = BlockStatus.MISSED) else it
            },
        )
        if (after == before) return@withTransaction ScheduleResult.NoChange(current.stored())
        save(current, after, "ROLLOVER", ChangeSource.SYSTEM)
    }

    suspend fun undo(date: LocalDate, revision: Long, now: Minute): ScheduleResult = db.withTransaction {
        val current = db.dao().day(date.toString()) ?: return@withTransaction ScheduleResult.Missing
        if (current.record.revision != revision) return@withTransaction ScheduleResult.Stale
        val last = db.dao().latestUndoable(current.record.id) ?: return@withTransaction ScheduleResult.NoHistory
        val previous = DayCodec.decode(requireNotNull(last.beforeJson))
        val proposal = SchedulingEngine.propose(current.stored().day, Command.Undo(previous), now)
        if (proposal.status != ProposalStatus.OK) return@withTransaction ScheduleResult.NotApplicable(proposal)
        val undoId = uuid()
        val result = save(current, proposal.day, "UNDO", ChangeSource.USER, proposal, undoable = false, changeId = undoId)
        db.dao().updateChange(last.copy(undoneBy = undoId, updatedAt = clock.millis()))
        result
    }

    suspend fun canUndo(date: LocalDate): Boolean = db.dao().day(date.toString())?.let { db.dao().latestUndoable(it.record.id) != null } ?: false

    suspend fun history(date: LocalDate): List<ChangeHistory> {
        val current = db.dao().day(date.toString()) ?: return emptyList()
        return db.dao().changes(current.record.id).map {
            ChangeHistory(
                it.id, it.revision, it.operation, ChangeSource.valueOf(it.source), it.beforeJson?.let(DayCodec::decode),
                DayCodec.decode(it.afterJson), it.undoneBy != null, it.createdAt, it.updatedAt, DayCodec.decodeChanges(it.changesJson),
            )
        }
    }

    private suspend fun save(
        before: DayWithBlocks,
        after: Day,
        operation: String,
        source: ChangeSource,
        proposal: Proposal? = null,
        undoable: Boolean = true,
        changeId: String = uuid(),
    ): ScheduleResult.Applied {
        validateIds(after)
        check(after.date.toString() == before.record.date)
        val now = clock.millis()
        val record = before.record.copy(wake = after.wake.value, bedtime = after.bedtime.value, revision = before.record.revision + 1, updatedAt = now)
        val old = before.blocks.associateBy { it.id }
        val blocks = after.blocks.mapIndexed { position, block ->
            val original = old[block.id]
            val entity = block.entity(record.id, position, original?.createdAt ?: now, now)
            // Preserve metadata for every completely unchanged row.
            if (original != null && entity.copy(updatedAt = original.updatedAt) == original) original else entity
        }
        db.dao().updateDay(record)
        db.dao().deleteBlocks(record.id)
        db.dao().insertBlocks(blocks)
        db.dao().insertChange(
            ChangeLogEntity(
                changeId, record.id, record.revision, operation, source.name,
                DayCodec.encode(before.stored().day), DayCodec.encode(after), DayCodec.encodeChanges(proposal?.changes.orEmpty()), undoable, null, now, now,
            ),
        )
        return ScheduleResult.Applied(StoredDay(record.id, record.revision, after, record.createdAt, record.updatedAt), proposal)
    }

    private fun validateIds(day: Day) {
        day.blocks.forEach {
            UUID.fromString(it.id)
            it.after?.let(UUID::fromString)
        }
    }
    private fun uuid(): String = UUID.randomUUID().toString()
}

private fun operation(command: Command): String = when (command) {
    Command.RebuildDay -> "REBUILD_DAY"
    is Command.LateWake -> "LATE_WAKE"
    is Command.Overrun -> "OVERRUN"
    is Command.RetryBlock -> "RETRY_BLOCK"
    is Command.MoveBlock -> "MOVE_BLOCK"
    is Command.SkipBlock -> "SKIP_BLOCK"
    is Command.AddBlock -> "ADD_BLOCK"
    is Command.SetFixed -> "SET_FIXED"
    is Command.SetBedtime -> "SET_BEDTIME"
    is Command.Undo -> "UNDO"
}

private fun DayWithBlocks.stored(): StoredDay = StoredDay(
    record.id,
    record.revision,
    Day(
        LocalDate.parse(record.date),
        Minute(record.wake),
        Minute(record.bedtime),
        blocks.sortedBy { it.position }.map {
            Block(
                it.id, it.title, Kind.valueOf(it.kind), Minute(it.start), Minute(it.end), it.duration, it.minDuration,
                it.priority?.let(Priority::valueOf), DayCodec.decodeWindow(it.windowJson), it.afterId, BlockStatus.valueOf(it.status),
            )
        },
    ),
    record.createdAt,
    record.updatedAt,
)

private fun Block.entity(dayId: String, position: Int, createdAt: Long, updatedAt: Long) = PlannedBlockEntity(
    id, dayId, position, title, kind.name, start.value, end.value, duration, minDuration, priority?.name,
    DayCodec.encodeWindow(window), after, status.name, createdAt, updatedAt,
)
