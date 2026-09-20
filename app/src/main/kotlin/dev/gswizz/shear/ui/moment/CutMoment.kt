/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.Wordmark
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Motion
import dev.gswizz.shear.ui.theme.shearFlavor
import kotlin.random.Random

/**
 * The Cut: the shared URL wrapped in monospace, a blade sweeping every line left to right, removed characters crumbling
 * into pixels as it passes, and the survivors reflowing into the clean link once it is done.
 *
 * With nothing to cut the blade still sweeps, so the moment reads the same; with no link at all the wordmark stands in.
 */
@Composable
fun CutMoment(summary: MomentSummary, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val plan = summary.cut ?: summary.firstUrl?.let { CutPlan(it, it, listOf(Segment(it, removed = false)), false) }
    if (plan == null) {
        WordmarkMoment(onFinished = onFinished, modifier = modifier)
        return
    }
    val flavor = shearFlavor()
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val crumbColors = remember(flavor) { listOf(flavor.red, flavor.maroon, flavor.peach) }
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val grid = remember(plan, constraints.maxWidth) { Grid(measurer, style, constraints.maxWidth.toFloat()) }
        val scene =
            remember(plan, grid) {
                CutScene.of(plan, grid.columns, Motion.CUT_HOLD_MS, Motion.CUT_SWEEP_MS, Motion.CUT_REFLOW_MS)
            }
        val finalLines = remember(plan, grid) { plan.final.chunked(grid.columns) }
        val shownLines = scene.lines.coerceAtMost(MAX_LINES).coerceAtLeast(finalLines.size.coerceAtMost(MAX_LINES))
        val canvasHeight = grid.lineHeight * shownLines + grid.lineHeight * CRUMB_ROOM_LINES
        var elapsed by remember { mutableFloatStateOf(0f) }
        val crumbs = remember(plan) { PixelField(CRUMB_CAPACITY, Random(plan.original.hashCode())) }
        LaunchedEffect(scene) {
            val start = withFrameNanos { it }
            var last = start
            var cutCount = 0
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - last) / NANOS_PER_SECOND
                last = now
                elapsed = (now - start) / NANOS_PER_MILLI
                val passed = scene.bladeIndex(elapsed).toInt().coerceAtMost(scene.chars.size)
                for (i in cutCount until passed) {
                    if (scene.chars[i].removed) {
                        val x = scene.columnOf(i) * grid.advance
                        val y = scene.lineOf(i) * grid.lineHeight
                        spawnCrumbs(crumbs, x, y, grid.advance, grid.lineHeight, density, i)
                    }
                }
                cutCount = passed
                crumbs.step(dt, gravity = GRAVITY_DP * density)
                if (elapsed >= Motion.CUT_MS) break
            }
            onFinished()
        }
        Canvas(modifier = Modifier.fillMaxWidth().height((canvasHeight / density).dp)) {
            val t = elapsed
            val reflow = scene.reflow(t)
            val originalAlpha = 1f - reflow
            if (originalAlpha > 0f) {
                for (line in 0 until scene.lines.coerceAtMost(MAX_LINES)) {
                    val top = line * grid.lineHeight
                    val from = line * grid.columns
                    val to = (from + grid.columns).coerceAtMost(scene.chars.size)
                    val kept =
                        CharArray(to - from) { j ->
                            val i = from + j
                            if (scene.chars[i].removed) ' ' else scene.chars[i].char
                        }
                    val doomed =
                        CharArray(to - from) { j ->
                            val i = from + j
                            if (scene.chars[i].removed && !scene.isCut(i, t)) scene.chars[i].char else ' '
                        }
                    drawText(
                        measurer.measure(String(kept), style),
                        flavor.text.copy(alpha = originalAlpha),
                        Offset(0f, top),
                    )
                    drawText(
                        measurer.measure(String(doomed), style),
                        flavor.red.copy(alpha = originalAlpha),
                        Offset(0f, top),
                    )
                }
            }
            if (reflow > 0f) {
                for ((line, text) in finalLines.take(MAX_LINES).withIndex()) {
                    drawText(
                        measurer.measure(text, style),
                        flavor.text.copy(alpha = reflow),
                        Offset(0f, line * grid.lineHeight),
                    )
                }
            }
            for (i in 0 until crumbs.count) {
                drawRect(
                    color = crumbColors[crumbs.colorIndex[i]].copy(alpha = crumbs.alpha(i)),
                    topLeft = Offset(crumbs.x[i], crumbs.y[i]),
                    size = Size(crumbs.size[i], crumbs.size[i]),
                )
            }
            if (t < scene.sweepEndMs) {
                val bladeWidth = BLADE_DP.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor))),
                    topLeft =
                        Offset(
                            scene.bladeColumn(t) * grid.advance - bladeWidth / 2,
                            scene.bladeLine(t) * grid.lineHeight,
                        ),
                    size = Size(bladeWidth, grid.lineHeight),
                )
            }
        }
    }
}

/** The wordmark alone, for shares that carried no link at all. */
@Composable
private fun WordmarkMoment(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var now = start
        while ((now - start) / NANOS_PER_MILLI < Motion.CUT_MS) now = withFrameNanos { it }
        onFinished()
    }
    Wordmark(modifier = modifier.fillMaxWidth())
}

/** The monospace cell the URL is laid out in: one glyph's advance, the line height, and how many columns fit. */
private class Grid(measurer: TextMeasurer, style: TextStyle, maxWidth: Float) {
    val advance: Float
    val lineHeight: Float
    val columns: Int

    init {
        val glyph = measurer.measure("M", style).size
        advance = glyph.width.toFloat()
        lineHeight = glyph.height.toFloat()
        columns = (maxWidth / advance).toInt().coerceAtLeast(MIN_COLUMNS)
    }
}

private fun spawnCrumbs(field: PixelField, x: Float, y: Float, w: Float, h: Float, density: Float, seed: Int) {
    val random = Random(seed)
    repeat(CRUMBS_PER_CHAR) {
        field.spawn(
            x = x + random.nextFloat() * w,
            y = y + h * (HALF + random.nextFloat() * HALF),
            vx = (random.nextFloat() - HALF) * CRUMB_SPEED_DP * density,
            vy = -random.nextFloat() * CRUMB_SPEED_DP * density,
            size = (CRUMB_MIN_DP + random.nextFloat() * (CRUMB_MAX_DP - CRUMB_MIN_DP)) * density,
            colorIndex = random.nextInt(CRUMB_COLORS),
            lifeSeconds = CRUMB_LIFE_S,
        )
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000f
private const val MAX_LINES = 8
private const val MIN_COLUMNS = 8
private const val CRUMB_ROOM_LINES = 1
private const val BLADE_DP = 2
private const val CRUMB_CAPACITY = 400
private const val CRUMBS_PER_CHAR = 3
private const val CRUMB_SPEED_DP = 60f
private const val CRUMB_MIN_DP = 2f
private const val CRUMB_MAX_DP = 4f
private const val CRUMB_COLORS = 3
private const val CRUMB_LIFE_S = 0.6f
private const val GRAVITY_DP = 350f
private const val HALF = 0.5f
