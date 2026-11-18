package com.jinatra.wend.companion

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinatra.wend.ai.RuntimeBackend

class MainActivity : ComponentActivity() {
    private var probe: ProbeViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val model: ProbeViewModel = viewModel()
            probe = model
            val state by model.state.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importModel) }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                ProbeScreen(
                    state, model::send, model::addFact, model::removeFact, model::select,
                    { picker.launch(arrayOf("*/*")) }, model::removeModel, model::stop, model::deleteData, model::updateMessageDraft, model::updateFactDraft, model::selectTab,
                )
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) probe?.stop()
        super.onStop()
    }
}

@Composable
fun ProbeScreen(
    state: ProbeState,
    send: (String) -> Unit,
    addFact: (String) -> Unit,
    removeFact: (String) -> Unit,
    select: (RuntimeBackend) -> Unit,
    import: () -> Unit,
    removeModel: () -> Unit,
    stop: () -> Unit,
    deleteData: () -> Unit,
    changeMessageDraft: (String) -> Unit,
    changeFactDraft: (String) -> Unit,
    selectTab: (Int) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Surface {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.chat, R.string.facts, R.string.model).forEachIndexed { index, label ->
                    FilterChip(selected = state.tab == index, onClick = { selectTab(index) }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(if (state.work == ProbeWork.CHAT) R.string.replying else R.string.working))
                if (state.work == ProbeWork.CHAT || state.work == ProbeWork.IMPORT) TextButton(stop) { Text(stringResource(R.string.stop)) }
            }
            when (state.tab) {
                0 -> {
                    if (state.model == null) TextButton({ selectTab(2) }) { Text(stringResource(R.string.choose_model)) }
                    ChatPanel(state, send, changeMessageDraft, Modifier.fillMaxWidth())
                }

                1 -> FactsPanel(state, addFact, removeFact, changeFactDraft, Modifier.fillMaxWidth())

                2 -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.model_help))
                    RuntimeBackend.entries.forEach { backend ->
                        val label = when (backend) {
                            RuntimeBackend.LITERT_CPU -> R.string.litert_cpu
                            RuntimeBackend.LITERT_GPU -> R.string.litert_gpu
                            RuntimeBackend.LLAMA_CPU -> R.string.llama_cpu
                        }
                        FilterChip(
                            selected = state.backend == backend,
                            onClick = { select(backend) },
                            enabled = !state.busy,
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    state.model?.let { Text(stringResource(R.string.model_ready, it.bytes / (1024 * 1024))) }
                    Button(import, enabled = !state.busy) { Text(stringResource(R.string.import_model)) }
                    if (state.model != null) OutlinedButton(removeModel, enabled = !state.busy) { Text(stringResource(R.string.remove_model)) }
                    Text(stringResource(R.string.context_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton({ confirmDelete = true }, enabled = !state.busy) { Text(stringResource(R.string.delete_data)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_data)) },
            text = { Text(stringResource(R.string.delete_explanation)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    deleteData()
                }) { Text(stringResource(R.string.delete_confirm)) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.keep_data)) } },
        )
    }
}

@Composable
private fun ChatPanel(state: ProbeState, send: (String) -> Unit, changeDraft: (String) -> Unit, modifier: Modifier) {
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.data.messages.size) {
        if (state.data.messages.isNotEmpty()) list.animateScrollToItem(state.data.messages.lastIndex)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp), state = list, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.data.messages.isEmpty()) item { Text(stringResource(R.string.empty_chat)) }
            items(state.data.messages, key = { it.id }) { message ->
                Column {
                    Text(stringResource(if (message.speaker == Speaker.USER) R.string.you else R.string.companion), style = MaterialTheme.typography.labelLarge)
                    Text(message.text)
                }
            }
        }
        if (state.omittedMessages > 0) Text(stringResource(R.string.context_trimmed), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            state.messageDraft,
            changeDraft,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !state.busy && !state.unreadable,
            label = { Text(stringResource(R.string.message)) },
        )
        Button({ send(state.messageDraft) }, enabled = !state.busy && !state.unreadable && state.model != null && state.messageDraft.isNotBlank()) {
            Text(stringResource(R.string.send))
        }
    }
}

@Composable
private fun FactsPanel(state: ProbeState, addFact: (String) -> Unit, removeFact: (String) -> Unit, changeDraft: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.facts_explanation))
        if (state.data.facts.isEmpty()) Text(stringResource(R.string.empty_facts))
        state.data.facts.forEach { fact ->
            Column {
                Text(fact.text)
                TextButton({ removeFact(fact.id) }, enabled = !state.busy && !state.unreadable) { Text(stringResource(R.string.remove_fact)) }
                HorizontalDivider()
            }
        }
        OutlinedTextField(
            state.factDraft,
            changeDraft,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !state.busy && !state.unreadable && state.data.facts.size < MAX_FACTS,
            label = { Text(stringResource(R.string.fact)) },
        )
        Button({ addFact(state.factDraft) }, enabled = !state.busy && !state.unreadable && state.factDraft.isNotBlank() && state.data.facts.size < MAX_FACTS) {
            Text(stringResource(R.string.save_fact))
        }
        Text(stringResource(R.string.fact_limit), style = MaterialTheme.typography.bodySmall)
    }
}

@Preview
@Composable
private fun LightPreview() {
    MaterialTheme { ProbeScreen(ProbeState(work = ProbeWork.IDLE), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) { ProbeScreen(ProbeState(work = ProbeWork.IDLE), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
