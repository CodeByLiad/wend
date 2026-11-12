package com.jinatra.wend.eval

import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Day
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.SchedulingEngine
import com.jinatra.wend.engine.Settings
import com.jinatra.wend.engine.Window
import com.jinatra.wend.engine.WindowBound

data class EvaluationContext(val day: Day, val now: Minute, val settings: Settings = Settings(), val previousDay: Day? = null)

enum class ClarificationField { TARGET, TIME, DURATION, CHOICE, HISTORY, DATE }

sealed interface Decision {
    data class EngineCommand(val command: Command) : Decision
    data class Clarify(val field: ClarificationField) : Decision
    data object Refuse : Decision
    data object ExplainDay : Decision
}

enum class DecodeError { INVALID_JSON, TOO_LARGE, MISSING_FIELD, WRONG_TYPE, EXTRA_FIELD, UNKNOWN_ACTION, UNKNOWN_BLOCK, OUT_OF_RANGE }
sealed interface Decoded {
    data class Valid(val decision: Decision) : Decoded
    data class Invalid(val error: DecodeError) : Decoded
}

private class BadOutput(val code: DecodeError) : RuntimeException(code.name)

object DecisionDecoder {
    fun decode(text: String, context: EvaluationContext, newBlockId: String): Decoded {
        if (text.length > 32768) return Decoded.Invalid(DecodeError.TOO_LARGE)
        val parsed = try {
            Json.parse(text)
        } catch (_: IllegalArgumentException) {
            return Decoded.Invalid(DecodeError.INVALID_JSON)
        }
        return try {
            val root = parsed as? Map<*, *> ?: throw BadOutput(DecodeError.WRONG_TYPE)
            val fields = Fields(root)
            val action = fields.string("action")
            fun target() = fields.string("id").also { id ->
                if (context.day.blocks.none { it.id == id }) throw BadOutput(DecodeError.UNKNOWN_BLOCK)
            }
            val decision = when (action) {
                "rebuild_day" -> Decision.EngineCommand(Command.RebuildDay)

                "late_wake" -> Decision.EngineCommand(Command.LateWake(Minute(fields.integer("time"))))

                "overrun" -> Decision.EngineCommand(Command.Overrun(target(), Minute(fields.integer("newEnd"))))

                "retry_block" -> Decision.EngineCommand(Command.RetryBlock(target()))

                "move_block" -> Decision.EngineCommand(Command.MoveBlock(target(), Minute(fields.integer("start"))))

                "skip_block" -> Decision.EngineCommand(Command.SkipBlock(target()))

                "set_fixed" -> Decision.EngineCommand(Command.SetFixed(target()))

                "set_bedtime" -> Decision.EngineCommand(Command.SetBedtime(Minute(fields.integer("time"))))

                "undo" -> Decision.EngineCommand(Command.Undo(context.previousDay ?: throw BadOutput(DecodeError.OUT_OF_RANGE)))

                "explain_day" -> Decision.ExplainDay

                "refuse" -> Decision.Refuse

                "clarify" -> Decision.Clarify(enumValue<ClarificationField>(fields.string("field").uppercase(java.util.Locale.ROOT)))

                "add_block" -> {
                    val title = fields.string("title").also { if (it.isBlank() || it.length > 200) throw BadOutput(DecodeError.OUT_OF_RANGE) }
                    val duration = fields.integer("duration", 1)
                    val minimum = fields.optionalInteger("minDuration", 1) ?: duration
                    val kind = fields.optionalString("kind")?.let { enumValue<Kind>(it) } ?: Kind.FLEXIBLE
                    val priority = fields.optionalString("priority")?.let { enumValue<Priority>(it) } ?: Priority.NORMAL
                    val requested = fields.optionalInteger("requestedStart")?.let(::Minute)
                    val after = fields.optionalString("after")?.also { id ->
                        if (context.day.blocks.none { it.id == id }) throw BadOutput(DecodeError.UNKNOWN_BLOCK)
                    }
                    val window = fields.optionalObject("window")?.let { bounds ->
                        fun bound(key: String): WindowBound? = when (val value = bounds.take(key, false)) {
                            null -> null

                            is Long -> WindowBound.Absolute(Minute(checkedInt(value, 0)))

                            is Map<*, *> -> Fields(value).let { relative ->
                                val result = WindowBound.BeforeBedtime(relative.integer("beforeBedtime"))
                                relative.finish()
                                result
                            }

                            else -> throw BadOutput(DecodeError.WRONG_TYPE)
                        }
                        Window(bound("start"), bound("end")).also { bounds.finish() }
                    }
                    val start = requested?.value ?: ((maxOf(context.day.wake.value, context.now.value) + 4) / 5 * 5)
                    val end = start.toLong() + duration
                    if (end > 2880) throw BadOutput(DecodeError.OUT_OF_RANGE)
                    Decision.EngineCommand(Command.AddBlock(Block(newBlockId, title, kind, Minute(start), Minute(end.toInt()), duration, minimum, priority, window, after), requested))
                }

                else -> throw BadOutput(DecodeError.UNKNOWN_ACTION)
            }
            fields.finish()
            Decoded.Valid(decision)
        } catch (error: BadOutput) {
            Decoded.Invalid(error.code)
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T = enumValues<T>().find { it.name == value } ?: throw BadOutput(DecodeError.OUT_OF_RANGE)
}

private fun checkedInt(value: Long, minimum: Int): Int {
    if (value !in minimum.toLong()..2880L) throw BadOutput(DecodeError.OUT_OF_RANGE)
    return value.toInt()
}

private class Fields(private val values: Map<*, *>) {
    private val consumed = mutableSetOf<String>()
    fun take(name: String, required: Boolean = true): Any? {
        consumed += name
        if (required && name !in values) throw BadOutput(DecodeError.MISSING_FIELD)
        return values[name]
    }
    fun string(name: String): String = take(name) as? String ?: throw BadOutput(DecodeError.WRONG_TYPE)
    fun optionalString(name: String): String? = take(name, false)?.let { it as? String ?: throw BadOutput(DecodeError.WRONG_TYPE) }
    fun integer(name: String, minimum: Int = 0): Int = checkedInt(take(name) as? Long ?: throw BadOutput(DecodeError.WRONG_TYPE), minimum)
    fun optionalInteger(name: String, minimum: Int = 0): Int? = take(name, false)?.let { checkedInt(it as? Long ?: throw BadOutput(DecodeError.WRONG_TYPE), minimum) }
    fun optionalObject(name: String): Fields? = take(name, false)?.let { Fields(it as? Map<*, *> ?: throw BadOutput(DecodeError.WRONG_TYPE)) }
    fun finish() {
        if (values.keys.any { it !in consumed }) throw BadOutput(DecodeError.EXTRA_FIELD)
    }
}

fun Decision.proposal(context: EvaluationContext): Proposal? = when (this) {
    is Decision.EngineCommand -> SchedulingEngine.propose(context.day, command, context.now, context.settings)
    else -> null
}
