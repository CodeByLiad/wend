package com.jinatra.wend.engine

/** The only engine entry point. All mutable working state belongs to this invocation. */
object SchedulingEngine {
    fun propose(day: Day, command: Command, now: Minute, settings: Settings = Settings()): Proposal {
        val errors = validateDay(day).ifEmpty { validateCommand(day, command, now, settings) }
        if (errors.isNotEmpty()) return Proposal(ProposalStatus.REJECTED, day, day, errors = errors)
        var edited = applyDirectEdit(day, command)
        var fastAdd = false
        if (command is Command.AddBlock && command.block.kind != Kind.FIXED) {
            val slot = fastAddSlot(day, command, now, settings)
            if (slot != null) {
                edited = edited.replace(slot)
                fastAdd = true
            }
        }
        val pinned = (command as? Command.MoveBlock)?.id
        val stopped = command is Command.SkipBlock || command is Command.SetFixed || command is Command.Undo || fastAdd
        val conflicts = if (stopped) emptyList() else conflicts(edited, pinned)
        val work = Rebuild(edited, command, now, settings, pinned)
        val status = when {
            stopped -> ProposalStatus.OK

            conflicts.isNotEmpty() -> ProposalStatus.CONFLICT

            work.start >= work.end -> ProposalStatus.NO_HORIZON

            else -> {
                edited = work.run()
                if (edited.blocks.any { it.status == BlockStatus.UNPLACED }) ProposalStatus.INFEASIBLE else ProposalStatus.OK
            }
        }
        return Proposal(status, edited, day, explain(day, edited, command, work), conflicts)
    }
}

private fun Day.replace(block: Block): Day = copy(blocks = blocks.map { if (it.id == block.id) block else it })

private fun applyDirectEdit(day: Day, command: Command): Day {
    val target = day.blocks.find { it.id == command.targetId() }
    return when (command) {
        is Command.LateWake -> day.copy(wake = command.time)
        is Command.SetBedtime -> day.copy(bedtime = command.time)
        is Command.Overrun -> day.replace(target!!.copy(end = command.newEnd))
        is Command.RetryBlock -> day.replace(target!!.copy(status = BlockStatus.PLANNED))
        is Command.SkipBlock -> day.replace(target!!.copy(status = BlockStatus.SKIPPED))
        is Command.SetFixed -> day.replace(target!!.copy(kind = Kind.FIXED))
        is Command.MoveBlock -> day.replace(target!!.copy(start = command.start, end = Minute(command.start.value + target.duration), status = BlockStatus.PLANNED))
        is Command.AddBlock -> day.copy(blocks = day.blocks + command.block)
        is Command.Undo -> command.previousDay
        Command.RebuildDay -> day
    }
}

private fun conflicts(day: Day, pinned: String?): List<String> {
    val occupying = day.blocks.filter { it.occupies(pinned) }
    val ids = mutableSetOf<String>()
    for (i in occupying.indices) {
        for (j in i + 1 until occupying.size) {
            if (overlaps(occupying[i], occupying[j])) {
                ids += occupying[i].id
                ids += occupying[j].id
            }
        }
    }
    return ids.sorted()
}

