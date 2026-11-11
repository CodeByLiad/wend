package com.jinatra.wend.engine

import java.time.LocalDate

@JvmInline
value class Minute(
    val value: Int,
) : Comparable<Minute> {
    override fun compareTo(other: Minute): Int = value.compareTo(other.value)
}

enum class Kind { FIXED, FLEXIBLE, OPTIONAL }

enum class Priority { HIGH, NORMAL, LOW }

enum class BlockStatus { PLANNED, IN_PROGRESS, DONE, SKIPPED, MISSED, DROPPED, UNPLACED }

enum class ProposalStatus { OK, INFEASIBLE, CONFLICT, NO_HORIZON, REJECTED }

enum class ChangeType { ADDED, MOVED, SHRUNK, EXTENDED, DROPPED, UNPLACED, MISSED, SKIPPED, MADE_FIXED, RESTORED }

enum class Reason {
    USER_MOVE,
    USER_SKIP,
    USER_ADD,
    SET_FIXED,
    OVERRUN,
    RETRIED,
    UNDO,
    WINDOW_PASSED,
    FIXED_EXPIRED,
    DROPPED_TO_FIT,
    NO_ROOM,
    DEPENDENCY_SHRINK,
    SHRUNK_TO_FIT,
    PAST_BEDTIME,
    FOLLOWS_DEPENDENCY,
    LATE_WAKE,
    BEDTIME_CHANGED,
    DISPLACED,
}

enum class ErrorCode {
    INVALID_INPUT,
    OFF_GRID,
    UNKNOWN_BLOCK,
    INVALID_TARGET,
    NOT_LATER,
    OVERLAPS_LOCKED,
    OUTSIDE_WINDOW,
    IN_THE_PAST,
    PAST_BEDTIME_NOT_ALLOWED,
    DEPENDENCY_VIOLATION,
}

enum class ValidationCode {
    DUPLICATE_ID,
    UNKNOWN_DEPENDENCY,
    SELF_DEPENDENCY,
    DEPENDENCY_CYCLE,
    INVALID_DURATION,
    INVALID_MIN_DURATION,
    EMPTY_INTERVAL,
    INVALID_WINDOW,
    MISSING_PRIORITY,
    MULTIPLE_IN_PROGRESS,
    TIME_OUT_OF_RANGE,
    WAKE_NOT_BEFORE_BEDTIME,
}

sealed interface WindowBound {
    data class Absolute(
        val time: Minute,
    ) : WindowBound

    data class BeforeBedtime(
        val minutes: Int,
    ) : WindowBound

    fun resolve(bedtime: Minute): Int = when (this) {
        is Absolute -> time.value
        is BeforeBedtime -> bedtime.value - minutes
    }
}

data class Window(
    val start: WindowBound? = null,
    val end: WindowBound? = null,
)

data class Block(
    val id: String,
    val title: String,
    val kind: Kind,
    val start: Minute,
    val end: Minute,
    val duration: Int,
    val minDuration: Int,
    val priority: Priority? = null,
    val window: Window? = null,
    val after: String? = null,
    val status: BlockStatus = BlockStatus.PLANNED,
) {
    val length: Int get() = end.value - start.value
}

data class Day(
    val date: LocalDate,
    val wake: Minute,
    val bedtime: Minute,
    val blocks: List<Block>,
)

data class Settings(
    val allowPastBedtime: Boolean = false,
)

data class Change(
    val blockId: String,
    val types: Set<ChangeType>,
    val reason: Reason,
    val causeBlockId: String? = null,
)

data class ValidationError(
    val code: ErrorCode,
    val subCode: ValidationCode? = null,
    val blockId: String? = null,
)

data class Proposal(
    val status: ProposalStatus,
    val day: Day,
    val previousDay: Day,
    val changes: List<Change> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val errors: List<ValidationError> = emptyList(),
)

sealed interface Command {
    data object RebuildDay : Command

    data class LateWake(
        val time: Minute,
    ) : Command

    data class Overrun(
        val id: String,
        val newEnd: Minute,
    ) : Command

    data class RetryBlock(
        val id: String,
    ) : Command

    data class MoveBlock(
        val id: String,
        val start: Minute,
    ) : Command

    data class SkipBlock(
        val id: String,
    ) : Command

    data class AddBlock(
        val block: Block,
        val requestedStart: Minute? = null,
    ) : Command

    data class SetFixed(
        val id: String,
    ) : Command

    data class SetBedtime(
        val time: Minute,
    ) : Command

    data class Undo(
        val previousDay: Day,
    ) : Command
}

internal fun Block.occupies(pinned: String? = null): Boolean = status == BlockStatus.DONE || status == BlockStatus.IN_PROGRESS ||
    (kind == Kind.FIXED && status == BlockStatus.PLANNED) || id == pinned

internal fun Block.candidate(pinned: String? = null): Boolean = kind != Kind.FIXED && status == BlockStatus.PLANNED && id != pinned

internal fun Block.windowStart(day: Day): Int = window?.start?.resolve(day.bedtime) ?: 0

internal fun Block.windowEnd(day: Day): Int = window?.end?.resolve(day.bedtime) ?: 2880

internal fun overlaps(
    a: Block,
    b: Block,
): Boolean = a.start < b.end && b.start < a.end

internal fun horizonStart(
    day: Day,
    now: Minute,
): Int = ((maxOf(day.wake.value, now.value) + 4) / 5) * 5

internal fun horizonEnd(
    day: Day,
    settings: Settings,
): Int = minOf(2880, day.bedtime.value + if (settings.allowPastBedtime) 60 else 0)
