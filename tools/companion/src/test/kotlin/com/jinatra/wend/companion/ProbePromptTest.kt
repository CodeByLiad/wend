package com.jinatra.wend.companion

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.UUID

private fun message(speaker: Speaker, text: String) = ProbeMessage(UUID.randomUUID().toString(), 1, 1, speaker, text)

class ProbePromptTest :
    FunSpec({
        test("bounded context retains the newest request and explicit facts without changing saved history") {
            val messages = (0 until 20).map { message(if (it % 2 == 0) Speaker.USER else Speaker.ASSISTANT, "Old-" + it + " " + "x".repeat(1500)) } +
                message(Speaker.USER, "Latest request")
            val fact = ProbeFact(UUID.randomUUID().toString(), 1, 1, "I enjoy walking")
            val data = ProbeData(messages, listOf(fact))
            val prompt = buildProbePrompt("Listen carefully.", data)
            (prompt.text.length <= PROMPT_CHARS) shouldBe true
            prompt.text shouldContain "Latest request"
            prompt.text shouldContain fact.text
            prompt.text shouldNotContain "Old-0 "
            (prompt.omittedMessages > 0) shouldBe true
            data.messages shouldBe messages
        }

        test("a bounded transcript begins with a user and never an orphaned reply") {
            val data = ProbeData(listOf(message(Speaker.USER, "a".repeat(2000)), message(Speaker.ASSISTANT, "Older reply"), message(Speaker.USER, "Latest")))
            val prompt = buildProbePrompt("s".repeat(5900), data)
            prompt.text.substringAfter("Conversation transcript:\n").startsWith("USER:") shouldBe true
            prompt.omittedMessages shouldBe 2
        }

        test("removing a fact excludes it from the next prompt without inventing another memory") {
            val fact = ProbeFact(UUID.randomUUID().toString(), 1, 1, "Unshared fact")
            val data = ProbeData(listOf(message(Speaker.USER, "Hello")), listOf(fact))
            buildProbePrompt("Listen", data.copy(facts = emptyList())).text shouldNotContain fact.text
        }

        test("requests and persisted records reject invalid sizes, identifiers and timestamps") {
            shouldThrow<IllegalArgumentException> { message(Speaker.USER, "x".repeat(MAX_MESSAGE + 1)) }
            shouldThrow<IllegalArgumentException> { ProbeFact(UUID.randomUUID().toString(), 1, 1, "x".repeat(MAX_FACT + 1)) }
            shouldThrow<IllegalArgumentException> { ProbeFact("not-a-uuid", 1, 1, "Fact") }
            shouldThrow<IllegalArgumentException> { ProbeFact(UUID.randomUUID().toString(), 2, 1, "Fact") }
            val item = message(Speaker.USER, "Hello")
            shouldThrow<IllegalArgumentException> { ProbeData(listOf(item, item)) }
            shouldThrow<IllegalArgumentException> { ProbeData(facts = List(MAX_FACTS + 1) { ProbeFact(UUID.randomUUID().toString(), 1, 1, "Fact") }) }
        }

        test("a reply needs a last user message and room for that request") {
            shouldThrow<IllegalArgumentException> { buildProbePrompt("Listen", ProbeData()) }
            shouldThrow<IllegalArgumentException> { buildProbePrompt("Listen", ProbeData(listOf(message(Speaker.ASSISTANT, "Hello")))) }
            shouldThrow<IllegalArgumentException> { buildProbePrompt("s".repeat(PROMPT_CHARS), ProbeData(listOf(message(Speaker.USER, "Hello")))) }
        }
    })
