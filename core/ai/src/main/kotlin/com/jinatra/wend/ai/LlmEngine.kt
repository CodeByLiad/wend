package com.jinatra.wend.ai

import java.io.File

enum class RuntimeBackend { LITERT_CPU, LITERT_GPU, LLAMA_CPU }

data class GenerationMetrics(val outputTokens: Int, val decodeNanos: Long)

data class InferenceConfig(val contextTokens: Int = 2048, val outputTokens: Int = 128) {
    init {
        require(contextTokens in 512..32768 && outputTokens in 1 until contextTokens)
    }
}

data class Completion(val text: String, val metrics: GenerationMetrics)

/** Blocking calls run on a worker thread. Text is never logged or retained. */
interface LlmEngine : AutoCloseable {
    fun load(model: File)
    fun generate(prompt: String): GenerationMetrics

    /** Fresh conversation, model-native chat template, optionally constrained to one JSON object. */
    fun complete(prompt: String, constrainedJson: Boolean = true): Completion
    fun cancel()
}

object LlmEngines {
    fun create(backend: RuntimeBackend, cache: File, config: InferenceConfig = InferenceConfig()): LlmEngine = when (backend) {
        RuntimeBackend.LITERT_CPU -> LiteRtEngine(false, cache, config)
        RuntimeBackend.LITERT_GPU -> LiteRtEngine(true, cache, config)
        RuntimeBackend.LLAMA_CPU -> LlamaEngine(config)
    }
}
