package com.jinatra.wend.ai

import java.io.File

enum class RuntimeBackend { LITERT_CPU, LITERT_GPU, LLAMA_CPU }

data class GenerationMetrics(val outputTokens: Int, val decodeNanos: Long)

/** Blocking calls run on a worker thread. Text is never logged or retained. */
interface LlmEngine : AutoCloseable {
    fun load(model: File)
    fun generate(prompt: String): GenerationMetrics
    fun cancel()
}

object LlmEngines {
    fun create(backend: RuntimeBackend, cache: File): LlmEngine = when (backend) {
        RuntimeBackend.LITERT_CPU -> LiteRtEngine(false, cache)
        RuntimeBackend.LITERT_GPU -> LiteRtEngine(true, cache)
        RuntimeBackend.LLAMA_CPU -> LlamaEngine()
    }
}
