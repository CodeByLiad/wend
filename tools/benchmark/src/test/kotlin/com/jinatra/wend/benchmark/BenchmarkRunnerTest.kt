package com.jinatra.wend.benchmark

import com.jinatra.wend.ai.GenerationMetrics
import com.jinatra.wend.ai.LlmEngine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private class FakeEngine(private val failLoad: Boolean = false, private val failGenerate: Boolean = false) : LlmEngine {
    override fun complete(prompt: String, constrainedJson: Boolean): com.jinatra.wend.ai.Completion = error("Not used by benchmark")
    val loaded = CompletableDeferred<Unit>()
    var closed = false
    var cancelled = AtomicBoolean(false)
    override fun load(model: File) {
        if (failLoad) error("load failed")
        loaded.complete(Unit)
    }
    override fun generate(prompt: String): GenerationMetrics {
        if (failGenerate) error("generation failed")
        Thread.sleep(2)
        return GenerationMetrics(10, 5_000_000)
    }
    override fun cancel() {
        cancelled.set(true)
    }
    override fun close() {
        closed = true
    }
}

private class FakeMeasurements : Measurements {
    var reading = Telemetry(4096, 75, 2_000_000, 32.0, false, 0)
    override fun elapsedNanos() = System.nanoTime()
    override fun wallTimeMillis() = 123456789L
    override fun sample() = reading
}

private fun result(first: Telemetry?, last: Telemetry?, charging: Boolean = false) = BenchmarkResult(
    "id", 0, 0, RunOutcome.COMPLETED, 0, 600_000_000_000, 100, 50_000_000, 10,
    100, first, last, 35.0, 601, charging,
)

class BenchmarkRunnerTest :
    FunSpec({
        test("production protocol is ten minutes with one second sampling") {
            RUN_MILLIS shouldBe 600_000L
            SAMPLE_MILLIS shouldBe 1_000L
        }
        test("timed run uses runtime token counts and closes the model") {
            val engine = FakeEngine()
            val measurements = FakeMeasurements()
            val result = BenchmarkRunner(measurements, 100).run(engine, File("model"), "fixed prompt") {}
            result.outcome shouldBe RunOutcome.COMPLETED
            result.outputTokens shouldBeGreaterThan 0
            result.tokensPerSecond shouldBe 2000.0
            result.peakPssKb shouldBe 4096
            result.runNanos shouldBeGreaterThan 0
            result.createdAt shouldBe 123456789L
            result.updatedAt shouldBe 123456789L
            result.drainMicroAh shouldBe 0L
            result.drainPercentagePoints shouldBe 0
            engine.closed shouldBe true
            engine.cancelled.get() shouldBe true
        }
        test("failed load is an explicit failed result and still closes the backend") {
            val engine = FakeEngine(failLoad = true)
            val result = BenchmarkRunner(FakeMeasurements(), 50).run(engine, File("model"), "fixed prompt") {}
            result.outcome shouldBe RunOutcome.FAILED
            result.completedGenerations shouldBe 0
            result.tokensPerSecond shouldBe null
            result.batteryStart shouldBe null
            engine.closed shouldBe true
        }
        test("failed generation does not invent throughput") {
            val engine = FakeEngine(failGenerate = true)
            val result = BenchmarkRunner(FakeMeasurements(), 50).run(engine, File("model"), "fixed prompt") {}
            result.outcome shouldBe RunOutcome.FAILED
            result.tokensPerSecond shouldBe null
            engine.closed shouldBe true
        }
        test("cancellation releases the backend") {
            coroutineScope {
                val engine = FakeEngine()
                val run = async { BenchmarkRunner(FakeMeasurements(), 10_000).run(engine, File("model"), "fixed prompt") {} }
                engine.loaded.await()
                delay(5)
                run.cancel()
                run.join()
                engine.closed shouldBe true
                engine.cancelled.get() shouldBe true
            }
        }
        test("charging makes drain unavailable") {
            val first = Telemetry(100, 80, 2000, 30.0, true, 0)
            val last = first.copy(batteryPercent = 70, chargeMicroAh = 1800)
            result(first, last, true).drainMicroAh shouldBe null
            result(first, last, true).drainPercentagePoints shouldBe null
        }
        test("missing sensors stay unavailable and charge increases are not called drain") {
            val first = Telemetry(100, null, null, null, false, null)
            result(first, first).drainMicroAh shouldBe null
            result(first, first).drainPercentagePoints shouldBe null
            result(first.copy(chargeMicroAh = 100), first.copy(chargeMicroAh = 110)).drainMicroAh shouldBe null
        }
        test("discharge reports microamp hours and percentage points without conversion guesses") {
            val first = Telemetry(100, 80, 2000, 30.0, false, 0)
            val last = first.copy(batteryPercent = 77, chargeMicroAh = 1500)
            result(first, last).drainMicroAh shouldBe 500L
            result(first, last).drainPercentagePoints shouldBe 3
        }
    })
