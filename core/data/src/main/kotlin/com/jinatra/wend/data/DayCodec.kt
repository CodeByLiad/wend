package com.jinatra.wend.data

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Change
import com.jinatra.wend.engine.ChangeType
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.Window
import com.jinatra.wend.engine.WindowBound
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Versioned exact engine snapshot for ChangeLog. This is never a plaintext file. */
object DayCodec {
    fun encode(day: Day): String = JSONObject().put("version", 1).put("date", day.date.toString())
        .put("wake", day.wake.value).put("bedtime", day.bedtime.value)
        .put(
            "blocks",
            JSONArray(
                day.blocks.map { block ->
                    JSONObject().put("id", block.id).put("title", block.title).put("kind", block.kind.name)
                        .put("start", block.start.value).put("end", block.end.value)
                        .put("duration", block.duration).put("minDuration", block.minDuration)
                        .put("priority", block.priority?.name ?: JSONObject.NULL)
                        .put("window", block.window?.let(::windowObject) ?: JSONObject.NULL)
                        .put("after", block.after ?: JSONObject.NULL).put("status", block.status.name)
                },
            ),
        ).toString()

    fun decode(text: String): Day {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported schedule snapshot version" }
        val blocks = root.getJSONArray("blocks")
        return Day(
            LocalDate.parse(root.getString("date")),
            Minute(root.getInt("wake")),
            Minute(root.getInt("bedtime")),
            (0 until blocks.length()).map { index ->
                val b = blocks.getJSONObject(index)
                Block(
                    b.getString("id"), b.getString("title"), Kind.valueOf(b.getString("kind")),
                    Minute(b.getInt("start")), Minute(b.getInt("end")), b.getInt("duration"), b.getInt("minDuration"),
                    b.nullableString("priority")?.let(Priority::valueOf),
                    if (b.isNull("window")) null else window(b.getJSONObject("window")),
                    b.nullableString("after"), BlockStatus.valueOf(b.getString("status")),
                )
            },
        )
    }

    internal fun encodeChanges(changes: List<Change>): String = JSONArray(
        changes.map {
            JSONObject().put("blockId", it.blockId).put("types", JSONArray(it.types.sortedBy { type -> type.ordinal }.map { type -> type.name }))
                .put("reason", it.reason.name).put("cause", it.causeBlockId ?: JSONObject.NULL)
        },
    ).toString()

    internal fun decodeChanges(text: String): List<Change> {
        val values = JSONArray(text)
        return (0 until values.length()).map { index ->
            val item = values.getJSONObject(index)
            val types = item.getJSONArray("types")
            Change(
                item.getString("blockId"),
                (0 until types.length()).map { ChangeType.valueOf(types.getString(it)) }.toSet(),
                Reason.valueOf(item.getString("reason")),
                item.nullableString("cause"),
            )
        }
    }

    internal fun encodeWindow(window: Window?): String? = window?.let { windowObject(it).toString() }
    internal fun decodeWindow(text: String?): Window? = text?.let { window(JSONObject(it)) }

    private fun windowObject(window: Window): JSONObject = JSONObject()
        .put("start", boundObject(window.start)).put("end", boundObject(window.end))

    private fun boundObject(bound: WindowBound?): Any = when (bound) {
        null -> JSONObject.NULL
        is WindowBound.Absolute -> JSONObject().put("type", "absolute").put("value", bound.time.value)
        is WindowBound.BeforeBedtime -> JSONObject().put("type", "beforeBedtime").put("value", bound.minutes)
    }

    private fun window(value: JSONObject) = Window(bound(value, "start"), bound(value, "end"))
    private fun bound(parent: JSONObject, name: String): WindowBound? {
        if (parent.isNull(name)) return null
        val b = parent.getJSONObject(name)
        return when (b.getString("type")) {
            "absolute" -> WindowBound.Absolute(Minute(b.getInt("value")))
            "beforeBedtime" -> WindowBound.BeforeBedtime(b.getInt("value"))
            else -> error("Unknown window bound")
        }
    }
}

private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
