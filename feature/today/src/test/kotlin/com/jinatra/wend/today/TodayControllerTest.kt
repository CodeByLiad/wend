package com.jinatra.wend.today

import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.ErrorCode
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.SchedulingEngine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

class TodayControllerTest :
    FunSpec({
        val date = LocalDate.of(2026, 11, 13)
        val now = SampleDay.START_MINUTE
        fun sample() = SampleDay.create(date)
        fun Day.byTitle(title: String) = blocks.first { it.title == title }
        fun notStarted(): Day = sample().let { day -> day.copy(blocks = day.blocks.map { it.copy(status = BlockStatus.PLANNED) }) }

        test("the sample day is valid and already settled at the prototype's start time") {
            val proposal = SchedulingEngine.propose(sample(), Command.RebuildDay, Minute(now))
            proposal.status shouldBe ProposalStatus.OK
            proposal.changes shouldHaveSize 0
        }

        test("running late during an activity proposes an overrun and waits for approval") {
            val controller = TodayController(sample())
            val outcome = controller.runningLate(30, now).shouldBeInstanceOf<Outcome.NeedsApproval>()
            val deepWork = outcome.proposal.day.byTitle("Deep work")
            deepWork.end.value shouldBe 11 * 60 + 30
            outcome.proposal.changes.first { it.blockId == deepWork.id }.reason shouldBe Reason.OVERRUN
            controller.day shouldBe sample()
        }

        test("running late before the day starts is a late wake-up") {
            val controller = TodayController(notStarted())
            val outcome = controller.runningLate(15, 7 * 60).shouldBeInstanceOf<Outcome.NeedsApproval>()
            outcome.proposal.day.wake.value shouldBe 7 * 60 + 15
            outcome.proposal.changes.map { it.reason } shouldContain Reason.LATE_WAKE
        }

        test("running late between activities rebuilds from the delayed time") {
            val day = sample().let { d -> d.copy(blocks = d.blocks.map { if (it.title == "Deep work") it.copy(status = BlockStatus.DONE) else it }) }
            val controller = TodayController(day)
            val outcome = controller.runningLate(30, 11 * 60).shouldBeInstanceOf<Outcome.NeedsApproval>()
            outcome.proposal.day.byTitle("Email and admin").start.value shouldBe 11 * 60 + 30
        }

        test("an approved proposal is logged and undo restores the exact previous day") {
            val controller = TodayController(sample())
            val outcome = controller.runningLate(60, now).shouldBeInstanceOf<Outcome.NeedsApproval>()
            controller.apply(outcome.proposal)
            controller.day shouldBe outcome.proposal.day
            controller.changeLog.single().source shouldBe ChangeSource.ENGINE
            controller.changeLog.single().before shouldBe sample()

            controller.undo(now).shouldBeInstanceOf<Outcome.Applied>().kind shouldBe AppliedKind.UNDONE
            controller.day shouldBe sample()
            controller.canUndo shouldBe false
        }

        test("a stale proposal cannot be applied") {
            val controller = TodayController(sample())
            val outcome = controller.runningLate(30, now).shouldBeInstanceOf<Outcome.NeedsApproval>()
            controller.markDone(sample().byTitle("Email and admin").id)
            runCatching { controller.apply(outcome.proposal) }.isFailure shouldBe true
        }

        test("marking done changes only the status, is logged and can be undone") {
            val controller = TodayController(sample())
            val id = sample().byTitle("Deep work").id
            controller.markDone(id).shouldBeInstanceOf<Outcome.Applied>().blockTitle shouldBe "Deep work"
            controller.day.byTitle("Deep work").status shouldBe BlockStatus.DONE
            controller.day.byTitle("Deep work").start shouldBe sample().byTitle("Deep work").start
            controller.changeLog.single().source shouldBe ChangeSource.USER
            controller.undo(now)
            controller.day shouldBe sample()
        }

        test("a skip with no knock-on effects applies without a review") {
            val controller = TodayController(sample())
            val gym = sample().byTitle("Gym")
            controller.skip(gym.id, now).shouldBeInstanceOf<Outcome.Applied>().kind shouldBe AppliedKind.SKIPPED
            controller.day.byTitle("Gym").status shouldBe BlockStatus.SKIPPED
        }

        test("only one activity can be in progress") {
            val controller = TodayController(sample())
            controller.start(sample().byTitle("Email and admin").id).shouldBeInstanceOf<Outcome.Rejected>().code shouldBe ErrorCode.INVALID_TARGET
        }

        test("moving onto a fixed plan is rejected with the engine's reason") {
            val controller = TodayController(sample())
            val outcome = controller.moveLater(sample().byTitle("Email and admin").id, 90, now)
            outcome.shouldBeInstanceOf<Outcome.Rejected>().code shouldBe ErrorCode.OVERLAPS_LOCKED
            controller.day shouldBe sample()
        }

        test("a rebuild with nothing to change says so") {
            TodayController(sample()).rebuild(now) shouldBe Outcome.NothingToChange
        }
    })
