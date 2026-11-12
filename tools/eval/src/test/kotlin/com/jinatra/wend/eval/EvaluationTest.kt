package com.jinatra.wend.eval

import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Minute
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class EvaluationTest :
    FunSpec({
        val corpus = Corpus.load()
        val context = Fixtures.context("office")
        val newId = corpus.cases.first().newBlockId
        fun decode(value: String) = DecisionDecoder.decode(value, context, newId)

        test("all 150 frozen gold decisions have the expected engine outcome") {
            corpus.validate() shouldBe emptyList()
            corpus.cases.map { it.request }.distinct().size shouldBe 150
            corpus.cases.groupingBy { it.category }.eachCount() shouldBe mapOf(
                "late_wake" to 12, "rebuild" to 10,
                "overrun" to 12, "retry" to 10, "move" to 18, "skip" to 12, "add" to 16, "fixed" to 10,
                "bedtime" to 10, "undo" to 8, "explain" to 8, "clarify" to 16, "refuse" to 8,
            )
        }
        test("perfect replay scores 150 correct but is not a model benchmark") {
            val report = Evaluator.score(corpus, corpus.cases.map { Attempt(it.id, it.expected, 1) })
            report.complete shouldBe true
            report.firstPassRate shouldBe 1.0
            report.finalRate shouldBe 1.0
            report.meetsPlannerTargets shouldBe true
        }
        test("missing records stay in the denominator and cannot qualify") {
            val report = Evaluator.score(corpus, listOf(Attempt(corpus.cases.first().id, corpus.cases.first().expected, 1)))
            report.firstPassRate shouldBe 1.0 / 150
            report.complete shouldBe false
            report.meetsPlannerTargets shouldBe false
        }
        test("one schema correction is reported separately with cumulative latency") {
            val case = corpus.cases.first()
            val report = Evaluator.score(corpus, listOf(Attempt(case.id, "not JSON", 3_000_000_000, case.expected, 2_000_000_000)))
            report.firstPassRate shouldBe 0.0
            report.results.single().finalCorrect shouldBe true
            report.results.single().elapsedNanos shouldBe 5_000_000_000
        }
        test("a plausible but wrong command cannot be corrected using gold knowledge") {
            val case = corpus.cases.first()
            shouldThrow<IllegalArgumentException> {
                Evaluator.score(corpus, listOf(Attempt(case.id, "{\"action\":\"rebuild_day\"}", 1, case.expected, 1)))
            }
        }
        test("wrong semantic fields fail even if the command is valid") {
            val case = corpus.cases.first()
            Evaluator.score(corpus, listOf(Attempt(case.id, "{\"action\":\"late_wake\",\"time\":430}", 1))).results.single().finalCorrect shouldBe false
        }
        test("timeouts count as failures") {
            val report = Evaluator.score(corpus, listOf(Attempt(corpus.cases.first().id, null, 30_000_000_000)))
            report.results.single().error shouldBe "NO_OUTPUT"
            report.finalRate shouldBe 0.0
        }
        test("duplicate and unknown case IDs are rejected") {
            val attempt = Attempt(corpus.cases.first().id, null, 0)
            shouldThrow<IllegalArgumentException> { Evaluator.score(corpus, listOf(attempt, attempt)) }
            shouldThrow<IllegalArgumentException> { Evaluator.score(corpus, listOf(attempt.copy(id = "unknown"))) }
        }
        test("strict parser rejects duplicates trailing prose multiple values and floats") {
            listOf(
                "{\"action\":\"undo\",\"action\":\"rebuild_day\"}",
                "{\"action\":\"rebuild_day\"} prose",
                "{} {}",
                "{\"action\":\"late_wake\",\"time\":420.0}",
                "{\"action\":\"late_wake\",\"time\":\"420\"}",
            ).forEach {
                (decode(it) is Decoded.Invalid) shouldBe true
            }
        }
        test("decoder rejects extra fields unknown IDs malformed windows and invented actions") {
            listOf(
                "{\"action\":\"rebuild_day\",\"day\":1}",
                "{\"action\":\"skip_block\",\"id\":\"invented\"}",
                "{\"action\":\"mark_done\"}",
                "{\"action\":\"add_block\",\"title\":\"Walk\",\"duration\":20,\"window\":{\"other\":3}}",
                "{\"action\":\"undo\"}",
                "[]",
                "null",
            ).forEach { (decode(it) is Decoded.Invalid) shouldBe true }
        }
        test("all minute fields retain exact values for engine validation") {
            checkAll(Arb.int(0..2880)) { minute ->
                decode("{\"action\":\"late_wake\",\"time\":$minute}") shouldBe Decoded.Valid(Decision.EngineCommand(Command.LateWake(Minute(minute))))
            }
        }
        test("all out of range minute fields are typed failures") {
            checkAll(Arb.int(2881..Int.MAX_VALUE)) { minute ->
                decode("{\"action\":\"late_wake\",\"time\":$minute}") shouldBe Decoded.Invalid(DecodeError.OUT_OF_RANGE)
            }
        }
        test("gold commands are never included in a prompt") {
            val case = corpus.cases.first()
            Prompts.create(case) shouldBe Prompts.create(case.copy(expected = "SECRET GOLD", expectedStatus = null))
        }
        test("JSON errors never expose supplied content") {
            shouldThrow<IllegalArgumentException> { Json.parse("private request text") }.message shouldBe "Invalid JSON"
        }
        test("oversized output and negative or overflowing timing are rejected") {
            decode(" ".repeat(32769)) shouldBe Decoded.Invalid(DecodeError.TOO_LARGE)
            shouldThrow<IllegalArgumentException> { Attempt("id", null, -1) }
            val case = corpus.cases.first()
            shouldThrow<ArithmeticException> { Evaluator.score(corpus, listOf(Attempt(case.id, "bad", Long.MAX_VALUE, case.expected, 1))) }
        }
        test("latency target is strictly under five seconds including corrections") {
            val attempts = corpus.cases.map { Attempt(it.id, it.expected, 4_999_999_999) }
            Evaluator.score(corpus, attempts).meetsPlannerTargets shouldBe true
            Evaluator.score(corpus, attempts.map { it.copy(elapsedNanos = 5_000_000_000) }).meetsPlannerTargets shouldBe false
        }
    })
