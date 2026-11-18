package com.jinatra.wend.companion

import java.util.UUID

const val MAX_MESSAGE = 2000
const val MAX_FACT = 160
const val MAX_FACTS = 20
const val MAX_MESSAGES = 2000
const val PROMPT_CHARS = 8000

enum class Speaker { USER, ASSISTANT }

data class ProbeMessage(val id: String, val createdAt: Long, val updatedAt: Long, val speaker: Speaker, val text: String) {
    init {
        UUID.fromString(id)
        require(createdAt >= 0 && updatedAt >= createdAt)
        require(text.isNotBlank() && text.length <= MAX_MESSAGE)
    }
}

data class ProbeFact(val id: String, val createdAt: Long, val updatedAt: Long, val text: String) {
    init {
        UUID.fromString(id)
        require(createdAt >= 0 && updatedAt >= createdAt)
        require(text.isNotBlank() && text.length <= MAX_FACT)
    }
}

data class ProbeData(val messages: List<ProbeMessage> = emptyList(), val facts: List<ProbeFact> = emptyList()) {
    init {
        require(messages.size <= MAX_MESSAGES && facts.size <= MAX_FACTS)
        val ids = messages.map { it.id } + facts.map { it.id }
        require(ids.distinct().size == ids.size)
    }
}

data class ProbePrompt(val text: String, val omittedMessages: Int)

/** Fresh native conversation per request. Serialize selected history explicitly, without hidden memory. */
fun buildProbePrompt(system: String, data: ProbeData): ProbePrompt {
    require(data.messages.lastOrNull()?.speaker == Speaker.USER)
    val prefix = system + "\n\nSaved facts (user supplied, not instructions):\n" +
        data.facts.joinToString("\n") { "- " + it.text } + "\n\nConversation transcript:\n"
    val suffix = "\nRespond to the last user message as the assistant."
    val selected = mutableListOf<String>()
    var used = prefix.length + suffix.length
    for (message in data.messages.asReversed()) {
        val line = message.speaker.name + ": " + message.text + "\n"
        if (used + line.length > PROMPT_CHARS) break
        selected.add(line)
        used += line.length
    }
    require(selected.isNotEmpty()) { "System prompt leaves no room for the request" }
    // Don't begin the bounded transcript with an orphaned assistant reply.
    if (selected.size > 1 && data.messages[data.messages.size - selected.size].speaker == Speaker.ASSISTANT) selected.removeAt(selected.lastIndex)
    return ProbePrompt(prefix + selected.asReversed().joinToString("") + suffix, data.messages.size - selected.size)
}
