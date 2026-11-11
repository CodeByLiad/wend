package com.jinatra.wend.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private val sample = Block("x", "Task", Kind.FLEXIBLE, minute("09:00"), minute("09:30"), 30, 15, Priority.NORMAL)
private fun smallDay(vararg blocks: Block) = BaseDays.c.copy(blocks = blocks.toList())
private fun run(day: Day, command: Command = Command.RebuildDay, now: String = "08:00", settings: Settings = Settings()) = SchedulingEngine.propose(day, command, minute(now), settings)

class CommandsAndValidationTest :
    FunSpec({
        val invalidDays = listOf(
            ValidationCode.DUPLICATE_ID to smallDay(sample, sample),
            ValidationCode.UNKNOWN_DEPENDENCY to smallDay(sample.copy(after = "missing")),
            ValidationCode.SELF_DEPENDENCY to smallDay(sample.copy(after = "x")),
            ValidationCode.DEPENDENCY_CYCLE to smallDay(sample.copy(after = "y"), sample.copy(id = "y", after = "x")),
            ValidationCode.INVALID_DURATION to smallDay(sample.copy(duration = 0)),
            ValidationCode.INVALID_DURATION to smallDay(sample.copy(duration = 31)),
            ValidationCode.INVALID_MIN_DURATION to smallDay(sample.copy(minDuration = 0)),
            ValidationCode.INVALID_MIN_DURATION to smallDay(sample.copy(minDuration = 16)),
            ValidationCode.INVALID_MIN_DURATION to smallDay(sample.copy(minDuration = 35)),
            ValidationCode.EMPTY_INTERVAL to smallDay(sample.copy(end = sample.start)),
            ValidationCode.INVALID_WINDOW to smallDay(sample.copy(window = window("10:00", "09:00"))),
            ValidationCode.INVALID_WINDOW to smallDay(sample.copy(window = window("09:00", "09:10"))),
            ValidationCode.MISSING_PRIORITY to smallDay(sample.copy(priority = null)),
            ValidationCode.MULTIPLE_IN_PROGRESS to smallDay(sample.copy(status = BlockStatus.IN_PROGRESS), sample.copy(id = "y", status = BlockStatus.IN_PROGRESS)),
            ValidationCode.TIME_OUT_OF_RANGE to smallDay(sample.copy(start = Minute(-5))),
            ValidationCode.TIME_OUT_OF_RANGE to smallDay(sample).copy(bedtime = Minute(2885)),
            ValidationCode.WAKE_NOT_BEFORE_BEDTIME to smallDay(sample).copy(wake = minute("23:00")),
        )
        invalidDays.forEachIndexed { index, (code, day) ->
            test("typed invalid input $index $code leaves the entire day unchanged") {
                val proposal = run(day)
                proposal.status shouldBe ProposalStatus.REJECTED
                proposal.day shouldBe day
                proposal.previousDay shouldBe day
                proposal.changes shouldBe emptyList()
                proposal.errors.map { it.subCode } shouldContain code
            }
        }
        test("off grid block time and relative window are rejected") {
            run(smallDay(sample.copy(start = Minute(541)))).errors.first().code shouldBe ErrorCode.OFF_GRID
            run(smallDay(sample.copy(window = Window(WindowBound.BeforeBedtime(61))))).errors.map { it.code } shouldContain ErrorCode.OFF_GRID
        }
        test("off grid command arguments are rejected") {
            val day = smallDay(sample)
            val commands = listOf(
                Command.MoveBlock("x", Minute(541)),
                Command.SetBedtime(Minute(1381)),
                Command.AddBlock(sample.copy(id = "y"), Minute(541)),
            )
            commands.forEach { run(day, it).errors.first().code shouldBe ErrorCode.OFF_GRID }
            run(day.inProgress("x"), Command.Overrun("x", Minute(601))).errors.first().code shouldBe ErrorCode.OFF_GRID
        }
        test("unknown target ids have their own error") {
            listOf(
                Command.MoveBlock("missing", Minute(600)),
                Command.SkipBlock("missing"),
                Command.RetryBlock("missing"),
                Command.SetFixed("missing"),
                Command.Overrun("missing", Minute(600)),
            ).forEach {
                run(smallDay(sample), it).errors shouldBe listOf(ValidationError(ErrorCode.UNKNOWN_BLOCK, blockId = "missing"))
            }
        }
        test("move validation follows the documented order") {
            val day = smallDay(sample.copy(window = window("09:00", "10:00")))
            run(day, Command.MoveBlock("x", minute("07:00"))).errors.first().code shouldBe ErrorCode.IN_THE_PAST
            run(day, Command.MoveBlock("x", minute("22:45"))).errors.first().code shouldBe ErrorCode.PAST_BEDTIME_NOT_ALLOWED
            run(day, Command.MoveBlock("x", minute("12:00"))).errors.first().code shouldBe ErrorCode.OUTSIDE_WINDOW
            val dependent = sample.copy(id = "y", after = "x", start = minute("10:00"), end = minute("10:30"))
            run(smallDay(sample, dependent), Command.MoveBlock("y", minute("09:00"))).errors.first().code shouldBe ErrorCode.DEPENDENCY_VIOLATION
        }
        test("invalid command statuses and kinds are rejected") {
            val day = smallDay(sample.copy(status = BlockStatus.DONE))
            listOf(Command.MoveBlock("x", Minute(600)), Command.SkipBlock("x"), Command.RetryBlock("x"), Command.SetFixed("x"), Command.Overrun("x", Minute(600))).forEach {
                run(day, it).errors.first().code shouldBe ErrorCode.INVALID_TARGET
            }
            run(smallDay(sample.copy(kind = Kind.FIXED)), Command.RetryBlock("x")).errors.first().code shouldBe ErrorCode.INVALID_TARGET
            run(smallDay(sample), Command.AddBlock(sample.copy(id = "y", status = BlockStatus.DONE))).errors.first().code shouldBe ErrorCode.INVALID_TARGET
        }
        test("overrun must extend the current interval") {
            val day = smallDay(sample).inProgress("x")
            run(day, Command.Overrun("x", sample.end)).errors.first().code shouldBe ErrorCode.NOT_LATER
            run(day, Command.Overrun("x", sample.start)).errors.first().code shouldBe ErrorCode.NOT_LATER
        }
        test("set fixed preserves target lengths and changes nothing else") {
            val day = smallDay(sample.copy(end = minute("09:15")), sample.copy(id = "y", start = minute("10:00"), end = minute("10:30")))
            val proposal = run(day, Command.SetFixed("x"))
            proposal.status shouldBe ProposalStatus.OK
            proposal.day shouldBe day.edit("x") { it.copy(kind = Kind.FIXED) }
            proposal.changes shouldBe listOf(Change("x", setOf(ChangeType.MADE_FIXED), Reason.SET_FIXED))
        }
        test("set fixed rejects an overlap with an occupying block") {
            run(smallDay(sample, sample.copy(id = "y", kind = Kind.FIXED)), Command.SetFixed("x")).errors.first().code shouldBe ErrorCode.OVERLAPS_LOCKED
        }
        test("skipped blocks free their interval on a later rebuild") {
            val skipped = sample.copy(kind = Kind.FIXED, status = BlockStatus.SKIPPED)
            val day = smallDay(skipped, sample.copy(id = "y"))
            run(day).day shouldBe day
        }
        test("dropped and unplaced blocks stay dormant until retried") {
            for (status in listOf(BlockStatus.MISSED, BlockStatus.DROPPED, BlockStatus.UNPLACED)) {
                val day = smallDay(sample.copy(status = status))
                run(day).day shouldBe day
                val retry = run(day, Command.RetryBlock("x"))
                retry.day shouldBe smallDay(sample)
                retry.changes shouldBe listOf(Change("x", emptySet(), Reason.RETRIED))
            }
        }
        test("skip then retry resolves the example six choice") {
            val first = run(BaseDays.a, Command.LateWake(minute("08:00")))
            val skipped = run(first.day, Command.SkipBlock("A1"))
            val retry = run(skipped.day, Command.RetryBlock("A2"))
            retry.status shouldBe ProposalStatus.OK
            retry.day.blocks.single { it.id == "A2" }.let {
                it.start shouldBe minute("08:00")
                it.end shouldBe minute("08:15")
                it.status shouldBe BlockStatus.PLANNED
            }
        }
        test("no horizon keeps only the direct edit") {
            val day = smallDay(sample)
            val result = run(day, Command.SetBedtime(minute("22:00")), "23:00")
            result.status shouldBe ProposalStatus.NO_HORIZON
            result.day shouldBe day.copy(bedtime = minute("22:00"))
            result.previousDay shouldBe day
        }
        test("validation precedes conflict and conflict precedes no horizon") {
            val fixed = sample.copy(kind = Kind.FIXED)
            val day = smallDay(fixed, fixed.copy(id = "y"))
            run(day, now = "23:10").status shouldBe ProposalStatus.CONFLICT
            run(day.copy(blocks = day.blocks + fixed), now = "23:10").status shouldBe ProposalStatus.REJECTED
        }
        test("keep repairs out of bounds lengths but preserves a valid shrunk length") {
            for (length in listOf(5, 60)) {
                val result = run(smallDay(sample.copy(end = Minute(sample.start.value + length))))
                result.day.blocks.single().length shouldBe 30
            }
            val shrunk = smallDay(sample.copy(end = minute("09:15")))
            run(shrunk).day shouldBe shrunk
        }
        test("extended horizon is capped at 2880 and remains valid") {
            val day = smallDay(sample).copy(bedtime = Minute(2880))
            run(day, now = "48:00", settings = Settings(true)).status shouldBe ProposalStatus.NO_HORIZON
            val result = run(day, now = "47:50", settings = Settings(true))
            result.day.blocks.single().status shouldBe BlockStatus.MISSED
            validateDay(result.day) shouldBe emptyList()
        }
        test("expired fixed boundary is inclusive and touching intervals do not conflict") {
            val day = smallDay(sample.copy(kind = Kind.FIXED), sample.copy(id = "y", kind = Kind.FIXED, start = minute("09:30"), end = minute("10:00")))
            val result = run(day, now = "09:30")
            result.status shouldBe ProposalStatus.OK
            result.day.blocks.first().status shouldBe BlockStatus.MISSED
            result.day.blocks.last().status shouldBe BlockStatus.PLANNED
        }
        test("move pin prevents dependency from extending into its occupying dependent") {
            val parent = sample.copy(priority = Priority.NORMAL)
            val child = sample.copy(id = "y", after = "x", start = minute("10:00"), end = minute("10:30"), priority = Priority.HIGH)
            val result = run(smallDay(parent, child), Command.MoveBlock("y", minute("09:30")))
            result.day.blocks shouldBe listOf(parent, child.copy(start = minute("09:30"), end = minute("10:00")))
        }
        test("moving a prerequisite after an occupying dependent is rejected without changes") {
            for (status in listOf(BlockStatus.DONE, BlockStatus.IN_PROGRESS, BlockStatus.PLANNED)) {
                val dependent = sample.copy(
                    id = "y",
                    kind = Kind.FIXED,
                    status = status,
                    after = "x",
                    start = minute("10:00"),
                    end = minute("10:30"),
                )
                val day = smallDay(sample, dependent)
                val rejected = run(day, Command.MoveBlock("x", minute("11:00")))
                rejected shouldBe Proposal(
                    ProposalStatus.REJECTED,
                    day,
                    day,
                    errors = listOf(ValidationError(ErrorCode.DEPENDENCY_VIOLATION, blockId = "x")),
                )
                val touching = run(day, Command.MoveBlock("x", minute("09:30")))
                touching.status shouldBe ProposalStatus.OK
                touching.day shouldBe day.at("x", "09:30", "10:00")
                val again = run(touching.day)
                again.day shouldBe touching.day
                again.changes shouldBe emptyList()
            }
        }
        test("add fast path honors dependency window and ignores dormant intervals") {
            val parent = sample.copy(status = BlockStatus.DONE)
            val dormant = sample.copy(id = "z", status = BlockStatus.DROPPED, start = minute("09:30"), end = minute("10:00"))
            val added = sample.copy(id = "y", after = "x", window = window("09:00", "10:00"))
            val day = smallDay(parent, dormant)
            val result = run(day, Command.AddBlock(added, minute("08:00")))
            result.day shouldBe day.add(added.copy(start = minute("09:30"), end = minute("10:00")))
            result.changes shouldBe listOf(Change("y", setOf(ChangeType.ADDED), Reason.USER_ADD))
        }
        test("undo restores added and removed blocks with exclusive change types") {
            val before = smallDay(sample)
            val added = run(before, Command.AddBlock(sample.copy(id = "y")))
            val undone = run(added.day, Command.Undo(added.previousDay))
            undone.day shouldBe before
            undone.changes shouldBe listOf(Change("y", setOf(ChangeType.RESTORED), Reason.UNDO))
            run(before, Command.Undo(added.day)).changes shouldBe listOf(Change("y", setOf(ChangeType.RESTORED), Reason.UNDO))
        }
        test("v02 XY counterexample remains stable with dormant unplaced block") {
            val x = sample.copy(start = minute("08:00"), end = minute("09:00"), duration = 60, minDuration = 60, window = window("10:00", "11:00"))
            val y = x.copy(id = "y", start = minute("09:00"), end = minute("10:00"), window = window("09:30", "10:30"))
            val result = run(smallDay(x, y))
            result.status shouldBe ProposalStatus.INFEASIBLE
            result.day.blocks.last().status shouldBe BlockStatus.UNPLACED
            run(result.day).day shouldBe result.day
            run(result.day).changes shouldBe emptyList()
        }
        test("very large target durations cannot overflow placement or move bounds") {
            val enormous = sample.copy(duration = 2_147_483_645)
            val day = smallDay(enormous.copy(end = minute("09:05")))
            val result = run(day)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks.single().let {
                it.start shouldBe minute("08:00")
                it.end shouldBe minute("23:00")
            }
            run(day, Command.MoveBlock("x", minute("10:00"))).errors.first().code shouldBe ErrorCode.PAST_BEDTIME_NOT_ALLOWED
        }
    })
