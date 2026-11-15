package com.jinatra.wend.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
    @Test fun cancelledDeliveryRemovesTheCompletedPrivateCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "models")
        val before = directory.listFiles().orEmpty().toSet()
        val source = File.createTempFile("cancel-import", ".bin", context.cacheDir).apply { writeText("LITERTLMfixture") }
        val deliveries = LinkedBlockingQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                deliveries.add(block)
            }
        }
        var delivered = false
        val job = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            ModelImport.copy(context, Uri.fromFile(source))
            delivered = true
        }
        try {
            // Hold the return to the caller until IO has completely copied and renamed it.
            val resume = requireNotNull(deliveries.poll(10, TimeUnit.SECONDS)) { "Import did not finish IO" }
            val copies = directory.listFiles().orEmpty().toSet() - before
            assertEquals(1, copies.size)
            assertEquals("litertlm", copies.single().extension)
            job.cancel()
            resume.run()
            while (!job.isCompleted) {
                requireNotNull(deliveries.poll(10, TimeUnit.SECONDS)) { "Cancellation cleanup did not finish" }.run()
            }
            assertFalse(delivered)
            assertEquals(before, directory.listFiles().orEmpty().toSet())
            assertTrue(source.exists())
        } finally {
            job.cancel()
            while (!job.isCompleted) deliveries.poll(10, TimeUnit.SECONDS)?.run() ?: error("Import cleanup timed out")
            source.delete()
            (directory.listFiles().orEmpty().toSet() - before).forEach { it.delete() }
        }
    }

    @Test fun emptyImportFailsWithoutLeavingAPrivateCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "models")
        val before = directory.listFiles().orEmpty().toSet()
        val source = File.createTempFile("empty-import", ".bin", context.cacheDir)
        try {
            var rejected = false
            try {
                ModelImport.copy(context, Uri.fromFile(source))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertEquals(before, directory.listFiles().orEmpty().toSet())
        } finally {
            source.delete()
        }
    }

    @Test fun importedContainersKeepTheSuffixRequiredByPathBasedRuntimeLoading() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((signature, suffix) in listOf("LITERTLMfixture" to "litertlm", "GGUFfixture" to "gguf")) {
            val source = File(context.cacheDir, "opaque-source.bin").apply { writeText(signature) }
            try {
                val imported = ModelImport.copy(context, Uri.fromFile(source))
                try {
                    assertEquals(suffix, imported.file.extension)
                    assertEquals(signature, imported.file.readText())
                    assertTrue(imported.file.name.startsWith(imported.id))
                } finally {
                    imported.file.delete()
                }
            } finally {
                source.delete()
            }
        }
    }

    @Test fun localImportCopiesAndHashesTheExactBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "import-test").apply { writeText("abc") }
        try {
            val imported = ModelImport.copy(context, Uri.fromFile(source))
            try {
                assertEquals(3L, imported.bytes)
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", imported.sha256)
                assertEquals("abc", imported.file.readText())
                assertFalse(imported.file == source)
            } finally {
                imported.file.delete()
            }
        } finally {
            source.delete()
        }
    }

    @Test fun llamaNativeLibraryLoadsAndRejectsAnInvalidModelWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.cacheDir, "invalid-model.gguf").apply { writeText("not a model") }
        val engine = LlmEngines.create(RuntimeBackend.LLAMA_CPU, context.cacheDir)
        try {
            var rejected = false
            try {
                engine.load(model)
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            engine.close()
            model.delete()
        }
    }

    @Test fun liteRtNativeLibraryLoadsAndRejectsAnInvalidModelWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.cacheDir, "invalid-model.litertlm").apply { writeText("not a model") }
        val engine = LlmEngines.create(RuntimeBackend.LITERT_CPU, context.cacheDir)
        try {
            var rejected = false
            try {
                engine.load(model)
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            engine.close()
            model.delete()
        }
    }
}
