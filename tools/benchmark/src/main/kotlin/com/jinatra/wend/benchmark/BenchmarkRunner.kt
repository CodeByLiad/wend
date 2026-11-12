package com.jinatra.wend.benchmark

import com.jinatra.wend.ai.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

internal const val RUN_MILLIS = 600_000L
internal const val SAMPLE_MILLIS = 1_000L

data class Telemetry(
    val pssKb: Int,
    val batteryPercent: Int?,
    val chargeMicroAh: Long?,
    val batteryCelsius: Double?,
    val charging: Boolean,
    val thermalStatus: Int?,
)

interface Measurements {
    fun elapsedNanos(): Long
    fun wallTimeMillis(): Long
    fun sample(): Telemetry
}

enum class RunOutcome { COMPLETED, CANCELLED, FAILED }

data class BenchmarkResult(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val outcome: RunOutcome,
    val loadNanos: Long,
    val runNanos: Long,
    val outputTokens: Long,
    val decodeNanos: Long,
    val completedGenerations: Int,
    val peakPssKb: Int,
    val batteryStart: Telemetry?,
    val batteryEnd: Telemetry?,
    val peakBatteryCelsius: Double?,
    val sampleCount: Int,
    val chargingObserved: Boolean,
) {
    val tokensPerSecond: Double? get() = if (decodeNanos > 0) outputTokens * 1e9 / decodeNanos else null
    val drainMicroAh: Long? get() = if (chargingObserved) null else batteryStart?.chargeMicroAh?.let { first -> batteryEnd?.chargeMicroAh?.let { last -> (first - last).takeIf { it >= 0 } } }
    val drainPercentagePoints: Int? get() = if (chargingObserved) null else batteryStart?.batteryPercent?.let { first -> batteryEnd?.batteryPercent?.let { last -> (first - last).takeIf { it >= 0 } } }
}

/** Injected measurements and duration make the real ten-minute loop testable without waiting. */
class BenchmarkRunner(private val measurements: Measurements, private val durationMillis: Long = RUN_MILLIS) {
    init {
        require(durationMillis > 0)
    }

    suspend fun run(engine: LlmEngine, model: java.io.File, prompt: String, progress: (Long) -> Unit): BenchmarkResult {
        val created = measurements.wallTimeMillis()
        var loadNanos = 0L
        var runStart: Long? = null
        var tokens = 0L
        var decodeNanos = 0L
        var generations = 0
        var peakPss = 0
        var peakTemp: Double? = null
        var samples = 0
        var charging = false
        var first: Telemetry? = null
        var last: Telemetry? = null
        var outcome = RunOutcome.FAILED
        var deadlineReached = false
        var elapsed = 0L
        fun sample() {
            val reading = measurements.sample()
            peakPss = maxOf(peakPss, reading.pssKb)
            reading.batteryCelsius?.let { peakTemp = maxOf(peakTemp ?: it, it) }
            if (runStart != null) {
                charging = charging || reading.charging
                last = reading
            }
            samples++
        }
        try {
            coroutineScope {
                sample()
                val sampler = launch {
                    while (true) {
                        delay(SAMPLE_MILLIS)
                        sample()
                        runStart?.let { progress((measurements.elapsedNanos() - it) / 1_000_000) }
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        val loadStart = measurements.elapsedNanos()
                        try {
                            engine.load(model)
                        } finally {
                            loadNanos = measurements.elapsedNanos() - loadStart
                        }
                    }
                    runStart = measurements.elapsedNanos()
                    first = measurements.sample()
                    charging = first.charging
                    val stopAtDeadline = launch {
                        delay(durationMillis)
                        deadlineReached = true
                        engine.cancel()
                    }
                    try {
                        withTimeout(durationMillis) {
                            while (true) {
                                ensureActive()
                                val metrics = withContext(Dispatchers.IO) { engine.generate(prompt) }
                                require(metrics.outputTokens > 0 && metrics.decodeNanos > 0)
                                tokens += metrics.outputTokens
                                decodeNanos += metrics.decodeNanos
                                generations++
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        outcome = RunOutcome.COMPLETED
                    } finally {
                        stopAtDeadline.cancel()
                    }
                } finally {
                    sampler.cancel()
                }
            }
        } catch (_: CancellationException) {
            outcome = RunOutcome.CANCELLED
        } catch (_: Exception) {
            outcome = if (deadlineReached && generations > 0) RunOutcome.COMPLETED else RunOutcome.FAILED
        } catch (_: LinkageError) {
            outcome = RunOutcome.FAILED
        } finally {
            sample()
            elapsed = runStart?.let { measurements.elapsedNanos() - it } ?: 0
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    engine.cancel()
                } finally {
                    engine.close()
                }
            }
        }
        if (outcome == RunOutcome.COMPLETED && generations == 0) outcome = RunOutcome.FAILED
        return BenchmarkResult(
            UUID.randomUUID().toString(), created, measurements.wallTimeMillis(), outcome,
            loadNanos, elapsed, tokens, decodeNanos, generations, peakPss, first, last, peakTemp, samples, charging,
        )
    }
}
