package com.jinatra.wend.today

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.Window
import com.jinatra.wend.engine.WindowBound
import java.time.LocalDate
import java.util.UUID

/**
 * Synthetic sample day for the prototype, shown until `:core:data` exists.
 * Its titles are written in code, not strings.xml, because they stand in for
 * user-entered data rather than interface text.
 */
object SampleDay {
    /** The prototype clock starts here so the sample day reads as mid-morning. */
    const val START_MINUTE = 10 * 60 + 25

    fun create(date: LocalDate): Day {
        val ids = mutableMapOf<String, String>()
        fun id(title: String) = ids.getOrPut(title) { UUID.nameUUIDFromBytes(title.toByteArray()).toString() }
        fun block(
            title: String,
            kind: Kind,
            start: String,
            end: String,
            min: Int? = null,
            priority: Priority? = Priority.NORMAL,
            window: Window? = null,
            after: String? = null,
            status: BlockStatus = BlockStatus.PLANNED,
        ): Block {
            val s = hm(start)
            val e = hm(end)
            return Block(
                id = id(title),
                title = title,
                kind = kind,
                start = Minute(s),
                end = Minute(e),
                duration = e - s,
                minDuration = min ?: (e - s),
                priority = if (kind == Kind.FIXED) null else priority,
                window = window,
                after = after?.let(::id),
                status = status,
            )
        }
        val blocks = listOf(
            block("Morning routine", Kind.FLEXIBLE, "07:00", "07:30", min = 15, status = BlockStatus.DONE),
            block("Breakfast", Kind.FLEXIBLE, "07:30", "07:50", min = 10, priority = Priority.HIGH, status = BlockStatus.DONE),
            block("Commute", Kind.FIXED, "08:15", "08:45", status = BlockStatus.DONE),
            block("Team standup", Kind.FIXED, "09:00", "09:15", status = BlockStatus.DONE),
            block("Deep work", Kind.FLEXIBLE, "09:30", "11:00", min = 60, priority = Priority.HIGH, status = BlockStatus.IN_PROGRESS),
            block("Email and admin", Kind.FLEXIBLE, "11:00", "11:45", min = 20, priority = Priority.LOW),
            block("Lunch with Sam", Kind.FIXED, "12:30", "13:30"),
            block("Design review", Kind.FIXED, "14:00", "15:00"),
            block("Write report", Kind.FLEXIBLE, "15:15", "16:30", min = 45, window = Window(end = WindowBound.Absolute(Minute(hm("18:00"))))),
            block("Gym", Kind.FLEXIBLE, "18:00", "19:00", min = 45, window = Window(WindowBound.Absolute(Minute(hm("17:00"))), WindowBound.Absolute(Minute(hm("21:00"))))),
            block("Shower", Kind.FLEXIBLE, "19:00", "19:20", min = 10, after = "Gym"),
            block("Dinner", Kind.FLEXIBLE, "19:30", "20:15", min = 30, priority = Priority.HIGH),
            block("Guitar practice", Kind.OPTIONAL, "20:30", "21:00", min = 15, priority = Priority.LOW),
            block("Reading", Kind.OPTIONAL, "21:30", "22:00", min = 15),
            block("Wind down", Kind.FLEXIBLE, "22:30", "23:00", min = 20, priority = Priority.HIGH, window = Window(start = WindowBound.BeforeBedtime(60))),
        )
        return Day(date = date, wake = Minute(hm("07:00")), bedtime = Minute(hm("23:00")), blocks = blocks)
    }

    private fun hm(text: String): Int = text.substringBefore(':').toInt() * 60 + text.substringAfter(':').toInt()
}
