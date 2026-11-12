package com.jinatra.wend.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

@OptIn(ExperimentalApi::class)
internal class LiteRtEngine(private val gpu: Boolean, private val cache: File, private val config: InferenceConfig) : LlmEngine {
    private var engine: Engine? = null

    @Volatile private var conversation: Conversation? = null

    override fun load(model: File) {
        check(engine == null)
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        ExperimentalFlags.enableBenchmark = true
        val instance = Engine(EngineConfig(modelPath = model.path, backend = if (gpu) Backend.GPU() else Backend.CPU(threadCount = 4), maxNumTokens = config.contextTokens, cacheDir = cache.path))
        engine = instance
        instance.initialize()
    }

    override fun generate(prompt: String): GenerationMetrics = run(prompt, false).metrics

    override fun complete(prompt: String, constrainedJson: Boolean): Completion = run(prompt, constrainedJson)

    private fun run(prompt: String, constrainedJson: Boolean): Completion {
        val instance = checkNotNull(engine)
        return instance.createConversation(ConversationConfig(maxOutputToken = config.outputTokens, samplerConfig = SamplerConfig(1, 1.0, 0.0), enableResponseFormat = constrainedJson)).use { current ->
            conversation = current
            try {
                val response = current.sendMessage(prompt, responseFormat = if (constrainedJson) ResponseFormat.json("""{"type":"object"}""") else null)
                val metrics = current.getBenchmarkInfo()
                val tokens = metrics.lastDecodeTokenCount
                val rate = metrics.lastDecodeTokensPerSecond
                check(tokens > 0 && rate.isFinite() && rate > 0)
                Completion(response.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }, GenerationMetrics(tokens, (tokens / rate * 1_000_000_000).toLong()))
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
