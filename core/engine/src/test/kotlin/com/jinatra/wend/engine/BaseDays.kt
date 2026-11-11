package com.jinatra.wend.engine

import java.time.LocalDate

internal fun minute(text: String): Minute {
    val (hour, minute) = text.split(":").map(String::toInt)
    return Minute(hour * 60 + minute)
}

internal fun window(
    start: String,
    end: String,
) = Window(WindowBound.Absolute(minute(start)), WindowBound.Absolute(minute(end)))

internal fun bedtimeWindow(minutes: Int) = Window(WindowBound.BeforeBedtime(minutes), WindowBound.BeforeBedtime(0))

private fun block(
    id: String,
    title: String,
    kind: Kind,
    start: String,
    end: String,
    duration: Int,
    minimum: Int,
    priority: Priority? = null,
    window: Window? = null,
    after: String? = null,
) = Block(id, title, kind, minute(start), minute(end), duration, minimum, priority, window, after)

internal object BaseDays {
    val a =
        Day(
            LocalDate.of(2026, 11, 16),
            minute("07:00"),
            minute("23:00"),
            listOf(
                block("A1", "Morning routine", Kind.FLEXIBLE, "07:00", "07:30", 30, 15, Priority.NORMAL, window("07:00", "08:15")),
                block("A2", "Breakfast", Kind.FLEXIBLE, "07:30", "07:50", 20, 10, Priority.HIGH, window("07:00", "08:15"), "A1"),
                block("A3", "Commute to work", Kind.FIXED, "08:15", "09:00", 45, 45),
                block("A4", "Work", Kind.FIXED, "09:00", "18:00", 540, 540),
                block("A5", "Commute home", Kind.FIXED, "18:00", "18:45", 45, 45),
                block("A6", "Gym", Kind.FLEXIBLE, "19:00", "20:00", 60, 40, Priority.NORMAL, window("18:45", "21:30")),
                block("A7", "Dinner", Kind.FLEXIBLE, "20:15", "20:45", 30, 20, Priority.HIGH, window("19:00", "22:00")),
                block("A8", "Reading", Kind.OPTIONAL, "21:00", "21:30", 30, 15, Priority.LOW),
                block("A9", "Plan tomorrow", Kind.FLEXIBLE, "22:30", "22:40", 10, 5, Priority.LOW),
                block("A10", "Wind down", Kind.FLEXIBLE, "22:45", "23:00", 15, 10, Priority.NORMAL, bedtimeWindow(60)),
            ),
        )
    val b =
        Day(
            LocalDate.of(2026, 11, 17),
            minute("07:00"),
            minute("23:00"),
            listOf(
                block("B1", "Evening class", Kind.FIXED, "18:00", "19:30", 90, 90),
                block("B2", "Dinner", Kind.FLEXIBLE, "19:30", "20:00", 30, 20, Priority.HIGH, window("19:00", "21:30")),
                block("B3", "Study", Kind.FLEXIBLE, "20:00", "21:30", 90, 60, Priority.HIGH),
                block("B4", "Series", Kind.OPTIONAL, "21:30", "22:15", 45, 30, Priority.LOW),
                block("B5", "Call family", Kind.FLEXIBLE, "22:15", "22:35", 20, 15, Priority.NORMAL),
                block("B6", "Wind down", Kind.FLEXIBLE, "22:35", "23:00", 25, 15, Priority.NORMAL, bedtimeWindow(60)),
            ),
        )
    val c =
        Day(
            LocalDate.of(2026, 11, 21),
            minute("08:00"),
            minute("23:00"),
            listOf(
                block("C1", "Laundry", Kind.FLEXIBLE, "09:00", "09:45", 45, 30, Priority.NORMAL),
                block("C2", "Gym", Kind.FLEXIBLE, "10:00", "11:00", 60, 45, Priority.HIGH, window("08:00", "13:00")),
                block("C3", "Shower", Kind.FLEXIBLE, "11:00", "11:20", 20, 15, Priority.HIGH, after = "C2"),
                block("C4", "Lunch with friends", Kind.FIXED, "13:00", "14:30", 90, 90),
                block("C5", "Groceries", Kind.OPTIONAL, "11:30", "12:15", 45, 30, Priority.LOW, window("09:00", "20:00")),
            ),
        )
}

internal fun Day.edit(
    id: String,
    transform: (Block) -> Block,
): Day = copy(blocks = blocks.map { if (it.id == id) transform(it) else it })

internal fun Day.done(vararg ids: String): Day = copy(blocks = blocks.map { if (it.id in ids) it.copy(status = BlockStatus.DONE) else it })

internal fun Day.inProgress(id: String): Day = edit(id) { it.copy(status = BlockStatus.IN_PROGRESS) }

internal fun Day.at(
    id: String,
    start: String,
    end: String,
): Day = edit(id) { it.copy(start = minute(start), end = minute(end)) }

internal fun Day.status(
    id: String,
    status: BlockStatus,
): Day = edit(id) { it.copy(status = status) }

internal fun Day.add(block: Block): Day = copy(blocks = blocks + block)
