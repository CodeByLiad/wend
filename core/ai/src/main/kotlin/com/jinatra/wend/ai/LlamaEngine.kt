package com.jinatra.wend.ai

import java.io.File

internal class LlamaEngine(private val config: InferenceConfig) : LlmEngine {
    @Volatile private var handle = 0L
    override fun load(model: File) {
        check(handle == 0L)
        handle = nativeLoad(model.path, config.contextTokens)
        check(handle != 0L)
    }
    override fun generate(prompt: String): GenerationMetrics {
        val result = nativeGenerate(handle, prompt)
        return GenerationMetrics(result[0].toInt(), result[1])
    }

    override fun complete(prompt: String, constrainedJson: Boolean): Completion {
        val result = nativeComplete(handle, prompt.toByteArray(Charsets.UTF_8), config.outputTokens, constrainedJson)
        // The first 16 bytes contain big-endian token count and decode nanoseconds.
        check(result.size >= 16)
        val header = java.nio.ByteBuffer.wrap(result, 0, 16)
        val metrics = GenerationMetrics(header.long.toInt(), header.long)
        return Completion(result.copyOfRange(16, result.size).toString(Charsets.UTF_8), metrics)
    }

    @Synchronized override fun cancel() {
        if (handle != 0L) nativeCancel(handle)
    }

    @Synchronized override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }
    private external fun nativeLoad(path: String, contextTokens: Int): Long
    private external fun nativeComplete(handle: Long, prompt: ByteArray, outputTokens: Int, constrainedJson: Boolean): ByteArray
    private external fun nativeGenerate(handle: Long, prompt: String): LongArray
    private external fun nativeCancel(handle: Long)
    private external fun nativeClose(handle: Long)
    companion object {
        init {
            System.loadLibrary("wend_llama")
        }
    }
}
