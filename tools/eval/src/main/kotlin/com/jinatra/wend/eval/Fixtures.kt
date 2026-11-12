package com.jinatra.wend.eval

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.SchedulingEngine
import com.jinatra.wend.engine.Settings
import com.jinatra.wend.engine.Window
import com.jinatra.wend.engine.WindowBound
import java.time.LocalDate

object Fixtures {
    fun id(alias: String): String {
        val prefix = when (alias.first()) {
            'A' -> "a0000000"
            'B' -> "b0000000"
            'C' -> "c0000000"
            else -> error("Unknown fixture id")
        }
        return "$prefix-0000-4000-8000-${alias.drop(1).padStart(12, '0')}"
    }
    private fun time(value: String): Minute = value.split(":").map(String::toInt).let { Minute(it[0] * 60 + it[1]) }
    private fun window(start: String, end: String) = Window(WindowBound.Absolute(time(start)), WindowBound.Absolute(time(end)))
    private fun block(
        alias: String,
        title: String,
        kind: Kind,
        start: String,
        end: String,
        duration: Int,
        minimum: Int,
        priority: Priority? = null,
        window: Window? = null,
        after: String? = null,
    ) = Block(id(alias), title, kind, time(start), time(end), duration, minimum, priority, window, after?.let(::id))

    private val office = Day(
        LocalDate.of(2026, 11, 16),
        time("07:00"),
        time("23:00"),
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
            block("A10", "Wind down", Kind.FLEXIBLE, "22:45", "23:00", 15, 10, Priority.NORMAL, Window(WindowBound.BeforeBedtime(60), WindowBound.BeforeBedtime(0))),
        ),
    )
    private val student = Day(
        LocalDate.of(2026, 11, 17),
        time("07:00"),
        time("23:00"),
        listOf(
            block("B1", "Evening class", Kind.FIXED, "18:00", "19:30", 90, 90),
            block("B2", "Dinner", Kind.FLEXIBLE, "19:30", "20:00", 30, 20, Priority.HIGH, window("19:00", "21:30")),
            block("B3", "Study", Kind.FLEXIBLE, "20:00", "21:30", 90, 60, Priority.HIGH),
            block("B4", "Series", Kind.OPTIONAL, "21:30", "22:15", 45, 30, Priority.LOW),
            block("B5", "Call family", Kind.FLEXIBLE, "22:15", "22:35", 20, 15, Priority.NORMAL),
            block("B6", "Wind down", Kind.FLEXIBLE, "22:35", "23:00", 25, 15, Priority.NORMAL, Window(WindowBound.BeforeBedtime(60), WindowBound.BeforeBedtime(0))),
        ),
    )
    private val saturday = Day(
        LocalDate.of(2026, 11, 21),
        time("08:00"),
        time("23:00"),
        listOf(
            block("C1", "Laundry", Kind.FLEXIBLE, "09:00", "09:45", 45, 30, Priority.NORMAL),
            block("C2", "Gym", Kind.FLEXIBLE, "10:00", "11:00", 60, 45, Priority.HIGH, window("08:00", "13:00")),
            block("C3", "Shower", Kind.FLEXIBLE, "11:00", "11:20", 20, 15, Priority.HIGH, after = "C2"),
            block("C4", "Lunch with friends", Kind.FIXED, "13:00", "14:30", 90, 90),
            block("C5", "Groceries", Kind.OPTIONAL, "11:30", "12:15", 45, 30, Priority.LOW, window("09:00", "20:00")),
        ),
    )
    private fun Day.doneThrough(last: Int): Day = copy(blocks = blocks.mapIndexed { index, block -> if (index < last) block.copy(status = BlockStatus.DONE) else block })
    private fun Day.progress(alias: String): Day = copy(blocks = blocks.map { if (it.id == id(alias)) it.copy(status = BlockStatus.IN_PROGRESS) else it })

    fun context(name: String, now: Int? = null, allowPastBedtime: Boolean = false, overrides: Map<String, String> = emptyMap()): EvaluationContext {
        val initial = when (name) {
            "office" -> EvaluationContext(office, time("06:30"))

            "office_evening" -> EvaluationContext(office.doneThrough(5), time("18:50"))

            "gym_running" -> EvaluationContext(office.doneThrough(5).progress("A6"), time("20:00"))

            "reading_late" -> EvaluationContext(office.doneThrough(7), time("21:40"))

            "office_undo" -> {
                val before = office.doneThrough(5).progress("A6")
                EvaluationContext(SchedulingEngine.propose(before, Command.Overrun(id("A6"), time("21:20")), time("20:00")).day, time("20:00"), previousDay = before)
            }

            "office_ambiguous" -> EvaluationContext(office.copy(blocks = office.blocks + block("A11", "Gym", Kind.FLEXIBLE, "21:30", "22:15", 45, 30, Priority.NORMAL)), time("06:30"))

            "breakfast_unplaced" -> {
                val late = SchedulingEngine.propose(office, Command.LateWake(time("08:00")), time("08:00")).day
                EvaluationContext(SchedulingEngine.propose(late, Command.SkipBlock(id("A1")), time("08:00")).day, time("08:00"))
            }

            "student" -> EvaluationContext(student, time("18:30"))

            "class_running" -> EvaluationContext(student.progress("B1"), time("19:30"))

            "saturday" -> EvaluationContext(saturday, time("08:30"))

            "saturday_gym_running" -> EvaluationContext(saturday.doneThrough(1).progress("C2"), time("11:00"))

            else -> error("Unknown synthetic fixture")
        }
        return initial.copy(
            day = initial.day.copy(
                blocks = initial.day.blocks.map { block ->
                    val status = overrides.entries.find { id(it.key) == block.id }?.value
                    if (status == null) block else block.copy(status = BlockStatus.valueOf(status))
                },
            ),
            now = now?.let(::Minute) ?: initial.now,
            settings = Settings(allowPastBedtime),
        )
    }
}
