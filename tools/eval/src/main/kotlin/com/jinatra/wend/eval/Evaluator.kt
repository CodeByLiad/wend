package com.jinatra.wend.eval

import kotlin.math.ceil

/** Durations include inference, decoding and engine proposal creation; exclude model loading. */
data class Attempt(val id: String, val response: String?, val elapsedNanos: Long, val correction: String? = null, val correctionNanos: Long? = null) {
    init {
        require(elapsedNanos >= 0)
        require((correction == null) == (correctionNanos == null))
        require(correctionNanos == null || correctionNanos >= 0)
    }
    companion object {
        fun parse(line: String): Attempt {
            val root = Json.parse(line).objectValue()
            require(root.keys.all { it in setOf("id", "response", "elapsedNanos", "correction", "correctionNanos") })
            return Attempt(
                root.getValue("id") as String,
                root["response"] as? String,
                root.getValue("elapsedNanos") as Long,
                root["correction"] as? String,
                root["correctionNanos"] as? Long,
            )
        }
    }
}

data class CaseResult(
    val id: String,
    val category: String,
    val firstCorrect: Boolean,
    val finalCorrect: Boolean,
    val error: String?,
    val elapsedNanos: Long,
    val corrected: Boolean,
)

data class EvaluationReport(val corpus: Corpus, val results: List<CaseResult>) {
    val complete: Boolean get() = results.size == corpus.cases.size
    val firstPassRate: Double get() = results.count { it.firstCorrect }.toDouble() / corpus.cases.size
    val finalRate: Double get() = results.count { it.finalCorrect }.toDouble() / corpus.cases.size
    val meetsPlannerTargets: Boolean get() = complete && finalRate >= 0.9 && results.all { it.elapsedNanos < 5_000_000_000 }

    fun json(): Map<String, Any?> {
        val sorted = results.map { it.elapsedNanos }.sorted()
        fun percentile(p: Double) = sorted.getOrNull((ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0))
        return mapOf(
            "corpusVersion" to corpus.version, "corpusSha256" to corpus.sha256,
            "state" to if (complete) "COMPLETE" else "PARTIAL", "expectedCount" to corpus.cases.size, "completedCount" to results.size,
            "firstPassRate" to firstPassRate, "afterCorrectionRate" to finalRate,
            "meetsPlannerTargets" to meetsPlannerTargets, "latencyPolicy" to "every proposal under five seconds, including correction",
            "medianNanos" to percentile(0.5), "p95Nanos" to percentile(0.95), "maxNanos" to sorted.lastOrNull(),
            "categories" to corpus.cases.map { it.category }.distinct().associateWith { category ->
                val expected = corpus.cases.count { it.category == category }
                val actual = results.filter { it.category == category }
                mapOf("expected" to expected, "completed" to actual.size, "firstCorrect" to actual.count { it.firstCorrect }, "finalCorrect" to actual.count { it.finalCorrect })
            },
            "cases" to results.map {
                mapOf(
                    "id" to it.id,
                    "firstCorrect" to it.firstCorrect,
                    "finalCorrect" to it.finalCorrect,
                    "error" to it.error,
                    "elapsedNanos" to it.elapsedNanos,
                    "corrected" to it.corrected,
                )
            },
        )
    }
}

object Evaluator {
    fun score(corpus: Corpus, attempts: List<Attempt>): EvaluationReport {
        require(attempts.map { it.id }.distinct().size == attempts.size) { "Duplicate request ID" }
        val cases = corpus.cases.associateBy { it.id }
        val results = attempts.map { attempt ->
            val case = requireNotNull(cases[attempt.id]) { "Unknown request ID" }
            val expected = (DecisionDecoder.decode(case.expected, case.context, case.newBlockId) as Decoded.Valid).decision
            val first = attempt.response?.let { DecisionDecoder.decode(it, case.context, case.newBlockId) }
            require(attempt.correction == null || first is Decoded.Invalid) { "Correction only permitted after a decoding error" }
            val final = attempt.correction?.let { DecisionDecoder.decode(it, case.context, case.newBlockId) } ?: first
            fun correct(decoded: Decoded?): Boolean = decoded is Decoded.Valid && decoded.decision == expected &&
                decoded.decision.proposal(case.context)?.status == case.expectedStatus
            val elapsed = Math.addExact(attempt.elapsedNanos, attempt.correctionNanos ?: 0)
            CaseResult(
                case.id,
                case.category,
                correct(first),
                correct(final),
                when (final) {
                    null -> "NO_OUTPUT"
                    is Decoded.Invalid -> final.error.name
                    is Decoded.Valid -> if (correct(final)) null else "WRONG_DECISION"
                },
                elapsed,
                attempt.correction != null,
            )
        }
        return EvaluationReport(corpus, results)
    }
}
