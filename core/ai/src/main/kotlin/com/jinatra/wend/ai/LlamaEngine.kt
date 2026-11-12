package com.jinatra.wend.ai

import java.io.File

internal class LlamaEngine : LlmEngine {
    @Volatile private var handle = 0L
    override fun load(model: File) {
        check(handle == 0L)
        handle = nativeLoad(model.path)
        check(handle != 0L)
    }
    override fun generate(prompt: String): GenerationMetrics {
        val result = nativeGenerate(handle, prompt)
        return GenerationMetrics(result[0].toInt(), result[1])
    }

    @Synchronized override fun cancel() {
        if (handle != 0L) nativeCancel(handle)
    }

    @Synchronized override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }
    private external fun nativeLoad(path: String): Long
    private external fun nativeGenerate(handle: Long, prompt: String): LongArray
    private external fun nativeCancel(handle: Long)
    private external fun nativeClose(handle: Long)
    companion object {
        init {
            System.loadLibrary("wend_llama")
        }
    }
}
