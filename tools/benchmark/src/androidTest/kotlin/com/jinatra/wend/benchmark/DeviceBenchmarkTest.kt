package com.jinatra.wend.benchmark

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.ai.RuntimeBackend
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Uses the actual foreground UI and ViewModel, including its normal ten-minute runner. */
@RunWith(AndroidJUnit4::class)
class DeviceBenchmarkTest {
    @Test fun benchmarkVerifiedCandidateForTenMinutes() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("No candidate selected", arguments.getString("model") != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = requireNotNull(arguments.getString("model"))
        require(name.matches(Regex("[a-zA-Z0-9._-]+")) && name != "." && name != "..")
        val source = File(context.filesDir, "models/$name")
        require(source.isFile)
        val expected = requireNotNull(arguments.getString("sha256"))
        require(expected.matches(Regex("[a-f0-9]{64}")))
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Model checksum mismatch" }
        val backend = RuntimeBackend.valueOf(requireNotNull(arguments.getString("backend")))
        val revision = requireNotNull(arguments.getString("gitRevision"))
        val modelRevision = requireNotNull(arguments.getString("modelRevision"))
        require(revision.matches(Regex("[a-f0-9]{40}")) && modelRevision.matches(Regex("[a-f0-9]{40}")))
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        require(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == 0) { "Unplug charging before the battery benchmark" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BenchmarkViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[BenchmarkViewModel::class.java]
                model.select(backend)
                model.importModel(Uri.fromFile(source))
            }
            fun await(timeout: Long, finished: () -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + timeout
                while (!finished()) {
                    check(SystemClock.elapsedRealtime() < deadline) { "Benchmark UI timed out" }
                    SystemClock.sleep(1000)
                }
            }
            try {
                await(120_000) { !model.state.value.busy }
                assertNotNull(model.state.value.model)
                scenario.onActivity { model.start() }
                await(1_200_000) { !model.state.value.busy }
                val state = model.state.value
                val result = requireNotNull(state.result) { "Benchmark did not produce a result" }
                val report = JSONObject(requireNotNull(state.report)).put("gitRevision", revision).put("modelRevision", modelRevision).put("execution", "UI_INSTRUMENTATION")
                File(context.filesDir, "results/${result.id}.json").writeText(report.toString(2))
                assertEquals(RunOutcome.COMPLETED, result.outcome)
                assertTrue(result.runNanos >= RUN_MILLIS * 1_000_000)
                assertTrue(result.outputTokens > 0 && result.completedGenerations > 0 && result.sampleCount > 0)
            } finally {
                scenario.onActivity { model.stop() }
            }
        }
    }
}
