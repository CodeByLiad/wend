package com.jinatra.wend.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun change(
    id: String,
    reason: Reason,
    vararg types: ChangeType,
    cause: String? = null,
) = Change(id, types.toSet(), reason, cause)

private val morningDone = arrayOf("A1", "A2", "A3", "A4", "A5")
private val throughDinner = morningDone + arrayOf("A6", "A7")

private fun overrunGiven() = BaseDays.a.done(*morningDone).inProgress("A6")

private fun readingGiven() = BaseDays.a.done(*throughDinner, "A9").at("A9", "21:40", "21:50")

/** Full Day equality checks every field of every block, including unchanged blocks. */
private fun verify(
    input: Day,
    command: Command,
    now: String,
    expected: Day = input,
    changes: List<Change> = emptyList(),
    status: ProposalStatus = ProposalStatus.OK,
    settings: Settings = Settings(),
    conflicts: List<String> = emptyList(),
    errors: List<ValidationError> = emptyList(),
): Proposal {
    val result = SchedulingEngine.propose(input, command, minute(now), settings)
    result.status shouldBe status
    result.previousDay shouldBe input
    result.day shouldBe expected
    result.changes.sortedBy { it.blockId } shouldBe changes.sortedBy { it.blockId }
    result.conflicts shouldBe conflicts
    result.errors shouldBe errors
    return result
}

