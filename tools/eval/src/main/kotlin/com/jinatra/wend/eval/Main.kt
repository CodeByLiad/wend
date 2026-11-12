package com.jinatra.wend.eval

import java.io.File

fun main(args: Array<String>) {
    val corpus = Corpus.load()
    val errors = corpus.validate()
    check(errors.isEmpty()) { errors.joinToString("\n") }
    when (args.firstOrNull() ?: "validate") {
        "validate" -> println("Validated ${corpus.cases.size} requests (${corpus.version}, SHA-256 ${corpus.sha256})")

        "prompts" -> {
            require(args.size == 2) { "Usage: prompts OUTPUT.jsonl" }
            File(args[1]).bufferedWriter().use { writer ->
                corpus.cases.forEach { case ->
                    writer.appendLine(Json.stringify(mapOf("id" to case.id, "prompt" to Prompts.create(case))))
                }
            }
        }

        "score" -> {
            require(args.size == 3) { "Usage: score INPUT.jsonl OUTPUT.json" }
            val attempts = File(args[1]).useLines { lines -> lines.filter(String::isNotBlank).map(Attempt::parse).toList() }
            File(args[2]).writeText(Json.stringify(mapOf("kind" to "EXTERNAL_UNVERIFIED_INPUT", "results" to Evaluator.score(corpus, attempts).json())) + "\n")
            println("Scored ${attempts.size} records. Results saved locally.")
        }

        else -> error("Commands: validate, prompts OUTPUT.jsonl, score INPUT.jsonl OUTPUT.json")
    }
}
