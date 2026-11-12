package com.jinatra.wend.today

import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.SchedulingEngine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

class TodayModelTest :
    FunSpec({
        val day = SampleDay.create(LocalDate.of(2026, 11, 13))
        val now = SampleDay.START_MINUTE

        test("the hero counts down the activity in progress and names what's next") {
            val model = buildTodayModel(day, now)
            val hero = model.hero.shouldBeInstanceOf<Hero.Current>()
            hero.block.title shouldBe "Deep work"
            hero.minutesLeft shouldBe 35
            model.next?.title shouldBe "Email and admin"
        }

        test("progress counts done, skipped and remaining blocks") {
            val model = buildTodayModel(day, now)
            model.done shouldBe 4
            model.skipped shouldBe 0
            model.left shouldBe 11
        }

        test("an activity past its end shows as running over") {
            buildTodayModel(day, 11 * 60 + 10).hero.shouldBeInstanceOf<Hero.RunningOver>().minutesOver shouldBe 10
        }

        test("with nothing in progress the hero counts down to the next activity") {
            val idle = day.copy(blocks = day.blocks.map { if (it.title == "Deep work") it.copy(status = BlockStatus.DONE) else it })
            val hero = buildTodayModel(idle, 10 * 60 + 50).hero.shouldBeInstanceOf<Hero.UpNext>()
            hero.block.title shouldBe "Email and admin"
            hero.minutesUntil shouldBe 10
        }

        test("a finished day shows how much got done") {
            val finished = day.copy(blocks = day.blocks.map { it.copy(status = BlockStatus.DONE) })
            buildTodayModel(finished, 23 * 60).hero shouldBe Hero.DayDone(15)
        }

        test("free gaps of at least fifteen minutes appear only from now on") {
            val gaps = buildTodayModel(day, now).sections.flatMap { it.items }.filterIsInstance<TimelineItem.Gap>()
            gaps.map { it.start to it.minutes } shouldContain ((11 * 60 + 45) to 45)
            gaps.all { it.start >= now } shouldBe true
            gaps.all { it.minutes >= MIN_GAP } shouldBe true
        }

        test("the timeline is grouped into morning, afternoon and evening, anchored by wake and bedtime") {
            val sections = buildTodayModel(day, now).sections
            sections.map { it.part } shouldContainExactly listOf(Part.MORNING, Part.AFTERNOON, Part.EVENING)
            sections.first().items.first() shouldBe TimelineItem.Anchor(AnchorKind.WAKE, 7 * 60)
            sections.last().items.last() shouldBe TimelineItem.Anchor(AnchorKind.BEDTIME, 23 * 60)
        }

        test("missed, dropped and unplaced blocks move off the timeline into their own list") {
            val dropped = day.copy(blocks = day.blocks.map { if (it.title == "Reading") it.copy(status = BlockStatus.DROPPED) else it })
            val model = buildTodayModel(dropped, now)
            model.offPlan.map { it.title } shouldContainExactly listOf("Reading")
            model.sections.flatMap { it.items }.filterIsInstance<TimelineItem.Row>().none { it.block.title == "Reading" } shouldBe true
        }

        test("proposal lines carry the effect, reason and the block that caused it") {
            val running = day.blocks.first { it.status == BlockStatus.IN_PROGRESS }
            val proposal = SchedulingEngine.propose(day, Command.Overrun(running.id, Minute(running.end.value + 60)), Minute(now))
            val lines = proposalLines(proposal).associateBy { it.title }
            lines.getValue("Deep work").effect shouldBe Effect.EXTENDED
            lines.getValue("Deep work").reason shouldBe Reason.OVERRUN
            val email = lines.getValue("Email and admin")
            email.reason shouldBe Reason.DISPLACED
            email.causeTitle shouldBe "Deep work"
            email.before?.start?.value shouldBe 11 * 60
        }
    })
