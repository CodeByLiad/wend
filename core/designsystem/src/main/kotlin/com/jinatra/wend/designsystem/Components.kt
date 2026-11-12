package com.jinatra.wend.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A one-pixel rule in the paper's rule colour. The page's only separator. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    VerticalDivider(modifier = modifier.fillMaxHeight(), thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

/** The serif name at the head of a screen. */
@Composable
fun Wordmark(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** A serif section heading under a hairline rule, marked as a heading for TalkBack. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Hairline()
        Text(
            text = text,
            modifier = Modifier
                .padding(top = Space.lg)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A label over a large serif numeral, used in rows of three split by vertical hairlines. */
@Composable
fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** The one filled, crimson action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.md),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** A text action in ink, for secondary choices beside the primary one. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Equal-width stat columns divided by vertical hairlines. */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.lg), content = content)
}

@Preview(name = "Components, light", widthDp = 360)
@Preview(name = "Components, dark", widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentsPreview() {
    WendTheme {
        Surface {
            Column(Modifier.padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Wordmark("Wend")
                Hairline()
                StatRow(Modifier.heightIn(min = 80.dp)) {
                    StatColumn("Done", "4", Modifier.weight(1f))
                    VerticalHairline()
                    StatColumn("Skipped", "1", Modifier.weight(1f))
                    VerticalHairline()
                    StatColumn("Left", "9", Modifier.weight(1f))
                }
                Hairline()
                SectionHeading("Schedule")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton("I'm running late", {})
                    QuietButton("Rebuild", {}, Modifier.width(120.dp))
                }
            }
        }
    }
}
