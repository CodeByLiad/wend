package com.jinatra.wend.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jinatra.wend.designsystem.Hairline
import com.jinatra.wend.designsystem.PrimaryButton
import com.jinatra.wend.designsystem.QuietButton
import com.jinatra.wend.designsystem.SerifText
import com.jinatra.wend.designsystem.Space
import com.jinatra.wend.designsystem.TABULAR
import com.jinatra.wend.designsystem.WendTheme
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.Command
import com.jinatra.wend.engine.Minute
import com.jinatra.wend.engine.Proposal
import com.jinatra.wend.engine.ProposalStatus
import com.jinatra.wend.engine.Reason
import com.jinatra.wend.engine.SchedulingEngine
import com.jinatra.wend.engine.Settings
import java.time.LocalDate

/** Stateful entry point: connects [TodayViewModel] to the stateless screen and its sheets. */
@Composable
fun TodayRoute(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : TodayActions {
            override fun runningLate() = viewModel.openRunningLate()

            override fun rebuild() = viewModel.rebuild()

            override fun markDone(id: String) = viewModel.markDone(id)

            override fun openActions(id: String) = viewModel.openActions(id)

            override fun retry(id: String) = viewModel.retry(id)

            override fun undo() = viewModel.undo()

            override fun messageShown(id: Long) = viewModel.messageShown(id)
        }
    }
    TodayScreen(state, actions, modifier)
    when (val sheet = state.sheet) {
        Sheet.RunningLate -> RunningLateSheet(onPick = viewModel::runningLate, onDismiss = viewModel::dismissSheet)

        is Sheet.BlockActions -> state.model.findBlock(sheet.blockId)?.let { block ->
            BlockActionsSheet(
                title = block.title,
                status = block.status,
                isFixed = !block.isMovable,
                onDismiss = viewModel::dismissSheet,
                onStart = { viewModel.start(block.id) },
                onDone = { viewModel.markDone(block.id) },
                onLater = { viewModel.moveLater(block.id, it) },
                onRanOver = { viewModel.ranOver(block.id, it) },
                onSkip = { viewModel.skip(block.id) },
                onFixed = { viewModel.makeFixed(block.id) },
            )
        }

        is Sheet.Review -> ReviewSheet(
            proposal = sheet.proposal,
            canAllowPastBedtime = !sheet.settings.allowPastBedtime,
            onApply = { viewModel.approve(sheet.proposal) },
            onPastBedtime = viewModel::rebuildPastBedtime,
            onDismiss = viewModel::dismissSheet,
        )

        null -> Unit
    }
}

private fun TodayModel.findBlock(id: String) = sections.asSequence().flatMap { it.items }.filterIsInstance<TimelineItem.Row>().map { it.block }.firstOrNull { it.id == id }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WendSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.gutter, end = Space.gutter, bottom = Space.xl),
            content = content,
        )
    }
}

