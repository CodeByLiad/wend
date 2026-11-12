package com.jinatra.wend.eval

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import java.io.StringWriter

/** Strict bounded JSON. Parser exceptions are sanitized so raw responses cannot leak into logs. */
object Json {
    private val factory = JsonFactory.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(32768).maxNumberLength(20).build())
        .build()

    fun parse(text: String): Any? {
        require(text.length <= 2_000_000) { "JSON input too large" }
        try {
            return factory.createParser(text).use { parser ->
                require(parser.nextToken() != null)
                val result = read(parser)
                require(parser.nextToken() == null)
                result
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid JSON")
        }
    }

    private fun read(parser: JsonParser): Any? = when (parser.currentToken()) {
        JsonToken.START_OBJECT -> linkedMapOf<String, Any?>().apply {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME)
                val name = parser.currentName()
                require(parser.nextToken() != null)
                put(name, read(parser))
            }
        }

        JsonToken.START_ARRAY -> buildList {
            while (parser.nextToken() != JsonToken.END_ARRAY) add(read(parser))
        }

        JsonToken.VALUE_STRING -> parser.text

        JsonToken.VALUE_NUMBER_INT -> parser.longValue

        JsonToken.VALUE_NUMBER_FLOAT -> parser.doubleValue.also { require(it.isFinite()) }

        JsonToken.VALUE_TRUE -> true

        JsonToken.VALUE_FALSE -> false

        JsonToken.VALUE_NULL -> null

        else -> error("Invalid JSON token")
    }

    fun stringify(value: Any?): String {
        val output = StringWriter()
        factory.createGenerator(output).use { writer ->
            fun write(item: Any?) {
                when (item) {
                    null -> writer.writeNull()

                    is String -> writer.writeString(item)

                    is Boolean -> writer.writeBoolean(item)

                    is Int -> writer.writeNumber(item)

                    is Long -> writer.writeNumber(item)

                    is Double -> if (item.isFinite()) writer.writeNumber(item) else writer.writeNull()

                    is Map<*, *> -> {
                        writer.writeStartObject()
                        for ((key, entry) in item) {
                            writer.writeFieldName(key as String)
                            write(entry)
                        }
                        writer.writeEndObject()
                    }

                    is Iterable<*> -> {
                        writer.writeStartArray()
                        item.forEach(::write)
                        writer.writeEndArray()
                    }

                    else -> error("Unsupported JSON value")
                }
            }
            write(value)
        }
        return output.toString()
    }
}

internal fun Any?.objectValue(): Map<String, Any?> {
    require(this is Map<*, *> && keys.all { it is String }) { "JSON object required" }
    @Suppress("UNCHECKED_CAST")
    return this as Map<String, Any?>
}
