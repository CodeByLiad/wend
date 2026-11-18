package com.jinatra.wend.data

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.ValidationCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest :
    FunSpec({
        val monday = LocalDate.of(2026, 11, 16)
        test("daily and weekday rules match actual calendar dates") {
            (0L..20L).forEach { Recurrence.Daily.matches(monday.plusDays(it)) shouldBe true }
            val rule = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            (0L..20L).forEach { offset -> rule.matches(monday.plusDays(offset)) shouldBe (offset % 7 in setOf(0L, 4L)) }
        }
        test("every N days is anchored and never matches before its anchor") {
            val rule = Recurrence.EveryNDays(3, monday)
            (-10L..30L).forEach { rule.matches(monday.plusDays(it)) shouldBe (it >= 0 && it % 3 == 0L) }
        }
        test("monthly day 31 skips shorter months and leap dates behave normally") {
            val rule = Recurrence.Monthly(31)
            rule.matches(LocalDate.of(2026, 1, 31)) shouldBe true
            rule.matches(LocalDate.of(2026, 2, 28)) shouldBe false
            rule.matches(LocalDate.of(2026, 4, 30)) shouldBe false
            rule.matches(LocalDate.of(2026, 5, 31)) shouldBe true
            Recurrence.Monthly(29).matches(LocalDate.of(2028, 2, 29)) shouldBe true
        }
        test("invalid recurrence parameters fail clearly") {
            shouldThrow<IllegalArgumentException> { Recurrence.EveryNDays(0, monday) }
            shouldThrow<IllegalArgumentException> { Recurrence.EveryNDays(-1, monday) }
            shouldThrow<IllegalArgumentException> { Recurrence.Weekdays(emptySet()) }
            shouldThrow<IllegalArgumentException> { Recurrence.Monthly(0) }
            shouldThrow<IllegalArgumentException> { Recurrence.Monthly(32) }
        }
        val first = "00000000-0000-4000-8000-000000000001"
        val second = "00000000-0000-4000-8000-000000000002"
        val block = Block(first, "Task", Kind.FLEXIBLE, Minute(540), Minute(570), 30, 15, Priority.NORMAL)
        val routine = RoutineDraft("00000000-0000-4000-8000-000000000003", "Workday", "workday", Recurrence.Daily, listOf(ActivityTemplate(block, "focus", "work")))
        test("templates validate UUIDs display fields statuses and complete dependency graphs") {
            validateRoutine(routine) shouldBe null
            validateRoutine(routine.copy(name = " "))!!.fields shouldBe setOf(RoutineFieldError.BLANK_NAME)
            validateRoutine(routine.copy(id = "not-uuid"))!!.fields shouldBe setOf(RoutineFieldError.INVALID_UUID)
            val dependent = block.copy(id = second, after = first)
            validateRoutine(routine.copy(activities = routine.activities + ActivityTemplate(dependent, "care", "shower"))) shouldBe null
            val cycle = listOf(ActivityTemplate(block.copy(after = second), "focus", "work"), ActivityTemplate(dependent, "care", "shower"))
            validateRoutine(routine.copy(activities = cycle))!!.engineErrors.any { it.subCode == ValidationCode.DEPENDENCY_CYCLE } shouldBe true
        }
        test("materialization remaps dependencies to distinct dated instance ids without altering templates") {
            val template = routine.copy(activities = routine.activities + ActivityTemplate(block.copy(id = second, after = first), "care", "shower"))
            val day = materialize(template, monday, Minute(420), Minute(1380), mapOf(first to "instance-one", second to "instance-two"))
            day.blocks.map { it.id } shouldBe listOf("instance-one", "instance-two")
            day.blocks[1].after shouldBe "instance-one"
            template.activities[1].block.after shouldBe first
        }
    })
