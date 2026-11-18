package com.jinatra.wend.data

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private fun base() = Day(
    LocalDate.of(2026, 11, 18),
    Minute(420),
    Minute(1380),
    listOf(
        Block("00000000-0000-4000-8000-000000000001", "Synthetic activity", Kind.FLEXIBLE, Minute(420), Minute(480), 60, 30, Priority.NORMAL),
    ),
)

class StatusBoundaryTest :
    FunSpec({
        test("Start changes only its target status and retains every scheduling field") {
            val day = base()
            statusDay(day, day.blocks.single().id, StatusAction.START) shouldBe day.copy(blocks = listOf(day.blocks.single().copy(status = BlockStatus.IN_PROGRESS)))
        }
        test("Done accepts planned or running activities without moving or shortening them") {
            for (status in listOf(BlockStatus.PLANNED, BlockStatus.IN_PROGRESS)) {
                val day = base().let { it.copy(blocks = listOf(it.blocks.single().copy(status = status))) }
                statusDay(day, day.blocks.single().id, StatusAction.DONE) shouldBe day.copy(blocks = listOf(day.blocks.single().copy(status = BlockStatus.DONE)))
            }
        }
        test("dormant and skipped activities require engine retry rather than status shortcuts") {
            for (status in listOf(BlockStatus.SKIPPED, BlockStatus.MISSED, BlockStatus.DROPPED, BlockStatus.UNPLACED, BlockStatus.DONE)) {
                val day = base().let { it.copy(blocks = listOf(it.blocks.single().copy(status = status))) }
                statusDay(day, day.blocks.single().id, StatusAction.START) shouldBe null
                statusDay(day, day.blocks.single().id, StatusAction.DONE) shouldBe null
            }
        }
        test("a second in-progress activity and unknown targets are rejected") {
            val day = base()
            val busy = day.copy(blocks = day.blocks + day.blocks.single().copy(id = "00000000-0000-4000-8000-000000000002", status = BlockStatus.IN_PROGRESS))
            statusDay(busy, day.blocks.single().id, StatusAction.START) shouldBe null
            statusDay(day, "unknown", StatusAction.DONE) shouldBe null
        }
        test("settings reject inconsistent sleep bounds, repeat settings and duplicate cards") {
            shouldThrow<IllegalArgumentException> { UserSettings(wake = 1380, bedtime = 420) }
            shouldThrow<IllegalArgumentException> { UserSettings(bedtime = 1381) }
            shouldThrow<IllegalArgumentException> { UserSettings(reminderRepeats = -1) }
            shouldThrow<IllegalArgumentException> { UserSettings(todayCards = listOf(TodayCard.TIMELINE, TodayCard.TIMELINE)) }
            UserSettings().autoApply shouldBe false
            UserSettings().accountability shouldBe Accountability.BALANCED
        }
    })
