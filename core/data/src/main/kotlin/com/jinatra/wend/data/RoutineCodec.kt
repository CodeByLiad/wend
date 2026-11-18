package com.jinatra.wend.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

internal fun encodeRecurrence(rule: Recurrence): String {
    val json = JSONObject().put("version", 1)
    when (rule) {
        Recurrence.Daily -> json.put("kind", "DAILY")
        is Recurrence.Weekdays -> json.put("kind", "WEEKDAYS").put("days", JSONArray(rule.days.sortedBy { it.value }.map { it.name }))
        is Recurrence.EveryNDays -> json.put("kind", "EVERY_N_DAYS").put("interval", rule.interval).put("anchor", rule.anchor.toString())
        is Recurrence.Monthly -> json.put("kind", "MONTHLY").put("day", rule.day)
    }
    return json.toString()
}
internal fun decodeRecurrence(text: String): Recurrence {
    val json = JSONObject(text)
    require(json.getInt("version") == 1)
    return when (json.getString("kind")) {
        "DAILY" -> Recurrence.Daily
        "WEEKDAYS" -> json.getJSONArray("days").let { days -> Recurrence.Weekdays((0 until days.length()).map { DayOfWeek.valueOf(days.getString(it)) }.toSet()) }
        "EVERY_N_DAYS" -> Recurrence.EveryNDays(json.getInt("interval"), LocalDate.parse(json.getString("anchor")))
        "MONTHLY" -> Recurrence.Monthly(json.getInt("day"))
        else -> error("Unsupported recurrence")
    }
}
