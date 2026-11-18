package com.jinatra.wend.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val editable = Block("a", "Task", Kind.FLEXIBLE, Minute(540), Minute(570), 30, 15, Priority.NORMAL)
private val neighbor = editable.copy(id = "b", title = "Next", start = Minute(600), end = Minute(630))
private fun editingDay(vararg blocks: Block) = BaseDays.c.copy(blocks = blocks.toList())
private fun edit(day: Day, replacement: Block, id: String = "a", now: Int = 480, settings: Settings = Settings()) = SchedulingEngine.propose(day, Command.EditBlock(id, replacement), Minute(now), settings)

class EditBlockTest :
    FunSpec({
        test("title-only edit preserves all intervals and records the edit") {
            val day = editingDay(editable, neighbor)
            val replacement = editable.copy(title = "New title")
            val result = edit(day, replacement)
            result.status shouldBe ProposalStatus.OK
            result.day shouldBe day.copy(blocks = listOf(replacement, neighbor))
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED), Reason.USER_EDIT))
            result.previousDay shouldBe day
        }
        test("explicit full and minimum duration edit expands the target") {
            val replacement = editable.copy(duration = 60, minDuration = 40, end = Minute(600))
            val result = edit(editingDay(editable, neighbor), replacement)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(replacement, neighbor)
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.EXTENDED), Reason.USER_EDIT))
        }
        test("shorter duration is placed at the requested start without retaining an oversized interval") {
            val replacement = editable.copy(duration = 15)
            val result = edit(editingDay(editable, neighbor), replacement)
            result.day.blocks shouldBe listOf(replacement.copy(end = Minute(555)), neighbor)
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.SHRUNK), Reason.USER_EDIT))
        }
        test("flexible requested interval is not pinned over a locked block") {
            val locked = neighbor.copy(kind = Kind.FIXED)
            val result = edit(editingDay(editable, locked), editable.copy(start = Minute(600), end = Minute(630)))
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(editable.copy(start = Minute(630), end = Minute(660)), locked)
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.MOVED), Reason.USER_EDIT))
        }
        test("new dependency is honored without changing identity or list order") {
            val result = edit(editingDay(editable, neighbor), editable.copy(after = "b"))
            result.day.blocks shouldBe listOf(editable.copy(after = "b", start = Minute(630), end = Minute(660)), neighbor)
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.MOVED), Reason.USER_EDIT))
        }
        test("fixed edit displaces flexible neighbors through the rebuild") {
            val replacement = editable.copy(kind = Kind.FIXED, duration = 75, end = Minute(615))
            val result = edit(editingDay(editable, neighbor), replacement)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(replacement, neighbor.copy(start = Minute(615), end = Minute(645)))
            result.changes shouldBe listOf(
                Change("a", setOf(ChangeType.EDITED, ChangeType.EXTENDED, ChangeType.MADE_FIXED), Reason.USER_EDIT),
                Change("b", setOf(ChangeType.MOVED), Reason.DISPLACED, "a"),
            )
        }
        test("fixed overlap returns only the direct edit with conflict ids") {
            val locked = neighbor.copy(kind = Kind.FIXED)
            val replacement = editable.copy(kind = Kind.FIXED, end = Minute(615))
            val result = edit(editingDay(editable, locked), replacement)
            result.status shouldBe ProposalStatus.CONFLICT
            result.conflicts shouldBe listOf("a", "b")
            result.day.blocks shouldBe listOf(replacement, locked)
        }
        test("fixed can become flexible and move into the remaining horizon") {
            val day = editingDay(editable.copy(kind = Kind.FIXED))
            val result = edit(day, editable, now = 600)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(editable.copy(start = Minute(600), end = Minute(630)))
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.MOVED), Reason.USER_EDIT))
        }
        test("editing cannot revive dormant blocks or rewrite historical statuses") {
            BlockStatus.entries.filter { it != BlockStatus.PLANNED }.forEach { status ->
                val day = editingDay(editable.copy(status = status))
                val result = edit(day, editable)
                result.status shouldBe ProposalStatus.REJECTED
                result.errors shouldBe listOf(ValidationError(ErrorCode.INVALID_TARGET, blockId = "a"))
                result.day shouldBe day
                result.changes shouldBe emptyList()
            }
        }
        test("identity and status are immutable through editing and unknown targets are rejected") {
            val day = editingDay(editable)
            listOf(editable.copy(id = "new"), editable.copy(status = BlockStatus.DONE)).forEach {
                edit(day, it).errors shouldBe listOf(ValidationError(ErrorCode.INVALID_TARGET, blockId = "a"))
            }
            edit(day, editable, id = "missing").errors shouldBe listOf(ValidationError(ErrorCode.UNKNOWN_BLOCK, blockId = "missing"))
        }
        test("replacement validates numeric fields windows and dependency graph before placement") {
            val day = editingDay(editable, neighbor.copy(after = "a"))
            val invalid = listOf(
                editable.copy(duration = 0) to ValidationCode.INVALID_DURATION,
                editable.copy(minDuration = 35) to ValidationCode.INVALID_MIN_DURATION,
                editable.copy(window = window("10:00", "09:00")) to ValidationCode.INVALID_WINDOW,
                editable.copy(after = "missing") to ValidationCode.UNKNOWN_DEPENDENCY,
                editable.copy(after = "a") to ValidationCode.SELF_DEPENDENCY,
                editable.copy(after = "b") to ValidationCode.DEPENDENCY_CYCLE,
                editable.copy(priority = null) to ValidationCode.MISSING_PRIORITY,
            )
            invalid.forEach { (replacement, subCode) ->
                val result = edit(day, replacement)
                result.status shouldBe ProposalStatus.REJECTED
                result.day shouldBe day
                result.errors.any { it.subCode == subCode } shouldBe true
            }
            edit(day, editable.copy(start = Minute(541))).errors.first().code shouldBe ErrorCode.OFF_GRID
        }
        test("fixed interval checks run horizon then window then locked dependencies") {
            val day = editingDay(editable)
            val fixed = editable.copy(kind = Kind.FIXED, window = window("09:00", "10:00"))
            edit(day, fixed.copy(start = Minute(420))).errors.first().code shouldBe ErrorCode.IN_THE_PAST
            edit(day, fixed.copy(end = Minute(1440))).errors.first().code shouldBe ErrorCode.PAST_BEDTIME_NOT_ALLOWED
            edit(day, fixed.copy(end = Minute(615))).errors.first().code shouldBe ErrorCode.OUTSIDE_WINDOW
            val prerequisite = neighbor.copy(kind = Kind.FIXED)
            edit(editingDay(editable, prerequisite), fixed.copy(after = "b")).errors.first().code shouldBe ErrorCode.DEPENDENCY_VIOLATION
            val dependent = neighbor.copy(kind = Kind.FIXED, after = "a")
            edit(editingDay(editable, dependent), fixed.copy(window = null, end = Minute(615))).errors.first().code shouldBe ErrorCode.DEPENDENCY_VIOLATION
            edit(editingDay(editable, dependent), fixed.copy(end = Minute(600))).status shouldBe ProposalStatus.OK
        }
        test("fixed edit can reposition a flexible prerequisite before it") {
            val prerequisite = neighbor.copy(start = Minute(660), end = Minute(690))
            val replacement = editable.copy(kind = Kind.FIXED, after = "b", start = Minute(600), end = Minute(630))
            val result = edit(editingDay(editable, prerequisite), replacement)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(replacement, prerequisite.copy(start = Minute(570), end = Minute(600)))
        }
        test("edit window failure retains user reason and dormant state remains idempotent") {
            val replacement = editable.copy(window = window("08:00", "08:15"))
            val result = edit(editingDay(editable), replacement, now = 540)
            result.status shouldBe ProposalStatus.OK
            result.day.blocks shouldBe listOf(replacement.copy(status = BlockStatus.MISSED))
            result.changes shouldBe listOf(Change("a", setOf(ChangeType.EDITED, ChangeType.MISSED), Reason.USER_EDIT))
            SchedulingEngine.propose(result.day, Command.RebuildDay, Minute(540)).changes shouldBe emptyList()
        }
        test("no-op edit has no change entry and undo restores every editable field") {
            val day = editingDay(editable, neighbor)
            edit(day, editable).changes shouldBe emptyList()
            val result = edit(day, editable.copy(title = "Changed", duration = 45, minDuration = 20, kind = Kind.OPTIONAL, priority = Priority.HIGH, after = "b", window = window("09:00", "20:00")))
            val undone = SchedulingEngine.propose(result.day, Command.Undo(result.previousDay), Minute(480))
            undone.status shouldBe ProposalStatus.OK
            undone.day shouldBe day
            undone.changes shouldBe listOf(Change("a", setOf(ChangeType.RESTORED), Reason.UNDO))
        }
    })
