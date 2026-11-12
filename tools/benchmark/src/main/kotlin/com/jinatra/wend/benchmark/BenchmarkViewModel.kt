package com.jinatra.wend.benchmark

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jinatra.wend.ai.ImportedModel
import com.jinatra.wend.ai.LlmEngine
import com.jinatra.wend.ai.LlmEngines
import com.jinatra.wend.ai.ModelImport
import com.jinatra.wend.ai.RuntimeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class BenchmarkState(
    val backend: RuntimeBackend = RuntimeBackend.LITERT_CPU,
    val model: ImportedModel? = null,
    val busy: Boolean = false,
    val loading: Boolean = false,
    val stopping: Boolean = false,
    val elapsedMillis: Long = 0,
    val result: BenchmarkResult? = null,
    val errorResource: Int? = null,
    val report: String? = null,
)

class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(BenchmarkState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var engine: LlmEngine? = null
    private var disposed = false
    private val app get() = getApplication<Application>()

    fun select(backend: RuntimeBackend) {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(backend = backend)
    }

    fun importModel(uri: Uri) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, loading = true, errorResource = null)
        job = viewModelScope.launch {
            try {
                val imported = ModelImport.copy(app, uri)
                mutable.value.model?.file?.delete()
                mutable.value = mutable.value.copy(model = imported, result = null, report = null)
            } catch (_: CancellationException) {
                // The importer removes its partial file.
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(errorResource = R.string.import_failed)
            } finally {
                mutable.value = mutable.value.copy(busy = false, loading = false, stopping = false)
            }
        }
    }

    fun start() {
        val selected = mutable.value
        val model = selected.model ?: return
        if (selected.busy) return
        mutable.value = selected.copy(busy = true, loading = true, stopping = false, elapsedMillis = 0, errorResource = null, result = null, report = null)
        job = viewModelScope.launch {
            val cache = File(app.cacheDir, "benchmark-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val current = LlmEngines.create(selected.backend, cache)
                engine = current
                val measurements = AndroidMeasurements(app)
                val result = BenchmarkRunner(measurements).run(current, model.file, app.getString(R.string.benchmark_prompt)) { elapsed ->
                    mutable.value = mutable.value.copy(loading = false, elapsedMillis = elapsed)
                }
                val report = reportJson(result, model, selected.backend, measurements.device()).toString(2)
                withContext(NonCancellable + Dispatchers.IO) {
                    val directory = File(app.filesDir, "results").apply { mkdirs() }
                    File(directory, "${result.id}.json").writeText(report)
                }
                mutable.value = mutable.value.copy(result = result, report = report)
            } catch (_: CancellationException) {
                // Run cleanup completes before the UI becomes available again.
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(errorResource = R.string.failed)
            } catch (_: LinkageError) {
                mutable.value = mutable.value.copy(errorResource = R.string.failed)
            } finally {
                engine = null
                withContext(NonCancellable + Dispatchers.IO) { cache.deleteRecursively() }
                mutable.value = mutable.value.copy(busy = false, loading = false, stopping = false)
                if (disposed) model.file.delete()
            }
        }
    }

    fun stop() {
        if (!mutable.value.busy) return
        mutable.value = mutable.value.copy(stopping = true)
        engine?.cancel()
        job?.cancel()
    }

    fun export(uri: Uri) {
        val report = mutable.value.report ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requireNotNull(app.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) } }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(errorResource = R.string.export_failed)
            }
        }
    }

    override fun onCleared() {
        disposed = true
        stop()
        if (!mutable.value.busy) mutable.value.model?.file?.delete()
    }
}

private fun reportJson(result: BenchmarkResult, model: ImportedModel, backend: RuntimeBackend, device: JSONObject): JSONObject {
    fun Telemetry?.json(): Any = this?.let {
        JSONObject().put("pssKb", pssKb).put("batteryPercent", batteryPercent ?: JSONObject.NULL)
            .put("chargeMicroAh", chargeMicroAh ?: JSONObject.NULL).put("batteryCelsius", batteryCelsius ?: JSONObject.NULL)
            .put("charging", charging).put("thermalStatus", thermalStatus ?: JSONObject.NULL)
    } ?: JSONObject.NULL
    return JSONObject().put("schemaVersion", 1).put("id", result.id).put("createdAt", result.createdAt).put("updatedAt", result.updatedAt)
        .put("outcome", result.outcome.name).put("device", device).put("backend", backend.name)
        .put("runtimeRevision", if (backend == RuntimeBackend.LLAMA_CPU) "93cf736a8d1d46aa922e27eb64aa712bcd5ebc30" else "0.19.1")
        .put("model", JSONObject().put("id", model.id).put("sha256", model.sha256).put("bytes", model.bytes))
        .put(
            "configuration",
            JSONObject().put("requestedContextTokens", 2048).put("maxOutputTokens", 128).put("cpuThreads", if (backend == RuntimeBackend.LITERT_GPU) JSONObject.NULL else 4)
                .put("sampling", "greedy").put("requestedDurationMillis", RUN_MILLIS).put("memorySampleMillis", SAMPLE_MILLIS)
                .put("osFileCacheControlled", false).put("temperatureSensor", "battery").put("promptId", "organize-day-v1"),
        )
        .put("loadNanos", result.loadNanos).put("runNanos", result.runNanos).put("outputTokens", result.outputTokens)
        .put("decodeNanos", result.decodeNanos).put("decodeTokensPerSecond", result.tokensPerSecond ?: JSONObject.NULL)
        .put("completedGenerations", result.completedGenerations).put("sampledPeakPssKb", result.peakPssKb)
        .put("peakBatteryCelsius", result.peakBatteryCelsius ?: JSONObject.NULL).put("sampleCount", result.sampleCount)
        .put("chargingObserved", result.chargingObserved).put("drainMicroAh", result.drainMicroAh ?: JSONObject.NULL)
        .put("drainPercentagePoints", result.drainPercentagePoints ?: JSONObject.NULL)
        .put("batteryStart", result.batteryStart.json()).put("batteryEnd", result.batteryEnd.json())
}
