package com.jinatra.wend.engine

internal fun validateDay(day: Day): List<ValidationError> = buildList {
    fun invalid(
        code: ValidationCode,
        id: String? = null,
    ) {
        add(ValidationError(ErrorCode.INVALID_INPUT, code, id))
    }

    fun time(
        value: Int,
        grid: Boolean,
        id: String? = null,
    ) {
        if (value !in 0..2880) invalid(ValidationCode.TIME_OUT_OF_RANGE, id)
        if (grid && value % 5 != 0) add(ValidationError(ErrorCode.OFF_GRID, blockId = id))
    }
    time(day.wake.value, false)
    time(day.bedtime.value, true)
    if (day.wake >= day.bedtime) invalid(ValidationCode.WAKE_NOT_BEFORE_BEDTIME)
    val byId = day.blocks.associateBy { it.id }
    day.blocks
        .groupBy { it.id }
        .filterValues { it.size > 1 }
        .keys
        .forEach { invalid(ValidationCode.DUPLICATE_ID, it) }
    if (day.blocks.count { it.status == BlockStatus.IN_PROGRESS } > 1) invalid(ValidationCode.MULTIPLE_IN_PROGRESS)
    for (block in day.blocks) {
        val id = block.id
        time(block.start.value, true, id)
        time(block.end.value, true, id)
        if (block.duration <= 0 || block.duration % 5 != 0) invalid(ValidationCode.INVALID_DURATION, id)
        if (block.minDuration <= 0 || block.minDuration % 5 != 0 || block.minDuration > block.duration) {
            invalid(ValidationCode.INVALID_MIN_DURATION, id)
        }
        if (block.end <= block.start) invalid(ValidationCode.EMPTY_INTERVAL, id)
        if (block.kind != Kind.FIXED && block.priority == null) invalid(ValidationCode.MISSING_PRIORITY, id)
        val start = block.windowStart(day)
        val end = block.windowEnd(day)
        time(start, true, id)
        time(end, true, id)
        if (start >= end || end - start < block.minDuration) invalid(ValidationCode.INVALID_WINDOW, id)
        if (block.after != null && block.after !in byId) invalid(ValidationCode.UNKNOWN_DEPENDENCY, id)
        if (block.after == id) invalid(ValidationCode.SELF_DEPENDENCY, id)
    }
    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()

    fun visit(id: String) {
        if (id in visited) return
        if (!visiting.add(id)) {
            invalid(ValidationCode.DEPENDENCY_CYCLE, id)
            return
        }
        byId[id]?.after?.takeIf { it in byId && it != id }?.let(::visit)
        visiting.remove(id)
        visited.add(id)
    }
    byId.keys.forEach(::visit)
}

internal fun validateCommand(
    day: Day,
    command: Command,
    now: Minute,
    settings: Settings,
): List<ValidationError> {
    fun error(
        code: ErrorCode,
        id: String? = null,
    ) = listOf(ValidationError(code, blockId = id))

    fun time(value: Int): List<ValidationError> = when {
        value !in 0..2880 -> listOf(ValidationError(ErrorCode.INVALID_INPUT, ValidationCode.TIME_OUT_OF_RANGE))
        value % 5 != 0 -> error(ErrorCode.OFF_GRID)
        else -> emptyList()
    }
    if (now.value !in 0..2880) return listOf(ValidationError(ErrorCode.INVALID_INPUT, ValidationCode.TIME_OUT_OF_RANGE))
    val id = command.targetId()
    val block = day.blocks.find { it.id == id }
    if (id != null && command !is Command.AddBlock && block == null) return error(ErrorCode.UNKNOWN_BLOCK, id)
    val retryable = setOf(BlockStatus.PLANNED, BlockStatus.MISSED, BlockStatus.DROPPED, BlockStatus.UNPLACED)
    when (command) {
        is Command.Overrun -> {
            if (block!!.status != BlockStatus.IN_PROGRESS) return error(ErrorCode.INVALID_TARGET, id)
            time(command.newEnd.value).takeIf { it.isNotEmpty() }?.let { return it }
            if (command.newEnd <= block.end) return error(ErrorCode.NOT_LATER, id)
        }

        is Command.RetryBlock -> {
            if (block!!.kind == Kind.FIXED || block.status !in retryable) return error(ErrorCode.INVALID_TARGET, id)
        }

        is Command.SkipBlock -> {
            if (block!!.status !in retryable) return error(ErrorCode.INVALID_TARGET, id)
        }

        is Command.SetFixed -> {
            if (block!!.kind == Kind.FIXED || block.status != BlockStatus.PLANNED) return error(ErrorCode.INVALID_TARGET, id)
            day.blocks.find { it.id != id && it.occupies() && overlaps(block, it) }?.let { return error(ErrorCode.OVERLAPS_LOCKED, it.id) }
        }

        is Command.MoveBlock -> {
            if (block!!.kind == Kind.FIXED || block.status !in retryable) return error(ErrorCode.INVALID_TARGET, id)
            time(command.start.value).takeIf { it.isNotEmpty() }?.let { return it }
            if (command.start.value < horizonStart(day, now)) return error(ErrorCode.IN_THE_PAST, id)
            val end = command.start.value.toLong() + block.duration
            if (end > horizonEnd(day, settings)) return error(ErrorCode.PAST_BEDTIME_NOT_ALLOWED, id)
            val moved = block.copy(start = command.start, end = Minute(end.toInt()))
            if (moved.start.value < block.windowStart(day) ||
                moved.end.value > block.windowEnd(day)
            ) {
                return error(ErrorCode.OUTSIDE_WINDOW, id)
            }
            val dep = day.blocks.find { it.id == block.after }
            if (dep != null && (dep.occupies() || dep.status == BlockStatus.PLANNED) &&
                moved.start < dep.end
            ) {
                return error(ErrorCode.DEPENDENCY_VIOLATION, id)
            }
            if (day.blocks.any { it.after == id && it.occupies() && moved.end > it.start }) {
                return error(ErrorCode.DEPENDENCY_VIOLATION, id)
            }
            day.blocks.find { it.id != id && it.occupies() && overlaps(moved, it) }?.let { return error(ErrorCode.OVERLAPS_LOCKED, it.id) }
        }

        is Command.AddBlock -> {
            val errors = validateDay(day.copy(blocks = day.blocks + command.block))
            if (errors.isNotEmpty()) return errors
            if (command.block.status != BlockStatus.PLANNED) return error(ErrorCode.INVALID_TARGET, id)
            command.requestedStart?.let { time(it.value).takeIf { e -> e.isNotEmpty() }?.let { e -> return e } }
        }

        is Command.SetBedtime -> {
            time(command.time.value).takeIf { it.isNotEmpty() }?.let { return it }
            return validateDay(day.copy(bedtime = command.time))
        }

        is Command.LateWake -> {
            return validateDay(day.copy(wake = command.time))
        }

        is Command.Undo -> {
            return validateDay(command.previousDay)
        }

        Command.RebuildDay -> Unit
    }
    return emptyList()
}

internal fun Command.targetId(): String? = when (this) {
    is Command.Overrun -> id
    is Command.RetryBlock -> id
    is Command.MoveBlock -> id
    is Command.SkipBlock -> id
    is Command.SetFixed -> id
    is Command.AddBlock -> block.id
    else -> null
}