private data class Gap(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

private fun freeGaps(start: Int, end: Int, occupied: List<Block>): List<Gap> {
    if (start >= end) return emptyList()
    val result = mutableListOf<Gap>()
    var cursor = start
    for (block in occupied.sortedWith(compareBy<Block> { it.start }.thenBy { it.id })) {
        if (block.end.value <= cursor || block.start.value >= end) continue
        if (block.start.value > cursor) result += Gap(cursor, block.start.value)
        cursor = maxOf(cursor, block.end.value)
        if (cursor >= end) break
    }
    if (cursor < end) result += Gap(cursor, end)
    return result
}

private fun fastAddSlot(day: Day, command: Command.AddBlock, now: Minute, settings: Settings): Block? {
    val block = command.block
    val dep = day.blocks.find { it.id == block.after && (it.occupies() || it.status == BlockStatus.PLANNED) }
    val start = maxOf(command.requestedStart?.value ?: 0, horizonStart(day, now), block.windowStart(day), dep?.end?.value ?: 0)
    val end = minOf(horizonEnd(day, settings), block.windowEnd(day))
    val occupied = day.blocks.filter { it.status in setOf(BlockStatus.PLANNED, BlockStatus.IN_PROGRESS, BlockStatus.DONE) }
    val gap = freeGaps(start, end, occupied).firstOrNull { it.length >= block.duration } ?: return null
    return block.copy(start = Minute(gap.start), end = Minute(gap.start + block.duration))
}

private class Rebuild(
    private val day: Day,
    private val command: Command,
    now: Minute,
    settings: Settings,
    private val pinned: String?,
) {
    val start = horizonStart(day, now)
    val end = horizonEnd(day, settings)
    private val blocks = day.blocks.associateByTo(linkedMapOf()) { it.id }
    private val originals = day.blocks.associate { block ->
        val original = if (command is Command.AddBlock && command.block.id == block.id) {
            command.requestedStart?.value ?: start
        } else {
            block.start.value
        }
        block.id to original
    }
    val placed = mutableSetOf<String>()
    val dependencyShrinks = mutableSetOf<String>()
    val gapShrinks = mutableSetOf<String>()

    fun run(): Day {
        expireFixedBlocks()
        windowCheck()
        for (id in order()) placeEachBlock(id)
        return day.copy(blocks = day.blocks.map { blocks.getValue(it.id) })
    }

    private fun expireFixedBlocks() {
        for ((id, block) in blocks.toMap()) {
            if (block.kind == Kind.FIXED && block.status == BlockStatus.PLANNED && block.end.value <= start) {
                blocks[id] = block.copy(status = BlockStatus.MISSED)
            }
        }
    }

    private fun windowCheck() {
        for ((id, block) in blocks.toMap()) {
            if (block.candidate(pinned) && minOf(end, block.windowEnd(day)) - maxOf(start, block.windowStart(day)) < block.minDuration) {
                blocks[id] = block.copy(status = BlockStatus.MISSED)
            }
        }
    }

    private fun order(): List<String> {
        val candidates = blocks.values.filter { it.candidate(pinned) }.sortedWith(
            compareBy<Block> { it.priority }.thenBy { originals.getValue(it.id) }.thenBy { it.id },
        )
        val visited = mutableSetOf<String>()
        val result = mutableListOf<String>()
        fun visit(block: Block) {
            if (!visited.add(block.id)) return
            blocks[block.after]?.takeIf { it.candidate(pinned) }?.let(::visit)
            result += block.id
        }
        candidates.forEach(::visit)
        return result
    }

    private fun dependencies(block: Block): Gap {
        val dep = blocks[block.after]?.takeIf { it.occupies(pinned) || it.id in placed }
        val dependentStart = blocks.values.filter { it.after == block.id && it.occupies(pinned) }.minOfOrNull { it.start.value } ?: end
        return Gap(maxOf(start, block.windowStart(day), dep?.end?.value ?: 0), minOf(end, block.windowEnd(day), dependentStart))
    }

    private fun gaps(block: Block): List<Gap> {
        val range = dependencies(block)
        val occupied = blocks.values.filter { it.id != block.id && (it.occupies(pinned) || it.id in placed) }
        return freeGaps(range.start, range.end, occupied)
    }

    private fun keep(block: Block): Block? {
        if (command is Command.AddBlock && block.id == command.block.id) return null
        if (block.length !in block.minDuration..block.duration) return null
        return block.takeIf { gaps(block).any { gap -> block.start.value >= gap.start && block.end.value <= gap.end } }
    }

    private fun later(block: Block): Block? {
        for (gap in gaps(block)) {
            val position = maxOf(gap.start, originals.getValue(block.id))
            if (gap.end - position >= block.duration) return block.at(position, block.duration)
        }
        return null
    }

    private fun earlier(block: Block): Block? {
        val original = originals.getValue(block.id)
        for (gap in gaps(block).asReversed()) {
            val position = minOf(gap.end - block.duration, original - 5)
            if (position >= gap.start) return block.at(position, block.duration)
        }
        return null
    }

    private fun shortenItsDependency(block: Block): Block? {
        val dep = blocks[block.after] ?: return null
        if (dep.id !in placed || dep.priority == null || dep.priority <= block.priority!!) return null
        var shortened = dep
        while (shortened.length > dep.minDuration) {
            shortened = shortened.copy(end = Minute(shortened.end.value - 5))
            blocks[dep.id] = shortened
            val full = later(block) ?: earlier(block)
            if (full != null) {
                dependencyShrinks += dep.id
                return full
            }
        }
        val small = shrink(block)
        if (small != null) {
            if (shortened != dep) dependencyShrinks += dep.id
            return small
        }
        blocks[dep.id] = dep
        return null
    }

    private fun shrink(block: Block): Block? {
        val original = originals.getValue(block.id)
        val gap = gaps(block).filter { it.length >= block.minDuration }.sortedWith(
            compareByDescending<Gap> { it.length }
                .thenBy { if (it.end > original) 0 else 1 }
                .thenBy { if (it.end > original) it.start else -it.end },
        ).firstOrNull() ?: return null
        val length = minOf(block.duration, gap.length / 5 * 5)
        val position = if (gap.end > original) gap.start else gap.end - length
        if (length < block.length) gapShrinks += block.id
        return block.at(position, length)
    }

    private fun giveUp(block: Block): Block = block.copy(
        status = if (block.kind == Kind.OPTIONAL) BlockStatus.DROPPED else BlockStatus.UNPLACED,
    )

    private fun placeEachBlock(id: String) {
        val block = blocks.getValue(id)
        val result = keep(block) ?: later(block) ?: earlier(block) ?: shortenItsDependency(block) ?: shrink(block)
        blocks[id] = result ?: giveUp(block)
        if (result != null) placed += id
    }
}

private fun Block.at(start: Int, length: Int): Block = copy(start = Minute(start), end = Minute(start + length), status = BlockStatus.PLANNED)

private fun explain(before: Day, after: Day, command: Command, work: Rebuild): List<Change> {
    val old = before.blocks.associateBy { it.id }
    val new = after.blocks.associateBy { it.id }
    val changed = (old.keys + new.keys).filter { old[it] != new[it] }
    return changed.map { id ->
        val a = old[id]
        val b = new[id]
        if (command is Command.Undo) return@map Change(id, setOf(ChangeType.RESTORED), Reason.UNDO)
        if (a == null) return@map Change(id, setOf(ChangeType.ADDED), Reason.USER_ADD)
        check(b != null)
        val types = buildSet {
            if (a.start != b.start) add(ChangeType.MOVED)
            if (b.length < a.length) add(ChangeType.SHRUNK)
            if (command is Command.Overrun && id == command.id && b.end > a.end) add(ChangeType.EXTENDED)
            if (a.kind != b.kind && b.kind == Kind.FIXED) add(ChangeType.MADE_FIXED)
            if (a.status != b.status) {
                when (b.status) {
                    BlockStatus.DROPPED -> add(ChangeType.DROPPED)
                    BlockStatus.UNPLACED -> add(ChangeType.UNPLACED)
                    BlockStatus.MISSED -> add(ChangeType.MISSED)
                    BlockStatus.SKIPPED -> add(ChangeType.SKIPPED)
                    else -> Unit
                }
            }
        }
        val targetReason = if (id == command.targetId()) {
            when (command) {
                is Command.MoveBlock -> Reason.USER_MOVE
                is Command.SkipBlock -> Reason.USER_SKIP
                is Command.SetFixed -> Reason.SET_FIXED
                is Command.Overrun -> Reason.OVERRUN
                is Command.RetryBlock -> Reason.RETRIED
                is Command.AddBlock -> Reason.USER_ADD
                else -> null
            }
        } else {
            null
        }
        val reason = targetReason ?: when {
            b.status == BlockStatus.MISSED && a.status != b.status -> if (b.kind == Kind.FIXED) Reason.FIXED_EXPIRED else Reason.WINDOW_PASSED
            b.status == BlockStatus.DROPPED && a.status != b.status -> Reason.DROPPED_TO_FIT
            b.status == BlockStatus.UNPLACED && a.status != b.status -> Reason.NO_ROOM
            id in work.dependencyShrinks -> Reason.DEPENDENCY_SHRINK
            id in work.gapShrinks -> Reason.SHRUNK_TO_FIT
            b.end > after.bedtime -> Reason.PAST_BEDTIME
            b.after in changed -> Reason.FOLLOWS_DEPENDENCY
            command is Command.LateWake -> Reason.LATE_WAKE
            command is Command.SetBedtime -> Reason.BEDTIME_CHANGED
            else -> Reason.DISPLACED
        }
        val cause = if (reason == Reason.DISPLACED) {
            after.blocks.filter {
                it.id != id && (it.occupies((command as? Command.MoveBlock)?.id) || it.id in work.placed) && overlaps(it, a)
            }.minWithOrNull(compareBy<Block> { it.start }.thenBy { it.id })?.id
        } else {
            null
        }
        Change(id, types, reason, cause)
    }
}
