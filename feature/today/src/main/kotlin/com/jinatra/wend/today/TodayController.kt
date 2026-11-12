package com.jinatra.wend.today

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.ErrorCode
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.SchedulingEngine
import com.jinatra.wend.engine.Settings

/** Who caused a recorded change. */
enum class ChangeSource { USER, ENGINE }

/** One applied change with exact before and after state, so undo can restore it. */
data class ChangeRecord(
    val source: ChangeSource,
    val before: Day,
    val after: Day,
)

/** What the screen should tell the person after an action. */
sealed interface Outcome {
    /** Applied straight away; offer Undo. */
    data class Applied(val kind: AppliedKind, val blockTitle: String?) : Outcome

    /** Changes beyond the person's own edit: wait for approval. */
    data class NeedsApproval(val proposal: Proposal, val settings: Settings) : Outcome

    /** The engine found nothing to change. */
    data object NothingToChange : Outcome

    data class Rejected(val code: ErrorCode?) : Outcome

    data object NoTimeLeft : Outcome
}

enum class AppliedKind { DONE, STARTED, SKIPPED, PLAN_UPDATED, UNDONE }

/**
 * Holds today's plan in memory and turns actions into engine commands.
 *
 * Only [SchedulingEngine] changes block times. Status-only edits (start, done) are
 * applied here, because they change no times, and are logged like every change.
 * Prototype only: nothing is persisted; `:core:data` will own the real ChangeLog.
 */
class TodayController(initial: Day) {
    var day: Day = initial
        private set

    private val log = mutableListOf<ChangeRecord>()
    val changeLog: List<ChangeRecord> get() = log

    val canUndo: Boolean get() = log.isNotEmpty()

    fun runningLate(minutes: Int, now: Int): Outcome {
        require(minutes > 0 && minutes % 5 == 0) { "Delay must be a positive multiple of 5" }
        val running = day.blocks.firstOrNull { it.status == BlockStatus.IN_PROGRESS }
        val started = day.blocks.any { it.status == BlockStatus.DONE || it.status == BlockStatus.IN_PROGRESS }
        return when {
            running != null -> propose(Command.Overrun(running.id, Minute(running.end.value + minutes)), now, running.id)

            !started -> {
                val wake = now + minutes
                propose(Command.LateWake(Minute(wake)), wake, null)
            }

            else -> propose(Command.RebuildDay, now + minutes, null)
        }
    }

    fun rebuild(now: Int, settings: Settings = Settings()): Outcome = propose(Command.RebuildDay, now, null, settings)

    fun skip(id: String, now: Int): Outcome = propose(Command.SkipBlock(id), now, id)

    fun moveLater(id: String, minutes: Int, now: Int): Outcome {
        val block = find(id) ?: return Outcome.Rejected(ErrorCode.UNKNOWN_BLOCK)
        return propose(Command.MoveBlock(id, Minute(block.start.value + minutes)), now, id)
    }

    fun ranOver(id: String, minutes: Int, now: Int): Outcome {
        val block = find(id) ?: return Outcome.Rejected(ErrorCode.UNKNOWN_BLOCK)
        return propose(Command.Overrun(id, Minute(block.end.value + minutes)), now, id)
    }

    fun retry(id: String, now: Int): Outcome = propose(Command.RetryBlock(id), now, id)

    fun makeFixed(id: String, now: Int): Outcome = propose(Command.SetFixed(id), now, id)

    fun markDone(id: String): Outcome {
        val block = find(id) ?: return Outcome.Rejected(ErrorCode.UNKNOWN_BLOCK)
        if (block.status != BlockStatus.PLANNED && block.status != BlockStatus.IN_PROGRESS) return Outcome.Rejected(ErrorCode.INVALID_TARGET)
        record(ChangeSource.USER, withStatus(id, BlockStatus.DONE))
        return Outcome.Applied(AppliedKind.DONE, block.title)
    }

    fun start(id: String): Outcome {
        val block = find(id) ?: return Outcome.Rejected(ErrorCode.UNKNOWN_BLOCK)
        val busy = day.blocks.any { it.status == BlockStatus.IN_PROGRESS }
        if (block.status != BlockStatus.PLANNED || busy) return Outcome.Rejected(ErrorCode.INVALID_TARGET)
        record(ChangeSource.USER, withStatus(id, BlockStatus.IN_PROGRESS))
        return Outcome.Applied(AppliedKind.STARTED, block.title)
    }

    /** Applies a proposal the person approved. */
    fun apply(proposal: Proposal) {
        check(proposal.previousDay == day) { "Proposal is stale" }
        record(ChangeSource.ENGINE, proposal.day)
    }

    /** Restores the plan before the last applied change, through the engine's undo command. */
    fun undo(now: Int): Outcome {
        val last = log.lastOrNull() ?: return Outcome.NothingToChange
        val proposal = SchedulingEngine.propose(day, Command.Undo(last.before), Minute(now))
        if (proposal.status != ProposalStatus.OK) return Outcome.Rejected(proposal.errors.firstOrNull()?.code)
        log.removeAt(log.lastIndex)
        day = proposal.day
        return Outcome.Applied(AppliedKind.UNDONE, null)
    }

    private fun propose(command: Command, now: Int, target: String?, settings: Settings = Settings()): Outcome {
        val proposal = SchedulingEngine.propose(day, command, Minute(now), settings)
        return when (proposal.status) {
            ProposalStatus.REJECTED -> Outcome.Rejected(proposal.errors.firstOrNull()?.code)

            ProposalStatus.NO_HORIZON -> Outcome.NoTimeLeft

            else -> when {
                proposal.changes.isEmpty() -> Outcome.NothingToChange

                // The person's own edit with no knock-on effects needs no second confirmation.
                proposal.status == ProposalStatus.OK && target != null && proposal.changes.all { it.blockId == target } -> {
                    apply(proposal)
                    Outcome.Applied(if (command is Command.SkipBlock) AppliedKind.SKIPPED else AppliedKind.PLAN_UPDATED, find(target)?.title)
                }

                else -> Outcome.NeedsApproval(proposal, settings)
            }
        }
    }

    private fun find(id: String): Block? = day.blocks.firstOrNull { it.id == id }

    private fun withStatus(id: String, status: BlockStatus): Day = day.copy(blocks = day.blocks.map { if (it.id == id) it.copy(status = status) else it })

    private fun record(source: ChangeSource, after: Day) {
        log += ChangeRecord(source, day, after)
        day = after
    }
}

/** Whether a block can still be moved by a rebuild. */
val Block.isMovable: Boolean get() = kind != Kind.FIXED
