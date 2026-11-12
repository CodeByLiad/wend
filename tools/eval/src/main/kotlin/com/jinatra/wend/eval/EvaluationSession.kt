package com.jinatra.wend.eval

/** Generator implementations must bound execution and throw on failed/timed-out inference. */
fun interface TextGenerator {
    fun complete(prompt: String): String
}

object EvaluationSession {
    fun run(corpus: Corpus, generator: TextGenerator, clockNanos: () -> Long = System::nanoTime, checkpoint: (List<Attempt>) -> Unit = {}): List<Attempt> {
        check(corpus.validate().isEmpty())
        val attempts = mutableListOf<Attempt>()
        for (case in corpus.cases) {
            val prompt = Prompts.create(case)
            fun generate(input: String): String? = try {
                generator.complete(input)
            } catch (_: Exception) {
                null
            }
            val started = clockNanos()
            val first = generate(prompt)
            val decoded = first?.let { DecisionDecoder.decode(it, case.context, case.newBlockId) }
            // Include schema validation and proposal construction in send-to-proposal timing.
            (decoded as? Decoded.Valid)?.decision?.proposal(case.context)
            val elapsed = clockNanos() - started
            var correction: String? = null
            var correctionNanos: Long? = null
            if (decoded is Decoded.Invalid && first.length <= 32768) {
                val retryStarted = clockNanos()
                correction = generate("$prompt\nPREVIOUS OUTPUT:\n$first\n${Prompts.correction(decoded.error)}")
                correction?.let { DecisionDecoder.decode(it, case.context, case.newBlockId) }
                    ?.let { (it as? Decoded.Valid)?.decision?.proposal(case.context) }
                val duration = clockNanos() - retryStarted
                // A failed correction still consumes time and counts as a failed final answer.
                correction = correction ?: ""
                correctionNanos = duration
            }
            attempts += Attempt(case.id, first, elapsed, correction, correctionNanos)
            checkpoint(attempts.toList())
        }
        return attempts
    }
}
