package com.jinatra.wend.companion

import android.app.Application
import android.content.ContextWrapper
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinatra.wend.ai.Completion
import com.jinatra.wend.ai.GenerationMetrics
import com.jinatra.wend.ai.LlmEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProbeStorageTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun fixture(test: (EncryptedProbeStore, File, String) -> Unit) {
        val root = File(app.noBackupFilesDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val alias = "wend-probe-test-" + UUID.randomUUID()
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = root
        }
        val store = EncryptedProbeStore(context, alias)
        try {
            test(store, root, alias)
        } finally {
            store.delete()
            root.deleteRecursively()
        }
    }

    @Test fun encryptedSnapshotsSurviveRecreationAndUseFreshNonces() = fixture { store, root, alias ->
        val data = ProbeData(
            listOf(ProbeMessage(UUID.randomUUID().toString(), 1, 1, Speaker.USER, "Private message sentinel")),
            listOf(ProbeFact(UUID.randomUUID().toString(), 1, 1, "Private fact sentinel")),
        )
        store.write(data)
        val file = File(root, "companion-v1.enc")
        val first = file.readBytes()
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("Private"))
        assertEquals(
            data,
            EncryptedProbeStore(
                object : ContextWrapper(app) {
                    override fun getNoBackupFilesDir() = root
                },
                alias,
            ).read(),
        )
        store.write(data)
        assertFalse(first.contentEquals(file.readBytes()))
        assertEquals(
            data,
            EncryptedProbeStore(
                object : ContextWrapper(app) {
                    override fun getNoBackupFilesDir() = root
                },
                alias,
            ).read(),
        )
        assertFalse(File(root, "companion-v1.enc.bak").exists())
    }

    @Test fun tamperingFailsClosedAndLeavesTheOriginalFileUntouched() = fixture { store, root, _ ->
        store.write(ProbeData())
        val file = File(root, "companion-v1.enc")
        val damaged = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(damaged)
        var rejected = false
        try {
            store.read()
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun deletingDataRemovesCiphertextAndKeyAndAllowsAFreshStart() = fixture { store, root, alias ->
        store.write(ProbeData())
        store.delete()
        assertTrue(root.listFiles().orEmpty().isEmpty())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        assertEquals(ProbeData(), store.read())
        store.write(ProbeData())
        assertEquals(ProbeData(), store.read())
    }

    @Test fun viewModelPersistsFactsAndRepliesAndUnloadsItsRuntime() = fixture { store, _, _ ->
        val source = File.createTempFile("fake-probe-model", ".gguf", app.cacheDir).apply { writeText("GGUFfixture") }
        val received = mutableListOf<String>()
        var closed = 0
        val owner = ViewModelStore()
        lateinit var model: ProbeViewModel
        try {
            main {
                model = ViewModelProvider(
                    owner,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ProbeViewModel(app, store) { _, _ ->
                            object : LlmEngine {
                                override fun load(model: File) {
                                    check(model.isFile)
                                }
                                override fun generate(prompt: String) = error("Not used")
                                override fun complete(prompt: String, constrainedJson: Boolean): Completion {
                                    check(!constrainedJson)
                                    received.add(prompt)
                                    return Completion("A synthetic reply", GenerationMetrics(3, 1_000_000))
                                }
                                override fun cancel() = Unit
                                override fun close() {
                                    closed++
                                }
                            }
                        } as T
                    },
                )[ProbeViewModel::class.java]
            }
            idle(model)
            main { model.addFact("I enjoy walking") }
            idle(model)
            main { model.importModel(Uri.fromFile(source)) }
            idle(model)
            main { model.send("Hello") }
            idle(model)
            assertEquals(listOf("Hello", "A synthetic reply"), store.read().messages.map { it.text })
            assertTrue(received.single().contains("I enjoy walking"))
            assertEquals(1, closed)
            val imported = requireNotNull(model.state.value.model).file
            main { owner.clear() }
            assertFalse(imported.exists())
            assertEquals(2, store.read().messages.size)
        } finally {
            main { owner.clear() }
            source.delete()
        }
    }

    @Test fun unreadableDataCannotBeOverwrittenByFactEdits() = fixture { store, root, alias ->
        store.write(ProbeData())
        val original = File(root, "companion-v1.enc").readBytes()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
        val owner = ViewModelStore()
        lateinit var model: ProbeViewModel
        main {
            model = ViewModelProvider(
                owner,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ProbeViewModel(app, store) as T
                },
            )[ProbeViewModel::class.java]
        }
        try {
            idle(model)
            assertTrue(model.state.value.unreadable)
            main { model.addFact("Must not overwrite") }
            assertArrayEquals(original, File(root, "companion-v1.enc").readBytes())
            main { model.deleteData() }
            idle(model)
            assertFalse(model.state.value.unreadable)
            assertEquals(ProbeData(), store.read())
        } finally {
            main { owner.clear() }
        }
    }

    @Test fun cancellationRetainsTheUserMessageButNeverCommitsADiscardedReply() = fixture { store, _, _ ->
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val source = File.createTempFile("cancel-probe", ".gguf", app.cacheDir).apply { writeText("GGUFfixture") }
        val owner = ViewModelStore()
        lateinit var model: ProbeViewModel
        main {
            model = ViewModelProvider(
                owner,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ProbeViewModel(app, store) { _, _ ->
                        object : LlmEngine {
                            override fun load(model: File) = Unit
                            override fun generate(prompt: String) = error("Not used")
                            override fun complete(prompt: String, constrainedJson: Boolean): Completion {
                                entered.countDown()
                                check(cancelled.await(10, TimeUnit.SECONDS))
                                return Completion("Discard this reply", GenerationMetrics(3, 1_000_000))
                            }
                            override fun cancel() {
                                cancelled.countDown()
                            }
                            override fun close() {
                                closed.countDown()
                            }
                        }
                    } as T
                },
            )[ProbeViewModel::class.java]
        }
        try {
            idle(model)
            main { model.importModel(Uri.fromFile(source)) }
            idle(model)
            main { model.send("Keep this request") }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            main { model.stop() }
            idle(model)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            assertEquals(listOf("Keep this request"), store.read().messages.map { it.text })
            assertEquals(R.string.reply_cancelled, model.state.value.error)
        } finally {
            cancelled.countDown()
            main { owner.clear() }
            source.delete()
        }
    }

    @Test fun draftsAndSectionSurviveTabChangesAndActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: ProbeViewModel
            scenario.onActivity { model = ViewModelProvider(it)[ProbeViewModel::class.java] }
            idle(model)
            scenario.onActivity {
                model.updateMessageDraft("Unsent message")
                model.selectTab(1)
                model.updateFactDraft("Unsent fact")
                model.selectTab(0)
            }
            assertEquals("Unsent message", model.state.value.messageDraft)
            assertEquals("Unsent fact", model.state.value.factDraft)
            scenario.onActivity { model.selectTab(1) }
            scenario.recreate()
            scenario.onActivity {
                model = ViewModelProvider(it)[ProbeViewModel::class.java]
                assertEquals(1, model.state.value.tab)
                assertEquals("Unsent message", model.state.value.messageDraft)
                assertEquals("Unsent fact", model.state.value.factDraft)
                model.updateMessageDraft("")
                model.updateFactDraft("")
            }
        }
    }

    @Test fun failedSavesKeepTheDraftForRetry() {
        val failingStore = object : ProbeStore {
            override fun read() = ProbeData()
            override fun write(data: ProbeData): Unit = throw java.io.IOException("Synthetic write failure")
            override fun delete() = Unit
        }
        val owner = ViewModelStore()
        val source = File.createTempFile("save-failure-probe", ".gguf", app.cacheDir).apply { writeText("GGUFfixture") }
        lateinit var model: ProbeViewModel
        main {
            model = ViewModelProvider(
                owner,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ProbeViewModel(app, failingStore) as T
                },
            )[ProbeViewModel::class.java]
        }
        try {
            idle(model)
            main {
                model.updateFactDraft("Keep this fact draft")
                model.addFact(model.state.value.factDraft)
            }
            idle(model)
            assertEquals(R.string.save_failed, model.state.value.error)
            assertEquals("Keep this fact draft", model.state.value.factDraft)
            assertTrue(model.state.value.data.facts.isEmpty())
            main { model.importModel(Uri.fromFile(source)) }
            idle(model)
            main {
                model.updateMessageDraft("Keep this message draft")
                model.send(model.state.value.messageDraft)
            }
            idle(model)
            assertEquals(R.string.reply_failed, model.state.value.error)
            assertEquals("Keep this message draft", model.state.value.messageDraft)
            assertTrue(model.state.value.data.messages.isEmpty())
        } finally {
            main { owner.clear() }
            source.delete()
        }
    }

    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun idle(model: ProbeViewModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (model.state.value.busy) {
            check(System.nanoTime() < deadline) { "Probe did not return to idle" }
            Thread.sleep(20)
        }
    }
}
