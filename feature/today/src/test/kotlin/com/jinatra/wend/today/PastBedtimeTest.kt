package com.jinatra.wend.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.ProposalStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PastBedtimeTest :
    FunSpec({
        test("allowing past bedtime preserves the overrun and reconsiders its unplaced dependent") {
            val original = eveningDay()
            val controller = TodayController(original)
            val initial = controller.runningLate(30, EVENING_NOW).shouldBeInstanceOf<Outcome.NeedsApproval>()
            initial.proposal.status shouldBe ProposalStatus.INFEASIBLE
            initial.proposal.day.blocks.last().status shouldBe BlockStatus.UNPLACED

            val extended = controller.allowPastBedtime(initial).shouldBeInstanceOf<Outcome.NeedsApproval>()
            extended.settings.allowPastBedtime shouldBe true
            extended.proposal.status shouldBe ProposalStatus.OK
            extended.proposal.previousDay shouldBe original
            extended.proposal.day.blocks.first().end shouldBe Minute(1380)
            extended.proposal.day.blocks.last().start shouldBe Minute(1380)
            extended.proposal.day.blocks.last().end shouldBe Minute(1410)
            extended.proposal.day.blocks.last().status shouldBe BlockStatus.PLANNED
            controller.day shouldBe original
            controller.canUndo shouldBe false

            controller.apply(extended.proposal)
            controller.undo(EVENING_NOW)
            controller.day shouldBe original
        }

        test("a changed day cannot replay a stale request with a different bedtime setting") {
            val controller = TodayController(eveningDay())
            val request = controller.runningLate(30, EVENING_NOW).shouldBeInstanceOf<Outcome.NeedsApproval>()
            controller.markDone(controller.day.blocks.first().id)
            val changed = controller.day
            shouldThrow<IllegalStateException> { controller.allowPastBedtime(request) }
            controller.day shouldBe changed
        }

        test("the review sheet repeats the original request and applies the resulting proposal") {
            val dispatcher = StandardTestDispatcher()
            val store = ViewModelStore()
            Dispatchers.setMain(dispatcher)
            try {
                val controller = TodayController(eveningDay())
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(controller, MinuteClock { EVENING_NOW }, isSample = true) as T
                }
                val model = ViewModelProvider(store, factory)[TodayViewModel::class.java]
                model.runningLate(30)
                model.state.value.sheet.shouldBeInstanceOf<Sheet.Review>().proposal.status shouldBe ProposalStatus.INFEASIBLE

                model.allowPastBedtime()
                val review = model.state.value.sheet.shouldBeInstanceOf<Sheet.Review>()
                review.proposal.status shouldBe ProposalStatus.OK
                review.proposal.day.blocks.first().end shouldBe Minute(1380)
                review.proposal.day.blocks.last().end shouldBe Minute(1410)
                model.approve(review.proposal)
                controller.day shouldBe review.proposal.day
                model.state.value.canUndo shouldBe true
                model.undo()
                controller.day shouldBe eveningDay()
            } finally {
                store.clear()
                dispatcher.scheduler.runCurrent()
                Dispatchers.resetMain()
            }
        }
    })

private const val EVENING_NOW = 1335

private fun eveningDay(): Day {
    val running = Block(
        "00000000-0000-4000-8000-000000000001", "Current activity", Kind.FLEXIBLE,
        Minute(1320), Minute(1350), 30, 30, Priority.NORMAL, status = BlockStatus.IN_PROGRESS,
    )
    val next = Block(
        "00000000-0000-4000-8000-000000000002", "Next activity", Kind.FLEXIBLE,
        Minute(1350), Minute(1380), 30, 30, Priority.HIGH, after = running.id,
    )
    return Day(LocalDate.of(2026, 11, 16), Minute(420), Minute(1380), listOf(running, next))
}
