package com.jinatra.wend.benchmark

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelImportLifecycleTest {
    @Test fun cancelledImportIsVisibleAndCanBeRetried() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("import-lifecycle", ".bin", context.cacheDir).apply { writeText("LITERTLMfixture") }
        val directory = File(context.filesDir, "models")
        val before = directory.listFiles().orEmpty().toSet()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: BenchmarkViewModel
                scenario.onActivity {
                    model = ViewModelProvider(it)[BenchmarkViewModel::class.java]
                    model.importModel(Uri.fromFile(source))
                    // Cancel before the main thread can receive even a completed copy.
                    model.stop()
                }
                awaitIdle(model)
                assertNull(model.state.value.model)
                assertEquals(R.string.import_cancelled, model.state.value.errorResource)
                assertEquals(before, directory.listFiles().orEmpty().toSet())

                scenario.onActivity { model.importModel(Uri.fromFile(source)) }
                awaitIdle(model)
                val imported = requireNotNull(model.state.value.model)
                assertNull(model.state.value.errorResource)
                assertEquals("LITERTLMfixture", imported.file.readText())

                scenario.onActivity { model.importModel(Uri.fromFile(File(context.cacheDir, "missing-import-source"))) }
                awaitIdle(model)
                assertEquals(R.string.import_failed, model.state.value.errorResource)
                assertEquals(imported, model.state.value.model)
                assertTrue(imported.file.exists())
                assertFalse(model.state.value.loading)
                assertFalse(model.state.value.stopping)
            }
        } finally {
            source.delete()
            (directory.listFiles().orEmpty().toSet() - before).forEach { it.delete() }
        }
    }

    private fun awaitIdle(model: BenchmarkViewModel) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (model.state.value.busy) {
            check(SystemClock.elapsedRealtime() < deadline) { "Import state did not return to idle" }
            SystemClock.sleep(20)
        }
    }
}
