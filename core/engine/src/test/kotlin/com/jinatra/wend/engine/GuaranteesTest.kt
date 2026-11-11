package com.jinatra.wend.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.LocalDate

private data class Case(val day: Day, val now: Minute, val settings: Settings, val command: Command)

/** Arbitrary movable intervals, priorities, windows and acyclic dependencies; disjoint occupying intervals. */
private val cases = Arb.list(Arb.int(0..100000), 1..20).map { values ->
    var hasInProgress = false
    val blocks = values.mapIndexed { index, value ->
        val kind = Kind.entries[value % 3]
        var status = BlockStatus.entries[(value / 3) % BlockStatus.entries.size]
        if (status == BlockStatus.IN_PROGRESS) {
            if (hasInProgress) status = BlockStatus.PLANNED else hasInProgress = true
        }
        val duration = 5 * (1 + value % 18)
        val minimum = 5 * (1 + (value / 18) % (duration / 5))
        val occupying = status == BlockStatus.DONE || status == BlockStatus.IN_PROGRESS || (kind == Kind.FIXED && status == BlockStatus.PLANNED)
        val start = if (occupying) 360 + index * 100 else 300 + value % 250 * 5
        val bounds = when (value % 4) {
            0 -> Window(WindowBound.Absolute(Minute(300 + value % 100 * 5)), WindowBound.Absolute(Minute(1800)))
            1 -> bedtimeWindow(120)
            else -> null
        }
        Block(
            "id$index", "Generated block", kind, Minute(start), Minute(start + duration), duration, minimum,
            if (kind == Kind.FIXED) null else Priority.entries[(value / 7) % 3], bounds,
            if (index > 0 && value % 3 == 0) "id${value % index}" else null, status,
        )
    }
    val seed = values.first()
    val day = Day(LocalDate.of(2026, 11, 16), Minute(300 + seed % 5), Minute(1800 + seed % 150 * 5), blocks)
    val now = Minute(seed % 1500)
    val settings = Settings(seed % 2 == 0)
    val command = when (seed % 5) {
        0 -> Command.LateWake(now)
        1 -> blocks.firstOrNull { it.status == BlockStatus.IN_PROGRESS }?.let { Command.Overrun(it.id, Minute(it.end.value + 30)) } ?: Command.RebuildDay
        2 -> Command.SetBedtime(Minute(1600 + seed % 150 * 5))
        3 -> blocks.firstOrNull { it.kind != Kind.FIXED && it.status == BlockStatus.PLANNED }?.let { Command.MoveBlock(it.id, Minute(600 + seed % 100 * 5)) } ?: Command.RebuildDay
        else -> Command.RebuildDay
    }
    Case(day, now, settings, command)
}

private fun Case.propose() = SchedulingEngine.propose(day, command, now, settings)

class GuaranteesTest :
    FunSpec({
        test("occupying blocks preserve their protected fields") {
            checkAll(1500, cases) { case ->
                validateDay(case.day) shouldBe emptyList()
                val result = case.propose()
                val target = case.command.targetId()
                case.day.blocks.filter { it.occupies() && it.id != target }.forEach { old ->
                    val new = result.day.blocks.single { it.id == old.id }
                    new.copy(status = old.status) shouldBe old
                }
            }
        }
        test("placed blocks satisfy horizon window dependency overlap and length bounds") {
            checkAll(2000, cases) { case ->
                val result = case.propose()
                if (result.status in setOf(ProposalStatus.OK, ProposalStatus.INFEASIBLE)) {
                    val pinned = (case.command as? Command.MoveBlock)?.id
                    val placed = result.day.blocks.filter { it.candidate(pinned) }
                    val occupied = result.day.blocks.filter { it.occupies(pinned) }
                    for (block in placed) {
                        block.start.value shouldBeGreaterThanOrEqual horizonStart(result.day, case.now)
                        block.end.value shouldBeLessThanOrEqual horizonEnd(result.day, case.settings)
                        block.start.value shouldBeGreaterThanOrEqual block.windowStart(result.day)
                        block.end.value shouldBeLessThanOrEqual block.windowEnd(result.day)
                        block.length shouldBeGreaterThanOrEqual block.minDuration
                        block.length shouldBeLessThanOrEqual block.duration
                        block.start.value % 5 shouldBe 0
                        block.end.value % 5 shouldBe 0
                        result.day.blocks.find { it.id == block.after && (it in placed || it in occupied) }?.let {
                            block.start.value shouldBeGreaterThanOrEqual it.end.value
                        }
                        (placed + occupied).filter { it.id != block.id }.forEach { overlaps(block, it) shouldBe false }
                    }
                    validateDay(result.day) shouldBe emptyList()
                }
            }
        }
        test("overlapping occupying blocks report conflict even outside bedtime or windows") {
            checkAll(500, Arb.int(0..280), Arb.int(1..18)) { slot, length ->
                val start = slot * 5
                val fixed = Block("x", "Fixed", Kind.FIXED, Minute(start), Minute(start + length * 5), length * 5, 5)
                val day = Day(LocalDate.of(2026, 11, 16), Minute(0), Minute(5), listOf(fixed, fixed.copy(id = "y", status = BlockStatus.DONE)))
                val result = SchedulingEngine.propose(day, Command.RebuildDay, Minute(0))
                result.status shouldBe ProposalStatus.CONFLICT
                result.day shouldBe day
                result.conflicts shouldBe listOf("x", "y")
            }
        }
        test("same inputs produce identical proposals and never mutate the input") {
            checkAll(1000, cases) { case ->
                val snapshot = case.day.copy(blocks = case.day.blocks.map { it.copy() })
                case.propose() shouldBe case.propose()
                case.day shouldBe snapshot
            }
        }
        test("rebuilt output is idempotent at the same now") {
            checkAll(2500, cases) { case ->
                val result = case.propose()
                if (result.status in setOf(ProposalStatus.OK, ProposalStatus.INFEASIBLE)) {
                    val again = SchedulingEngine.propose(result.day, Command.RebuildDay, case.now, case.settings)
                    again.day shouldBe result.day
                    again.changes shouldBe emptyList()
                    again.status shouldBe result.status
                }
            }
        }
        test("undo restores every field of the previous day") {
            checkAll(1000, cases) { case ->
                val proposal = case.propose()
                val undo = SchedulingEngine.propose(proposal.day, Command.Undo(proposal.previousDay), case.now, case.settings)
                undo.status shouldBe ProposalStatus.OK
                undo.day shouldBe case.day
                undo.changes.forEach {
                    it.types shouldBe setOf(ChangeType.RESTORED)
                    it.reason shouldBe Reason.UNDO
                }
            }
        }
    })
