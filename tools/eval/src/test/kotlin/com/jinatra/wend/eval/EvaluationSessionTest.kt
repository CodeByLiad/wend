package com.jinatra.wend.eval

import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Kind
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Priority
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvaluationSessionTest :
    FunSpec({
        val corpus = Corpus.load()
        test("session makes one fresh request per case and checkpoints all progress") {
            var index = 0
            var clock = 0L
            var checkpoints = 0
            val attempts = EvaluationSession.run(corpus, TextGenerator { corpus.cases[index++].expected }, { clock++.times(100) }) {
                checkpoints++
                it.size shouldBe checkpoints
            }
            index shouldBe 150
            checkpoints shouldBe 150
            attempts.all { it.elapsedNanos == 100L } shouldBe true
            Evaluator.score(corpus, attempts).firstPassRate shouldBe 1.0
        }
        test("decoder errors get one correction with no gold answer in the correction prompt") {
            var calls = 0
            val attempts = EvaluationSession.run(
                corpus,
                TextGenerator { prompt ->
                    calls++
                    if (calls == 1) {
                        "bad"
                    } else if (calls == 2) {
                        prompt.contains("INVALID_JSON") shouldBe true
                        corpus.cases.first().expected.let { prompt.contains(it) } shouldBe false
                        corpus.cases.first().expected
                    } else {
                        "{\"action\":\"refuse\"}"
                    }
                },
            )
            calls shouldBe 151
            attempts.first().correction shouldBe corpus.cases.first().expected
        }
        test("generation failures remain failed attempts without unbounded retries") {
            var calls = 0
            val attempts = EvaluationSession.run(
                corpus,
                TextGenerator {
                    calls++
                    error("private runtime details")
                },
            )
            calls shouldBe 150
            attempts.all { it.response == null && it.correction == null } shouldBe true
            Evaluator.score(corpus, attempts).finalRate shouldBe 0.0
        }
        test("valid wrong answers are never repaired using the expected command") {
            var calls = 0
            EvaluationSession.run(
                corpus,
                TextGenerator {
                    calls++
                    "{\"action\":\"refuse\"}"
                },
            )
            calls shouldBe 150
        }
        test("failed correction includes its latency and remains incorrect") {
            var calls = 0
            var clock = 0L
            val attempts = EvaluationSession.run(
                corpus,
                TextGenerator {
                    calls++
                    if (calls == 1) {
                        "bad"
                    } else if (calls == 2) {
                        error("failed")
                    } else {
                        "{\"action\":\"refuse\"}"
                    }
                },
                { clock++.times(100) },
            )
            attempts.first().correction shouldBe ""
            Evaluator.score(corpus, attempts).results.first().elapsedNanos shouldBe 200
            Evaluator.score(corpus, attempts).results.first().finalCorrect shouldBe false
        }
        test("decoder maps IDs times fields and caller-assigned addition defaults independently of corpus") {
            val context = Fixtures.context("office")
            fun decode(text: String) = (DecisionDecoder.decode(text, context, "new-id") as Decoded.Valid).decision
            val id = Fixtures.id("A6")
            decode("{\"action\":\"overrun\",\"id\":\"$id\",\"newEnd\":1280}") shouldBe Decision.EngineCommand(Command.Overrun(id, Minute(1280)))
            decode("{\"action\":\"move_block\",\"id\":\"$id\",\"start\":1170}") shouldBe Decision.EngineCommand(Command.MoveBlock(id, Minute(1170)))
            decode("{\"action\":\"skip_block\",\"id\":\"$id\"}") shouldBe Decision.EngineCommand(Command.SkipBlock(id))
            decode("{\"action\":\"retry_block\",\"id\":\"$id\"}") shouldBe Decision.EngineCommand(Command.RetryBlock(id))
            decode("{\"action\":\"set_fixed\",\"id\":\"$id\"}") shouldBe Decision.EngineCommand(Command.SetFixed(id))
            decode("{\"action\":\"set_bedtime\",\"time\":1350}") shouldBe Decision.EngineCommand(Command.SetBedtime(Minute(1350)))
            val addition = ((decode("{\"action\":\"add_block\",\"title\":\"Walk\",\"duration\":20}") as Decision.EngineCommand).command as Command.AddBlock)
            addition.block.id shouldBe "new-id"
            addition.block.title shouldBe "Walk"
            addition.block.duration shouldBe 20
            addition.block.minDuration shouldBe 20
            addition.block.kind shouldBe Kind.FLEXIBLE
            addition.block.priority shouldBe Priority.NORMAL
            addition.requestedStart shouldBe null
        }
        test("exactly 135 of 150 correct satisfies the confirmed accuracy threshold") {
            val correct = corpus.cases.map { Attempt(it.id, it.expected, 1) }
            val atThreshold = correct.mapIndexed { index, attempt -> if (index < 15) attempt.copy(response = null) else attempt }
            Evaluator.score(corpus, atThreshold).meetsPlannerTargets shouldBe true
            Evaluator.score(corpus, atThreshold.mapIndexed { index, attempt -> if (index == 15) attempt.copy(response = null) else attempt }).meetsPlannerTargets shouldBe false
        }
    })
