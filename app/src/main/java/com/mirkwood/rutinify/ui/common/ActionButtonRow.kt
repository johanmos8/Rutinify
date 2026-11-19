package com.mirkwood.rutinify.ui.common

import com.mirkwood.rutinify.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/**
 * Buttons side by side that never wrap their labels: equal widths when every label fits that way, natural widths
 * sharing the leftover space when only that fits, and one full-width button per line otherwise (large text, long
 * translations). Children keep their order, so the first one stays first in every arrangement.
 */
@Composable
fun ActionButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val gaps = gap * (measurables.size - 1).coerceAtLeast(0)
        val free = width - gaps
        val widths: List<Int>? = when {
            measurables.isEmpty() -> emptyList()
            natural.max() * measurables.size <= free -> List(measurables.size) { free / measurables.size }
            natural.sum() <= free -> {
                val extra = (free - natural.sum()) / measurables.size
                natural.map { it + extra }
            }
            else -> null // stack
        }
        if (widths != null) {
            val placeables = measurables.zip(widths) { m, w -> m.measure(Constraints.fixedWidth(w)) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = 0
                placeables.forEach { p ->
                    p.placeRelative(x, (height - p.height) / 2)
                    x += p.width + gap
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val height = placeables.sumOf { it.height } + gaps
            layout(width, height) {
                var y = 0
                placeables.forEach { p ->
                    p.placeRelative(0, y)
                    y += p.height + gap
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ActionButtonRowPreview() {
    RutinifyTheme {
        Column {
            ActionButtonRow {
                Button(onClick = {}) { Text(stringResource(R.string.action_copy_template)) }
                OutlinedButton(onClick = {}) { Text(stringResource(R.string.action_open_in_ai)) }
            }
            ActionButtonRow {
                OutlinedButton(onClick = {}) { Text(stringResource(R.string.action_paste)) }
                Button(onClick = {}) { Text(stringResource(R.string.action_validate)) }
            }
        }
    }
}
