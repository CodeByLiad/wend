package com.jinatra.wend.benchmark

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinatra.wend.ai.RuntimeBackend
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var benchmark: BenchmarkViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val model: BenchmarkViewModel = viewModel()
            benchmark = model
            val state by model.state.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importModel) }
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                BenchmarkScreen(
                    state,
                    model::select,
                    { picker.launch(arrayOf("*/*")) },
                    model::start,
                    model::stop,
                    { export.launch("wend-benchmark-${state.result?.id}.json") },
                )
            }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) benchmark?.stop()
        super.onStop()
    }
}

@Composable
fun BenchmarkScreen(
    state: BenchmarkState,
    select: (RuntimeBackend) -> Unit,
    import: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    export: () -> Unit,
) {
    Surface {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.description))
            RuntimeBackend.entries.forEach { backend ->
                val resource = when (backend) {
                    RuntimeBackend.LITERT_CPU -> R.string.runtime_litert_cpu
                    RuntimeBackend.LITERT_GPU -> R.string.runtime_litert_gpu
                    RuntimeBackend.LLAMA_CPU -> R.string.runtime_llama
                }
                Row(Modifier.fillMaxWidth().selectable(selected = state.backend == backend, enabled = !state.busy, role = Role.RadioButton) { select(backend) }) {
                    RadioButton(state.backend == backend, null, enabled = !state.busy)
                    Text(stringResource(resource), modifier = Modifier.padding(top = 12.dp))
                }
            }
            Text(stringResource(R.string.model_help))
            state.model?.let { Text(stringResource(R.string.model_ready, it.bytes / (1024 * 1024))) }
            OutlinedButton(import, enabled = !state.busy) { Text(stringResource(R.string.import_model)) }
            if (state.busy) {
                CircularProgressIndicator()
                Text(
                    when {
                        state.stopping -> stringResource(R.string.stopping)
                        state.loading -> stringResource(R.string.loading)
                        else -> stringResource(R.string.progress, state.elapsedMillis / 1000)
                    },
                )
                OutlinedButton(stop, enabled = !state.stopping) { Text(stringResource(R.string.stop)) }
            } else {
                Button(start, enabled = state.model != null) { Text(stringResource(R.string.start)) }
            }
            state.errorResource?.let { Text(stringResource(it)) }
            state.result?.let { result ->
                Text(
                    stringResource(
                        when (result.outcome) {
                            RunOutcome.COMPLETED -> R.string.completed
                            RunOutcome.CANCELLED -> R.string.cancelled
                            RunOutcome.FAILED -> R.string.failed
                        },
                    ),
                )
                val unavailable = stringResource(R.string.unavailable)
                fun Double?.display() = this?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: unavailable
                Text(
                    stringResource(
                        R.string.result,
                        result.loadNanos / 1e9,
                        result.tokensPerSecond.display(),
                        result.peakPssKb / 1024,
                        result.peakBatteryCelsius.display(),
                        result.drainPercentagePoints?.toString() ?: unavailable,
                        result.completedGenerations,
                    ),
                )
            }
            if (state.report != null) OutlinedButton(export, enabled = !state.busy) { Text(stringResource(R.string.export)) }
            Text(stringResource(R.string.measurement_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LightPreview() {
    MaterialTheme { BenchmarkScreen(BenchmarkState(), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) { BenchmarkScreen(BenchmarkState(), {}, {}, {}, {}, {}) }
}
