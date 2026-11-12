package com.jinatra.wend.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.eval.Corpus
import com.jinatra.wend.eval.EvaluationSession
import com.jinatra.wend.eval.Evaluator
import com.jinatra.wend.eval.Json
import com.jinatra.wend.eval.TextGenerator
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Explicit opt-in only. Reports and models remain in this temporary test package's private files. */
@RunWith(AndroidJUnit4::class)
class PlannerEvaluationTest {
    @Test fun frozenCorpusAndScorerLoadOnAndroid() {
        val corpus = Corpus.load()
        assertEquals(150, corpus.cases.size)
        assertEquals(emptyList<String>(), corpus.validate())
    }

    @Test fun evaluateImportedCandidate() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("No candidate selected; no inference performed", arguments.getString("model") != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = requireNotNull(arguments.getString("model"))
        require(name.matches(Regex("[a-zA-Z0-9._-]+")) && name != "." && name != "..")
        val model = File(File(context.filesDir, "models"), name)
        require(model.isFile)
        val expectedHash = requireNotNull(arguments.getString("sha256"))
        require(expectedHash.matches(Regex("[a-f0-9]{64}")))
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(hash == expectedHash) { "Model checksum mismatch" }
        val backend = RuntimeBackend.valueOf(requireNotNull(arguments.getString("backend")))
        val revision = requireNotNull(arguments.getString("gitRevision"))
        require(revision.matches(Regex("[a-f0-9]{40}")))
        val sourceRevision = requireNotNull(arguments.getString("modelRevision"))
        require(sourceRevision.matches(Regex("[a-f0-9]{40}")))
        val corpus = Corpus.load()
        val runId = UUID.randomUUID().toString()
        val createdAt = Instant.now().toString()
        val output = File(context.filesDir, "evaluation/$runId.json").apply { parentFile!!.mkdirs() }
        val config = InferenceConfig(4096, 512)
        val engine = LlmEngines.create(backend, context.cacheDir, config)
        val worker = Executors.newSingleThreadExecutor()
        var usable = true
        val loadStarted = System.nanoTime()
        try {
            engine.load(model)
            val loadNanos = System.nanoTime() - loadStarted
            val memory = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val metadata = mapOf(
                "id" to runId, "createdAt" to createdAt, "gitRevision" to revision,
                "kind" to "DEVICE_EVALUATION",
                "modelRevision" to sourceRevision, "modelSha256" to hash, "backend" to backend.name,
                "runtime" to if (backend == RuntimeBackend.LLAMA_CPU) "llama.cpp 93cf736a8d1d46aa922e27eb64aa712bcd5ebc30" else "LiteRT-LM 0.19.1",
                "contextTokens" to config.contextTokens, "outputTokens" to config.outputTokens,
                "constraints" to "JSON_OBJECT_SYNTAX", "loadNanos" to loadNanos,
                "conditionsAtStart" to mapOf("plugged" to battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1), "batteryTemperatureTenthsCelsius" to battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)),
                "device" to mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "model" to Build.MODEL,
                    "sdk" to Build.VERSION.SDK_INT,
                    "totalRamBytes" to memory.totalMem,
                    "soc" to if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
                ),
            )
            EvaluationSession.run(
                corpus,
                TextGenerator { prompt ->
                    check(usable) { "Runtime did not stop after timeout" }
                    val future = worker.submit(Callable { engine.complete(prompt, true).text })
                    try {
                        future.get(30, TimeUnit.SECONDS)
                    } catch (_: TimeoutException) {
                        engine.cancel()
                        try {
                            future.get(10, TimeUnit.SECONDS)
                        } catch (_: TimeoutException) {
                            usable = false
                        } catch (_: Exception) { /* stopped */ }
                        throw IllegalStateException("Inference timed out")
                    }
                },
            ) { attempts ->
                val report = metadata + mapOf("updatedAt" to Instant.now().toString(), "results" to Evaluator.score(corpus, attempts).json())
                val temporary = File(output.parentFile, "$runId.tmp")
                temporary.writeText(Json.stringify(report) + "\n")
                check(temporary.renameTo(output)) { "Cannot save evaluation checkpoint" }
                check(usable) { "Runtime cancellation failed; run remains partial" }
            }
        } finally {
            engine.cancel()
            worker.shutdown()
            if (worker.awaitTermination(10, TimeUnit.SECONDS)) engine.close()
        }
    }
}