class ExamplesTest :
    FunSpec({
        test("example01_noOpRebuild") { verify(BaseDays.a, Command.RebuildDay, "06:30") }
        test("example02_rebuildOwnOutput") {
            val day =
                BaseDays.a
                    .copy(wake = minute("07:20"))
                    .at("A1", "07:20", "07:50")
                    .at("A2", "07:50", "08:10")
            verify(day, Command.RebuildDay, "07:20")
        }
        test("example03_lateWakeTwentyMinutes") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("07:20")),
                "07:20",
                BaseDays.a
                    .copy(wake = minute("07:20"))
                    .at("A1", "07:20", "07:50")
                    .at("A2", "07:50", "08:10"),
                listOf(change("A1", Reason.LATE_WAKE, ChangeType.MOVED), change("A2", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED)),
            )
        }
        test("example04_shortenLowerPriorityDependency") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("07:35")),
                "07:35",
                BaseDays.a
                    .copy(wake = minute("07:35"))
                    .at("A1", "07:35", "07:55")
                    .at("A2", "07:55", "08:15"),
                listOf(
                    change("A1", Reason.DEPENDENCY_SHRINK, ChangeType.MOVED, ChangeType.SHRUNK),
                    change("A2", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED),
                ),
            )
        }
        test("example05_bothAtMinimum") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("07:50")),
                "07:50",
                BaseDays.a
                    .copy(wake = minute("07:50"))
                    .at("A1", "07:50", "08:05")
                    .at("A2", "08:05", "08:15"),
                listOf(
                    change("A1", Reason.DEPENDENCY_SHRINK, ChangeType.MOVED, ChangeType.SHRUNK),
                    change("A2", Reason.SHRUNK_TO_FIT, ChangeType.MOVED, ChangeType.SHRUNK),
                ),
            )
        }
        test("example06_breakfastUnplaced") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("08:00")),
                "08:00",
                BaseDays.a
                    .copy(wake = minute("08:00"))
                    .at("A1", "08:00", "08:15")
                    .status("A2", BlockStatus.UNPLACED),
                listOf(
                    change("A1", Reason.SHRUNK_TO_FIT, ChangeType.MOVED, ChangeType.SHRUNK),
                    change("A2", Reason.NO_ROOM, ChangeType.UNPLACED),
                ),
                ProposalStatus.INFEASIBLE,
            )
        }
        test("example07_windowPassed") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("08:05")),
                "08:05",
                BaseDays.a
                    .copy(wake = minute("08:05"))
                    .status("A1", BlockStatus.MISSED)
                    .at("A2", "08:05", "08:15"),
                listOf(
                    change("A1", Reason.WINDOW_PASSED, ChangeType.MISSED),
                    change("A2", Reason.SHRUNK_TO_FIT, ChangeType.MOVED, ChangeType.SHRUNK),
                ),
            )
        }
        test("example08_fixedExpired") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("09:30")),
                "09:30",
                BaseDays.a
                    .copy(
                        wake = minute("09:30"),
                    ).status("A1", BlockStatus.MISSED)
                    .status("A2", BlockStatus.MISSED)
                    .status("A3", BlockStatus.MISSED),
                listOf(
                    change("A1", Reason.WINDOW_PASSED, ChangeType.MISSED),
                    change("A2", Reason.WINDOW_PASSED, ChangeType.MISSED),
                    change("A3", Reason.FIXED_EXPIRED, ChangeType.MISSED),
                ),
            )
        }
        test("example09_roundHorizonUp") {
            verify(
                BaseDays.a,
                Command.LateWake(minute("07:23")),
                "07:23",
                BaseDays.a
                    .copy(wake = minute("07:23"))
                    .at("A1", "07:25", "07:55")
                    .at("A2", "07:55", "08:15"),
                listOf(change("A1", Reason.LATE_WAKE, ChangeType.MOVED), change("A2", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED)),
            )
        }
        test("example10_gymOverrun") {
            val input = overrunGiven()
            verify(
                input,
                Command.Overrun("A6", minute("20:25")),
                "20:00",
                input.at("A6", "19:00", "20:25").at("A7", "20:25", "20:55"),
                listOf(change("A6", Reason.OVERRUN, ChangeType.EXTENDED), change("A7", Reason.DISPLACED, ChangeType.MOVED, cause = "A6")),
            )
        }
        test("example11_longGymOverrun") {
            val input = overrunGiven()
            verify(
                input,
                Command.Overrun("A6", minute("21:20")),
                "20:00",
                input.at("A6", "19:00", "21:20").at("A7", "21:20", "21:50").at("A8", "21:50", "22:20"),
                listOf(
                    change("A6", Reason.OVERRUN, ChangeType.EXTENDED),
                    change("A7", Reason.DISPLACED, ChangeType.MOVED, cause = "A6"),
                    change("A8", Reason.DISPLACED, ChangeType.MOVED, cause = "A6"),
                ),
            )
        }
        test("example12_dropSeries") {
            val input = BaseDays.b.inProgress("B1")
            verify(
                input,
                Command.Overrun("B1", minute("20:00")),
                "19:30",
                input
                    .at("B1", "18:00", "20:00")
                    .at("B2", "20:00", "20:30")
                    .at("B3", "20:30", "22:00")
                    .status("B4", BlockStatus.DROPPED),
                listOf(
                    change("B1", Reason.OVERRUN, ChangeType.EXTENDED),
                    change("B2", Reason.DISPLACED, ChangeType.MOVED, cause = "B1"),
                    change("B3", Reason.DISPLACED, ChangeType.MOVED, cause = "B2"),
                    change("B4", Reason.DROPPED_TO_FIT, ChangeType.DROPPED),
                ),
            )
        }
        test("example13_highPriorityWins") {
            val input = BaseDays.b.inProgress("B1")
            verify(
                input,
                Command.Overrun("B1", minute("21:00")),
                "20:30",
                input
                    .at(
                        "B1",
                        "18:00",
                        "21:00",
                    ).at(
                        "B2",
                        "21:00",
                        "21:30",
                    ).at(
                        "B3",
                        "21:30",
                        "23:00",
                    ).status("B4", BlockStatus.DROPPED)
                    .status("B5", BlockStatus.UNPLACED)
                    .status("B6", BlockStatus.UNPLACED),
                listOf(
                    change("B1", Reason.OVERRUN, ChangeType.EXTENDED),
                    change("B2", Reason.DISPLACED, ChangeType.MOVED, cause = "B1"),
                    change("B3", Reason.DISPLACED, ChangeType.MOVED, cause = "B1"),
                    change("B4", Reason.DROPPED_TO_FIT, ChangeType.DROPPED),
                    change("B5", Reason.NO_ROOM, ChangeType.UNPLACED),
                    change("B6", Reason.NO_ROOM, ChangeType.UNPLACED),
                ),
                ProposalStatus.INFEASIBLE,
            )
        }
        test("example14_shrinkBeforeBedtime") {
            val input = readingGiven()
            verify(
                input,
                Command.RebuildDay,
                "22:30",
                input.at("A8", "22:30", "22:45"),
                listOf(change("A8", Reason.SHRUNK_TO_FIT, ChangeType.MOVED, ChangeType.SHRUNK)),
            )
        }
        test("example15_allowPastBedtime") {
            val input = readingGiven()
            verify(
                input,
                Command.RebuildDay,
                "22:30",
                input.at("A8", "23:00", "23:30"),
                listOf(change("A8", Reason.PAST_BEDTIME, ChangeType.MOVED)),
                settings = Settings(true),
            )
        }
        test("example16_earlierBedtime") {
            val input = BaseDays.a.done(*morningDone)
            verify(
                input,
                Command.SetBedtime(minute("22:00")),
                "18:50",
                input.copy(bedtime = minute("22:00")).at("A10", "21:45", "22:00").at("A9", "21:35", "21:45"),
                listOf(change("A10", Reason.BEDTIME_CHANGED, ChangeType.MOVED), change("A9", Reason.BEDTIME_CHANGED, ChangeType.MOVED)),
            )
        }
        test("example17_retryReading") {
            val input = BaseDays.a.done(*throughDinner)
            verify(
                input,
                Command.RetryBlock("A8"),
                "21:40",
                input.at("A8", "21:40", "22:10"),
                listOf(change("A8", Reason.RETRIED, ChangeType.MOVED)),
            )
        }
        test("example18_pinGym") {
            verify(
                BaseDays.c,
                Command.MoveBlock("C2", minute("11:30")),
                "08:30",
                BaseDays.c
                    .at("C2", "11:30", "12:30")
                    .at("C3", "12:30", "12:50")
                    .at("C5", "14:30", "15:15"),
                listOf(
                    change("C2", Reason.USER_MOVE, ChangeType.MOVED),
                    change("C3", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED),
                    change("C5", Reason.DISPLACED, ChangeType.MOVED, cause = "C2"),
                ),
            )
        }
        test("example19_skipWithoutRebuild") {
            verify(
                BaseDays.c,
                Command.SkipBlock("C2"),
                "09:50",
                BaseDays.c.status("C2", BlockStatus.SKIPPED),
                listOf(change("C2", Reason.USER_SKIP, ChangeType.SKIPPED)),
            )
        }
        test("example20_showerAfterOverrun") {
            val input = BaseDays.c.done("C1").inProgress("C2")
            verify(
                input,
                Command.Overrun("C2", minute("11:30")),
                "11:00",
                input.at("C2", "10:00", "11:30").at("C3", "11:30", "11:50").at("C5", "11:50", "12:35"),
                listOf(
                    change("C2", Reason.OVERRUN, ChangeType.EXTENDED),
                    change("C3", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED),
                    change("C5", Reason.DISPLACED, ChangeType.MOVED, cause = "C3"),
                ),
            )
        }
        test("example21_showerAfterLunch") {
            val input = BaseDays.c.done("C1").inProgress("C2")
            verify(
                input,
                Command.Overrun("C2", minute("12:50")),
                "12:00",
                input.at("C2", "10:00", "12:50").at("C3", "14:30", "14:50").at("C5", "14:50", "15:35"),
                listOf(
                    change("C2", Reason.OVERRUN, ChangeType.EXTENDED),
                    change("C3", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED),
                    change("C5", Reason.DISPLACED, ChangeType.MOVED, cause = "C2"),
                ),
            )
        }
        test("example22_addDoctor") {
            val doctor = Block("D1", "Doctor", Kind.FIXED, minute("10:00"), minute("12:30"), 150, 150)
            verify(
                BaseDays.c,
                Command.AddBlock(doctor),
                "08:30",
                BaseDays.c
                    .add(
                        doctor,
                    ).at("C2", "09:00", "10:00")
                    .at("C3", "12:30", "12:50")
                    .at("C1", "14:30", "15:15")
                    .at("C5", "15:15", "16:00"),
                listOf(
                    change("D1", Reason.USER_ADD, ChangeType.ADDED),
                    change("C2", Reason.DISPLACED, ChangeType.MOVED, cause = "D1"),
                    change("C3", Reason.FOLLOWS_DEPENDENCY, ChangeType.MOVED),
                    change("C1", Reason.DISPLACED, ChangeType.MOVED, cause = "C2"),
                    change("C5", Reason.DISPLACED, ChangeType.MOVED, cause = "D1"),
                ),
            )
        }
        test("example23_missedGroceries") {
            val input = BaseDays.c.done("C1", "C2", "C3", "C4")
            verify(
                input,
                Command.RebuildDay,
                "19:50",
                input.status("C5", BlockStatus.MISSED),
                listOf(change("C5", Reason.WINDOW_PASSED, ChangeType.MISSED)),
            )
        }
        test("example24_addWithoutMovingOthers") {
            val input = BaseDays.a.done("A1", "A2", "A3")
            val call = Block("N1", "Call mom", Kind.FLEXIBLE, minute("20:00"), minute("20:20"), 20, 15, Priority.NORMAL)
            verify(
                input,
                Command.AddBlock(call, minute("20:00")),
                "10:00",
                input.add(call.copy(start = minute("21:30"), end = minute("21:50"))),
                listOf(change("N1", Reason.USER_ADD, ChangeType.ADDED)),
            )
        }
        test("example25_addWithFallbackRebuild") {
            val input = BaseDays.b.inProgress("B1")
            val assignment = Block("N2", "Assignment", Kind.FLEXIBLE, minute("18:30"), minute("19:15"), 45, 30, Priority.NORMAL)
            verify(
                input,
                Command.AddBlock(assignment),
                "18:30",
                input.add(assignment.copy(start = minute("21:30"), end = minute("22:15"))).status("B4", BlockStatus.DROPPED),
                listOf(change("N2", Reason.USER_ADD, ChangeType.ADDED), change("B4", Reason.DROPPED_TO_FIT, ChangeType.DROPPED)),
            )
        }
        test("example26_rejectOverlapWithWork") {
            verify(
                BaseDays.a,
                Command.MoveBlock("A8", minute("12:00")),
                "10:00",
                status = ProposalStatus.REJECTED,
                errors = listOf(ValidationError(ErrorCode.OVERLAPS_LOCKED, blockId = "A4")),
            )
        }
        test("example27_conflictPreservesDirectEdit") {
            val input =
                BaseDays.a
                    .done("A1", "A2", "A3", "A4")
                    .inProgress("A5")
                    .edit("A6") { it.copy(kind = Kind.FIXED) }
            verify(
                input,
                Command.Overrun("A5", minute("19:20")),
                "18:50",
                input.at("A5", "18:00", "19:20"),
                listOf(change("A5", Reason.OVERRUN, ChangeType.EXTENDED)),
                ProposalStatus.CONFLICT,
                conflicts = listOf("A5", "A6"),
            )
        }
        test("example28_undoExactPreviousDay") {
            val input = overrunGiven()
            val applied = SchedulingEngine.propose(input, Command.Overrun("A6", minute("21:20")), minute("20:00"))
            verify(
                applied.day,
                Command.Undo(applied.previousDay),
                "20:00",
                input,
                listOf(
                    change("A6", Reason.UNDO, ChangeType.RESTORED),
                    change("A7", Reason.UNDO, ChangeType.RESTORED),
                    change("A8", Reason.UNDO, ChangeType.RESTORED),
                ),
            )
        }
        test("example29_inProgressLocked") { verify(overrunGiven(), Command.RebuildDay, "19:30") }
        test(
            "example30_afterBedtime",
        ) { verify(BaseDays.a.done(*throughDinner), Command.RebuildDay, "23:10", status = ProposalStatus.NO_HORIZON) }
    })
