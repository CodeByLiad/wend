package com.jinatra.wend.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

@OptIn(ExperimentalApi::class)
internal class LiteRtEngine(private val gpu: Boolean, private val cache: File) : LlmEngine {
    private var engine: Engine? = null

    @Volatile private var conversation: Conversation? = null

    override fun load(model: File) {
        check(engine == null)
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        ExperimentalFlags.enableBenchmark = true
        val instance = Engine(EngineConfig(modelPath = model.path, backend = if (gpu) Backend.GPU() else Backend.CPU(threadCount = 4), maxNumTokens = 2048, cacheDir = cache.path))
        engine = instance
        instance.initialize()
    }

    override fun generate(prompt: String): GenerationMetrics {
        val instance = checkNotNull(engine)
        return instance.createConversation(ConversationConfig(maxOutputToken = 128, samplerConfig = SamplerConfig(1, 1.0, 0.0))).use { current ->
            conversation = current
            try {
                current.sendMessage(prompt)
                val metrics = current.getBenchmarkInfo()
                val tokens = metrics.lastDecodeTokenCount
                val rate = metrics.lastDecodeTokensPerSecond
                check(tokens > 0 && rate.isFinite() && rate > 0)
                GenerationMetrics(tokens, (tokens / rate * 1_000_000_000).toLong())
            } finally {
                conversation = null
            }
        }
    }

    override fun cancel() {
        conversation?.cancelProcess()
    }
    override fun close() {
        val instance = engine
        engine = null
        if (instance?.isInitialized() == true) instance.close()
    }
}
