package com.jinatra.wend.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jinatra.wend.designsystem.Hairline
import com.jinatra.wend.designsystem.PrimaryButton
import com.jinatra.wend.designsystem.QuietButton
import com.jinatra.wend.designsystem.SectionHeading
import com.jinatra.wend.designsystem.SerifText
import com.jinatra.wend.designsystem.Space
import com.jinatra.wend.designsystem.StatColumn
import com.jinatra.wend.designsystem.StatRow
import com.jinatra.wend.designsystem.TABULAR
import com.jinatra.wend.designsystem.VerticalHairline
import com.jinatra.wend.designsystem.Wordmark
import com.jinatra.wend.engine.Block
import com.jinatra.wend.engine.BlockStatus
import com.jinatra.wend.engine.ErrorCode
import com.jinatra.wend.engine.Kind

/** Everything the screen can ask for. Kept as one value so previews can pass a no-op. */
interface TodayActions {
    fun runningLate()

    fun rebuild()

    fun markDone(id: String)

    fun openActions(id: String)

    fun retry(id: String)

    fun undo()

    fun messageShown(id: Long)

    companion object {
        val None = object : TodayActions {
            override fun runningLate() = Unit

            override fun rebuild() = Unit

            override fun markDone(id: String) = Unit

            override fun openActions(id: String) = Unit

            override fun retry(id: String) = Unit

            override fun undo() = Unit

            override fun messageShown(id: Long) = Unit
        }
    }
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val time = rememberTimeFormat()
    MessageEffect(state.message, snackbar, actions)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        // Paper behind the status bar, so the timeline never scrolls under the system icons.
        topBar = {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        },
        bottomBar = { ActionBar(actions) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = padding.calculateTopPadding() + Space.lg,
                bottom = padding.calculateBottomPadding() + Space.section,
            ),
        ) {
            item(key = "header") { Header(state, time, actions) }
            item(key = "hero") { HeroBlock(state.model, time) }
            item(key = "stats") { Stats(state.model) }
            timeline(state.model, time, actions)
            offPlan(state.model.offPlan, time, actions)
        }
    }
}

