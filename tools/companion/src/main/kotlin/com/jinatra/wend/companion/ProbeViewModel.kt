package com.jinatra.wend.companion

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jinatra.wend.ai.ImportedModel
import com.jinatra.wend.ai.InferenceConfig
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
import java.io.File
import java.util.UUID

enum class ProbeWork { IDLE, STORAGE, IMPORT, CHAT }

data class ProbeState(
    val data: ProbeData = ProbeData(),
    val work: ProbeWork = ProbeWork.STORAGE,
    val model: ImportedModel? = null,
    val backend: RuntimeBackend = RuntimeBackend.LITERT_CPU,
    val unreadable: Boolean = false,
    val error: Int? = null,
    val omittedMessages: Int = 0,
    val messageDraft: String = "",
    val factDraft: String = "",
    val tab: Int = 0,
) {
    val busy get() = work != ProbeWork.IDLE
}

class ProbeViewModel @JvmOverloads constructor(
    application: Application,
    private val store: ProbeStore = EncryptedProbeStore(application),
    private val factory: (RuntimeBackend, File) -> LlmEngine = { backend, cache -> LlmEngines.create(backend, cache, InferenceConfig(4096, 256)) },
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ProbeState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    @Volatile private var engine: LlmEngine? = null
    private var disposed = false
    private val app get() = getApplication<Application>()

    init {
        job = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    // Previous process death may leave an unclaimed model import. This
                    // directory belongs only to the disposable probe, not user documents.
                    File(app.filesDir, "models").listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
                    store.read()
                }
                mutable.value = mutable.value.copy(data = data)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(unreadable = true, error = R.string.read_failed)
            } finally {
                mutable.value = mutable.value.copy(work = ProbeWork.IDLE)
            }
        }
    }

    fun selectTab(tab: Int) {
        require(tab in 0..2)
        mutable.value = mutable.value.copy(tab = tab)
    }

    fun updateMessageDraft(value: String) {
        if (!mutable.value.busy && !mutable.value.unreadable && value.length <= MAX_MESSAGE) mutable.value = mutable.value.copy(messageDraft = value)
    }

    fun updateFactDraft(value: String) {
        if (!mutable.value.busy && !mutable.value.unreadable && value.length <= MAX_FACT) mutable.value = mutable.value.copy(factDraft = value)
    }

    fun select(backend: RuntimeBackend) {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(backend = backend)
    }

    fun importModel(uri: Uri) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(work = ProbeWork.IMPORT, error = null)
        job = viewModelScope.launch {
            try {
                val imported = ModelImport.copy(app, uri)
                mutable.value.model?.file?.delete()
                mutable.value = mutable.value.copy(model = imported)
            } catch (_: CancellationException) {
                mutable.value = mutable.value.copy(error = R.string.import_cancelled)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(error = R.string.import_failed)
            } finally {
                mutable.value = mutable.value.copy(work = ProbeWork.IDLE)
                if (disposed) mutable.value.model?.file?.delete()
            }
        }
    }

    private suspend fun persist(data: ProbeData) = withContext(NonCancellable) {
        withContext(Dispatchers.IO) { store.write(data) }
        mutable.value = mutable.value.copy(data = data)
    }

    private fun edit(clearFactDraft: Boolean = false, change: (ProbeData) -> ProbeData) {
        if (mutable.value.busy || mutable.value.unreadable) return
        mutable.value = mutable.value.copy(work = ProbeWork.STORAGE, error = null)
        job = viewModelScope.launch {
            try {
                persist(change(mutable.value.data))
                if (clearFactDraft) mutable.value = mutable.value.copy(factDraft = "")
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(error = R.string.save_failed)
            } finally {
                mutable.value = mutable.value.copy(work = ProbeWork.IDLE)
                if (disposed) mutable.value.model?.file?.delete()
            }
        }
    }

    fun addFact(text: String) {
        val value = text.trim()
        if (value.isBlank() || value.length > MAX_FACT || mutable.value.data.facts.size >= MAX_FACTS) return
        edit(clearFactDraft = true) { data ->
            val now = System.currentTimeMillis()
            data.copy(facts = data.facts + ProbeFact(UUID.randomUUID().toString(), now, now, value))
        }
    }

    fun removeFact(id: String) = edit { data -> data.copy(facts = data.facts.filterNot { it.id == id }) }

    fun deleteData() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(work = ProbeWork.STORAGE, error = null)
        job = viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { store.delete() }
                    mutable.value = mutable.value.copy(data = ProbeData(), unreadable = false, omittedMessages = 0, messageDraft = "", factDraft = "")
                }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(error = R.string.delete_failed)
            } finally {
                mutable.value = mutable.value.copy(work = ProbeWork.IDLE)
            }
        }
    }

    fun removeModel() {
        if (mutable.value.busy) return
        mutable.value.model?.file?.delete()
        mutable.value = mutable.value.copy(model = null)
    }

    fun send(text: String) {
        val selected = mutable.value
        val model = selected.model ?: return
        val value = text.trim()
        if (selected.busy || selected.unreadable || value.isBlank() || value.length > MAX_MESSAGE) return
        if (selected.data.messages.size > MAX_MESSAGES - 2) {
            mutable.value = selected.copy(error = R.string.history_full)
            return
        }
        mutable.value = selected.copy(work = ProbeWork.CHAT, error = null)
        job = viewModelScope.launch {
            val cache = File(app.cacheDir, "probe-" + UUID.randomUUID()).apply { mkdirs() }
            try {
                val now = System.currentTimeMillis()
                val data = selected.data.copy(messages = selected.data.messages + ProbeMessage(UUID.randomUUID().toString(), now, now, Speaker.USER, value))
                persist(data)
                mutable.value = mutable.value.copy(messageDraft = "")
                val prompt = buildProbePrompt(app.getString(R.string.system_prompt), data)
                mutable.value = mutable.value.copy(omittedMessages = prompt.omittedMessages)
                val response = withContext(Dispatchers.IO) {
                    factory(selected.backend, cache).use { current ->
                        engine = current
                        try {
                            current.load(model.file)
                            current.complete(prompt.text, false).text.trim()
                        } finally {
                            engine = null
                        }
                    }
                }
                require(response.isNotBlank() && response.length <= MAX_MESSAGE)
                val replied = System.currentTimeMillis()
                persist(data.copy(messages = data.messages + ProbeMessage(UUID.randomUUID().toString(), replied, replied, Speaker.ASSISTANT, response)))
            } catch (_: CancellationException) {
                mutable.value = mutable.value.copy(error = R.string.reply_cancelled)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(error = R.string.reply_failed)
            } catch (_: LinkageError) {
                mutable.value = mutable.value.copy(error = R.string.reply_failed)
            } finally {
                withContext(NonCancellable) {
                    engine = null
                    withContext(Dispatchers.IO) { cache.deleteRecursively() }
                    mutable.value = mutable.value.copy(work = ProbeWork.IDLE)
                    if (disposed) withContext(Dispatchers.IO) { model.file.delete() }
                }
            }
        }
    }

    fun stop() {
        if (mutable.value.work != ProbeWork.CHAT && mutable.value.work != ProbeWork.IMPORT) return
        engine?.cancel()
        job?.cancel()
    }

    override fun onCleared() {
        disposed = true
        stop()
        if (!mutable.value.busy) mutable.value.model?.file?.delete()
    }
}
