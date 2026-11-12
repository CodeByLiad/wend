package com.jinatra.wend.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
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
