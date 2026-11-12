package com.jinatra.wend.today

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind

/** What the large numeral at the top of Today says. */
sealed interface Hero {
    data class Current(val block: Block, val minutesLeft: Int) : Hero

    data class RunningOver(val block: Block, val minutesOver: Int) : Hero

    data class UpNext(val block: Block, val minutesUntil: Int) : Hero

    data class DayDone(val doneCount: Int) : Hero
}

enum class Part { MORNING, AFTERNOON, EVENING }

sealed interface TimelineItem {
    data class Anchor(val kind: AnchorKind, val time: Int) : TimelineItem

    data class Row(val block: Block, val isNow: Boolean) : TimelineItem

    data class Gap(val start: Int, val minutes: Int) : TimelineItem
}

enum class AnchorKind { WAKE, BEDTIME }

data class TimelineSection(val part: Part, val items: List<TimelineItem>)

data class TodayModel(
    val hero: Hero,
    val next: Block?,
    val done: Int,
    val skipped: Int,
    val left: Int,
    val sections: List<TimelineSection>,
    val offPlan: List<Block>,
)

/** Shortest gap worth showing as free time. */
const val MIN_GAP = 15

private const val NOON = 12 * 60
private const val EVENING = 17 * 60

private val onPlan = setOf(BlockStatus.PLANNED, BlockStatus.IN_PROGRESS, BlockStatus.DONE, BlockStatus.SKIPPED)
private val takesTime = setOf(BlockStatus.PLANNED, BlockStatus.IN_PROGRESS, BlockStatus.DONE)

fun partOf(minute: Int): Part = when {
    minute < NOON -> Part.MORNING
    minute < EVENING -> Part.AFTERNOON
    else -> Part.EVENING
}

fun buildTodayModel(day: Day, now: Int): TodayModel {
    val sorted = day.blocks.sortedWith(compareBy({ it.start.value }, { it.id }))
    val upcoming = sorted.filter { it.status == BlockStatus.PLANNED && it.end.value > now }
    val running = sorted.firstOrNull { it.status == BlockStatus.IN_PROGRESS }
    val current = running ?: upcoming.firstOrNull { it.start.value <= now }
    val next = upcoming.firstOrNull { it != current }

    val doneCount = day.blocks.count { it.status == BlockStatus.DONE }
    val hero = when {
        current != null && now > current.end.value -> Hero.RunningOver(current, now - current.end.value)
        current != null -> Hero.Current(current, current.end.value - now)
        next != null -> Hero.UpNext(next, (next.start.value - now).coerceAtLeast(0))
        else -> Hero.DayDone(doneCount)
    }

    val items = mutableListOf<TimelineItem>(TimelineItem.Anchor(AnchorKind.WAKE, day.wake.value))
    var cursor = day.wake.value
    for (block in sorted.filter { it.status in onPlan }) {
        if (block.status in takesTime) {
            val gapStart = maxOf(cursor, now)
            if (block.start.value - gapStart >= MIN_GAP) items += TimelineItem.Gap(gapStart, block.start.value - gapStart)
            cursor = maxOf(cursor, block.end.value)
        }
        items += TimelineItem.Row(block, block == current)
    }
    val tailStart = maxOf(cursor, now)
    if (day.bedtime.value - tailStart >= MIN_GAP) items += TimelineItem.Gap(tailStart, day.bedtime.value - tailStart)
    items += TimelineItem.Anchor(AnchorKind.BEDTIME, day.bedtime.value)

    val sections = items.groupBy { partOf(it.time) }.toSortedMap().map { (part, list) -> TimelineSection(part, list) }

    return TodayModel(
        hero = hero,
        next = if (hero is Hero.UpNext) upcoming.firstOrNull { it != next && it != current } else next,
        done = doneCount,
        skipped = day.blocks.count { it.status == BlockStatus.SKIPPED },
        left = day.blocks.count { it.status == BlockStatus.PLANNED || it.status == BlockStatus.IN_PROGRESS },
        sections = sections,
        offPlan = sorted.filter { it.status !in onPlan },
    )
}

private val TimelineItem.time: Int get() = when (this) {
    is TimelineItem.Anchor -> time
    is TimelineItem.Row -> block.start.value
    is TimelineItem.Gap -> start
}

val Block.kindLabelNeeded: Boolean get() = kind != Kind.FLEXIBLE