@Composable
fun RunningLateSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    WendSheet(onDismiss) {
        Text(stringResource(R.string.late_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.sm))
        Text(stringResource(R.string.late_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.xl))
        Text(stringResource(R.string.late_unit), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            LATE_OPTIONS.forEach { minutes ->
                val description = stringResource(R.string.late_option, minutes)
                OutlinedButton(
                    onClick = { onPick(minutes) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .semantics { contentDescription = description },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        minutes.toString(),
                        style = MaterialTheme.typography.headlineMedium.cappedScale(1.3f),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val LATE_OPTIONS = listOf(10, 15, 30, 45, 60)

@Composable
fun BlockActionsSheet(
    title: String,
    status: BlockStatus,
    isFixed: Boolean,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: (Int) -> Unit,
    onRanOver: (Int) -> Unit,
    onSkip: () -> Unit,
    onFixed: () -> Unit,
) {
    WendSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.md))
        Hairline()
        if (status == BlockStatus.PLANNED) SheetAction(stringResource(R.string.block_start), onStart)
        SheetAction(stringResource(R.string.action_mark_done), onDone)
        if (status == BlockStatus.IN_PROGRESS) {
            SheetAction(stringResource(R.string.block_ran_over_15)) { onRanOver(15) }
            SheetAction(stringResource(R.string.block_ran_over_30)) { onRanOver(30) }
        } else {
            SheetAction(stringResource(R.string.block_later_30)) { onLater(30) }
            SheetAction(stringResource(R.string.block_later_60)) { onLater(60) }
            if (!isFixed) SheetAction(stringResource(R.string.block_fixed), onFixed)
        }
        if (status == BlockStatus.PLANNED) SheetAction(stringResource(R.string.block_skip), onSkip)
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** The signature interaction: each change set as before and after, with the reason beneath. */
@Composable
fun ReviewSheet(
    proposal: Proposal,
    canAllowPastBedtime: Boolean,
    onApply: () -> Unit,
    onPastBedtime: () -> Unit,
    onDismiss: () -> Unit,
) {
    val time = rememberTimeFormat()
    WendSheet(onDismiss) {
        val (title, body) = when (proposal.status) {
            ProposalStatus.INFEASIBLE -> R.string.review_title_infeasible to R.string.review_body_infeasible
            ProposalStatus.CONFLICT -> R.string.review_title_conflict to R.string.review_body_conflict
            else -> R.string.review_title_ok to R.string.review_body_ok
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.sm))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.lg))
        Hairline()
        proposalLines(proposal).forEach { line ->
            ChangeLineRow(line, time)
            Hairline()
        }
        Spacer(Modifier.height(Space.xl))
        if (proposal.status != ProposalStatus.CONFLICT) {
            PrimaryButton(stringResource(R.string.review_apply), onApply, Modifier.fillMaxWidth())
        }
        if (proposal.status == ProposalStatus.INFEASIBLE && canAllowPastBedtime) {
            Spacer(Modifier.height(Space.sm))
            OutlinedButton(
                onClick = onPastBedtime,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(R.string.review_past_bedtime), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground) }
        }
        Spacer(Modifier.height(Space.xs))
        QuietButton(stringResource(R.string.review_keep), onDismiss, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.review_note),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChangeLineRow(line: ChangeLine, time: TimeFormat) {
    val oldRange = line.before?.let { "${time.format(it.start.value)}–${time.format(it.end.value)}" }
    val newRange = "${time.format(line.after.start.value)}–${time.format(line.after.end.value)}"
    val timesChanged = line.before != null && (line.before.start != line.after.start || line.before.end != line.after.end)
    val showsTimes = line.effect !in setOf(Effect.DROPPED, Effect.UNPLACED, Effect.MISSED, Effect.SKIPPED)
    val wasText = oldRange?.let { stringResource(R.string.review_a11y_was, it) }
    val nowText = stringResource(R.string.review_a11y_now, newRange)
    val effect = effectText(line, time)
    val reason = reasonText(line)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.lg)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(line.title, effect, if (showsTimes && timesChanged) wasText else null, if (showsTimes) nowText else null, reason).joinToString(". ")
            },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(line.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(effect, modifier = Modifier.padding(start = Space.sm, top = Space.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showsTimes) {
            Spacer(Modifier.height(Space.xs))
            Text(
                buildAnnotatedString {
                    if (timesChanged && oldRange != null) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(oldRange) }
                        append("  ")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(newRange) }
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR),
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(reason, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SerifText), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun effectText(line: ChangeLine, time: TimeFormat): String {
    val length = durationText(line.after.length)
    return when (line.effect) {
        Effect.ADDED -> stringResource(R.string.effect_added)
        Effect.RESTORED -> stringResource(R.string.effect_restored)
        Effect.DROPPED -> stringResource(R.string.effect_dropped)
        Effect.UNPLACED -> stringResource(R.string.effect_unplaced)
        Effect.MISSED -> stringResource(R.string.effect_missed)
        Effect.SKIPPED -> stringResource(R.string.effect_skipped)
        Effect.EXTENDED -> stringResource(R.string.effect_extended, time.format(line.after.end.value))
        Effect.MOVED_AND_SHRUNK -> stringResource(R.string.effect_moved_shrunk, length)
        Effect.MOVED -> stringResource(R.string.effect_moved)
        Effect.SHRUNK -> stringResource(R.string.effect_shrunk, length)
        Effect.MADE_FIXED -> stringResource(R.string.effect_made_fixed)
        Effect.RETRIED -> stringResource(R.string.effect_retried)
    }
}

@Composable
private fun reasonText(line: ChangeLine): String {
    val cause = line.causeTitle
    return when (line.reason) {
        Reason.LATE_WAKE -> stringResource(R.string.reason_late_wake)
        Reason.OVERRUN -> stringResource(R.string.reason_overrun)
        Reason.DISPLACED -> if (cause != null) stringResource(R.string.reason_displaced_by, cause) else stringResource(R.string.reason_displaced)
        Reason.FOLLOWS_DEPENDENCY -> if (cause != null) stringResource(R.string.reason_follows, cause) else stringResource(R.string.reason_follows_plain)
        Reason.DEPENDENCY_SHRINK -> if (cause != null) stringResource(R.string.reason_dependency_shrink, cause) else stringResource(R.string.reason_dependency_shrink_plain)
        Reason.SHRUNK_TO_FIT -> stringResource(R.string.reason_shrunk_to_fit)
        Reason.DROPPED_TO_FIT -> stringResource(R.string.reason_dropped_to_fit)
        Reason.NO_ROOM -> stringResource(R.string.reason_no_room)
        Reason.WINDOW_PASSED -> stringResource(R.string.reason_window_passed)
        Reason.FIXED_EXPIRED -> stringResource(R.string.reason_fixed_expired)
        Reason.PAST_BEDTIME -> stringResource(R.string.reason_past_bedtime)
        Reason.BEDTIME_CHANGED -> stringResource(R.string.reason_bedtime_changed)
        Reason.USER_MOVE -> stringResource(R.string.reason_user_move)
        Reason.USER_SKIP -> stringResource(R.string.reason_user_skip)
        Reason.USER_ADD -> stringResource(R.string.reason_user_add)
        Reason.SET_FIXED -> stringResource(R.string.reason_set_fixed)
        Reason.RETRIED -> stringResource(R.string.reason_retried)
        Reason.UNDO -> stringResource(R.string.reason_undo)
    }
}

private fun previewState(): TodayUiState {
    val day = SampleDay.create(LocalDate.of(2026, 11, 13))
    return TodayUiState(LocalDate.of(2026, 11, 13), SampleDay.START_MINUTE, buildTodayModel(day, SampleDay.START_MINUTE), canUndo = false, isSample = true)
}

@Preview(name = "Today, light", widthDp = 393, heightDp = 1400, showBackground = true)
@Preview(name = "Today, dark", widthDp = 393, heightDp = 1400, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview() {
    WendTheme { TodayScreen(previewState(), TodayActions.None) }
}

@Preview(name = "Review, light", widthDp = 393, showBackground = true)
@Preview(name = "Review, dark", widthDp = 393, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReviewLinesPreview() {
    val day = SampleDay.create(LocalDate.of(2026, 11, 13))
    val running = day.blocks.first { it.status == BlockStatus.IN_PROGRESS }
    val proposal = SchedulingEngine.propose(day, Command.Overrun(running.id, Minute(running.end.value + 60)), Minute(SampleDay.START_MINUTE), Settings())
    val time = rememberTimeFormat()
    WendTheme {
        Surface {
            Column(Modifier.padding(Space.gutter)) { proposalLines(proposal).forEach { ChangeLineRow(it, time) } }
        }
    }
}