@Composable
private fun Header(state: TodayUiState, time: TimeFormat, actions: TodayActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(stringResource(R.string.today_wordmark), Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDate(state.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                if (state.isSample) {
                    Text(
                        stringResource(R.string.today_sample_label, time.format(state.now)),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.canUndo) {
            // Undo stays reachable after the snackbar has gone, for the rest of the day.
            QuietButton(
                stringResource(R.string.action_undo_last),
                actions::undo,
                // The button's own padding hangs into the gutter so its text lines up with the date.
                Modifier
                    .align(Alignment.End)
                    .offset(x = Space.lg),
            )
        } else {
            Spacer(Modifier.height(Space.md))
        }
        Hairline()
    }
}

/** The signature: one enormous crimson numeral for the time that matters now. */
@Composable
private fun HeroBlock(model: TodayModel, time: TimeFormat) {
    val hero = model.hero
    val (numeral, label, title, a11y) = when (hero) {
        is Hero.Current -> HeroText(hero.minutesLeft.toString(), stringResource(R.string.hero_left_of), hero.block.title, stringResource(R.string.hero_a11y_left, hero.minutesLeft, hero.block.title))
        is Hero.RunningOver -> HeroText("+${hero.minutesOver}", stringResource(R.string.hero_over_on), hero.block.title, stringResource(R.string.hero_a11y_over, hero.minutesOver, hero.block.title))
        is Hero.UpNext -> HeroText(hero.minutesUntil.toString(), stringResource(R.string.hero_until), hero.block.title, stringResource(R.string.hero_a11y_until, hero.minutesUntil, hero.block.title))
        is Hero.DayDone -> HeroText(hero.doneCount.toString(), stringResource(R.string.hero_done_count), stringResource(R.string.hero_done_body), null)
    }
    Column(
        Modifier
            .padding(top = Space.md, bottom = Space.xl)
            .semantics(mergeDescendants = true) { if (a11y != null) contentDescription = a11y },
    ) {
        Row {
            Text(
                numeral,
                // Cut the numeral's descent so the activity sits tight under it, as one phrase.
                modifier = Modifier
                    .alignByBaseline()
                    .trimBelowBaseline(Space.sm),
                style = MaterialTheme.typography.displayLarge.cappedScale(1.25f),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.width(Space.md))
            Text(
                label,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        model.next?.let { next ->
            Spacer(Modifier.height(Space.sm))
            Text(
                stringResource(R.string.hero_next, next.title, time.format(next.start.value)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class HeroText(val numeral: String, val label: String, val title: String, val a11y: String?)

@Composable
private fun Stats(model: TodayModel) {
    Column {
        Hairline()
        StatRow(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(top = Space.lg),
        ) {
            StatColumn(stringResource(R.string.stat_done), model.done.toString(), Modifier.weight(1f))
            VerticalHairline()
            StatColumn(stringResource(R.string.stat_skipped), model.skipped.toString(), Modifier.weight(1f))
            VerticalHairline()
            StatColumn(stringResource(R.string.stat_left), model.left.toString(), Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.timeline(model: TodayModel, time: TimeFormat, actions: TodayActions) {
    for (section in model.sections) {
        item(key = "part-${section.part}") {
            Column(Modifier.padding(top = Space.lg, bottom = Space.sm)) {
                SectionHeading(stringResource(section.part.label))
            }
        }
        items(section.items, key = { it.key }) { item ->
            when (item) {
                is TimelineItem.Anchor -> AnchorRow(item, time)
                is TimelineItem.Gap -> GapRow(item, time)
                is TimelineItem.Row -> BlockRow(item.block, item.isNow, time, actions)
            }
        }
    }
}

private val Part.label: Int get() = when (this) {
    Part.MORNING -> R.string.part_morning
    Part.AFTERNOON -> R.string.part_afternoon
    Part.EVENING -> R.string.part_evening
}

private val TimelineItem.key: String get() = when (this) {
    is TimelineItem.Anchor -> "anchor-$kind"
    is TimelineItem.Gap -> "gap-$start"
    is TimelineItem.Row -> "block-${block.id}"
}

private const val LARGE_TEXT = 1.5f

/** The time column widens with the font scale and wraps rather than clipping AM/PM. */
@Composable
private fun timeColumn(): Dp = 72.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)

@Composable
private fun TimeCell(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier
            .width(timeColumn())
            .padding(end = Space.sm),
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
        color = color,
        maxLines = 2,
    )
}

@Composable
private fun AnchorRow(item: TimelineItem.Anchor, time: TimeFormat) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeCell(time.format(item.time))
        Text(
            stringResource(if (item.kind == AnchorKind.WAKE) R.string.anchor_wake else R.string.anchor_bedtime),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun GapRow(item: TimelineItem.Gap, time: TimeFormat) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(timeColumn()))
        Text(
            stringResource(R.string.gap_free, durationText(item.minutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockRow(block: Block, isNow: Boolean, time: TimeFormat, actions: TodayActions) {
    val open = block.status == BlockStatus.PLANNED || block.status == BlockStatus.IN_PROGRESS
    val faded = !open
    val ink = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    val doneLabel = stringResource(R.string.action_mark_done)
    val moreLabel = stringResource(R.string.action_more)
    val clickable = if (open) {
        Modifier.combinedClickable(
            onClickLabel = doneLabel,
            onLongClickLabel = moreLabel,
            onLongClick = { actions.openActions(block.id) },
            onClick = { actions.markDone(block.id) },
        )
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(clickable)
            .padding(vertical = Space.md),
    ) {
        TimeCell(time.format(block.start.value), Modifier.alignByBaseline(), if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .alignByBaseline(),
        ) {
            Text(block.title, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SerifText), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta(block), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status(block, isNow)?.let { (label, color) ->
            Text(label, modifier = Modifier.alignByBaseline().padding(start = Space.sm), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun meta(block: Block): String {
    val duration = durationText(block.length)
    val kind = when (block.kind) {
        Kind.FIXED -> stringResource(R.string.label_fixed)
        Kind.OPTIONAL -> stringResource(R.string.label_optional)
        Kind.FLEXIBLE -> null
    }
    return if (kind == null) duration else stringResource(R.string.meta_separator, duration, kind)
}

@Composable
private fun status(block: Block, isNow: Boolean): Pair<String, Color>? = when {
    isNow -> stringResource(R.string.label_now) to MaterialTheme.colorScheme.primary

    block.status == BlockStatus.DONE -> stringResource(R.string.label_done) to MaterialTheme.colorScheme.onSurfaceVariant

    // Skipping is neutral: same quiet ink as done, never the accent.
    block.status == BlockStatus.SKIPPED -> stringResource(R.string.label_skipped) to MaterialTheme.colorScheme.onSurfaceVariant

    else -> null
}

private fun LazyListScope.offPlan(blocks: List<Block>, time: TimeFormat, actions: TodayActions) {
    if (blocks.isEmpty()) return
    item(key = "off-plan") {
        Column(Modifier.padding(top = Space.lg, bottom = Space.sm)) {
            SectionHeading(stringResource(R.string.off_plan_title))
        }
    }
    items(blocks, key = { "off-${it.id}" }) { block ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeCell(time.format(block.start.value))
            Column(Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(block.offPlanLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QuietButton(stringResource(R.string.action_put_back), { actions.retry(block.id) })
        }
    }
}

private val Block.offPlanLabel: Int get() = when (status) {
    BlockStatus.MISSED -> R.string.off_missed
    BlockStatus.DROPPED -> R.string.off_dropped
    else -> R.string.off_unplaced
}

@Composable
private fun ActionBar(actions: TodayActions) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
            Hairline()
            val padding = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md)
            // Large text gets a stacked bar, so neither label breaks mid-word.
            if (LocalDensity.current.fontScale >= LARGE_TEXT) {
                Column(padding, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    PrimaryButton(stringResource(R.string.action_running_late), actions::runningLate, Modifier.fillMaxWidth())
                    QuietButton(stringResource(R.string.action_rebuild), actions::rebuild, Modifier.fillMaxWidth())
                }
            } else {
                Row(padding, horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(stringResource(R.string.action_running_late), actions::runningLate, Modifier.weight(1f))
                    QuietButton(stringResource(R.string.action_rebuild), actions::rebuild)
                }
            }
        }
    }
}

@Composable
private fun MessageEffect(message: Message?, host: SnackbarHostState, actions: TodayActions) {
    val outcome = message?.outcome
    val text = outcome?.let { messageText(it) }
    val undo = stringResource(R.string.msg_undo)
    val offersUndo = outcome is Outcome.Applied && outcome.kind != AppliedKind.UNDONE
    LaunchedEffect(message?.id) {
        if (message == null || text == null) return@LaunchedEffect
        val result = host.showSnackbar(text, actionLabel = if (offersUndo) undo else null, withDismissAction = false, duration = if (offersUndo) SnackbarDuration.Long else SnackbarDuration.Short)
        actions.messageShown(message.id)
        if (result == SnackbarResult.ActionPerformed) actions.undo()
    }
}

@Composable
private fun messageText(outcome: Outcome): String? = when (outcome) {
    is Outcome.Applied -> when (outcome.kind) {
        AppliedKind.DONE -> stringResource(R.string.msg_done, outcome.blockTitle.orEmpty())
        AppliedKind.STARTED -> stringResource(R.string.msg_started, outcome.blockTitle.orEmpty())
        AppliedKind.SKIPPED -> stringResource(R.string.msg_skipped, outcome.blockTitle.orEmpty())
        AppliedKind.PLAN_UPDATED -> stringResource(R.string.msg_updated)
        AppliedKind.UNDONE -> stringResource(R.string.msg_undone)
    }

    Outcome.NothingToChange -> stringResource(R.string.msg_nothing)

    Outcome.NoTimeLeft -> stringResource(R.string.msg_no_time)

    is Outcome.Rejected -> stringResource(
        when (outcome.code) {
            ErrorCode.OVERLAPS_LOCKED -> R.string.error_overlaps_locked
            ErrorCode.OUTSIDE_WINDOW -> R.string.error_outside_window
            ErrorCode.IN_THE_PAST -> R.string.error_in_the_past
            ErrorCode.PAST_BEDTIME_NOT_ALLOWED -> R.string.error_past_bedtime
            ErrorCode.DEPENDENCY_VIOLATION -> R.string.error_dependency
            ErrorCode.INVALID_TARGET, ErrorCode.NOT_LATER -> R.string.error_invalid_target
            else -> R.string.error_generic
        },
    )

    is Outcome.NeedsApproval -> null
}

/**
 * Lets display type grow with the system font scale up to [max] times its 1x size, measured on
 * the resolved pixel size, since Android 14 scales large text non-linearly. Never smaller than 1x.
 */
@Composable
internal fun TextStyle.cappedScale(max: Float): TextStyle {
    val density = LocalDensity.current
    val basePx = fontSize.value * density.density
    val resolvedPx = with(density) { fontSize.toPx() }
    val targetPx = basePx * max
    if (resolvedPx <= targetPx) return this
    val factor = targetPx / resolvedPx
    return with(density) {
        copy(
            fontSize = targetPx.toSp(),
            lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else (lineHeight.toPx() * factor).toSp(),
        )
    }
}

/** Cuts a text's height to [below] under its last baseline, keeping its baselines for alignment. */
private fun Modifier.trimBelowBaseline(below: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val first = placeable[FirstBaseline]
    val last = placeable[LastBaseline]
    val height = if (last == AlignmentLine.Unspecified) placeable.height else minOf(placeable.height, last + below.roundToPx())
    layout(placeable.width, height, mapOf(FirstBaseline to first, LastBaseline to last)) { placeable.place(0, 0) }
}
