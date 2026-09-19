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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gswizz.shear.ui.Wordmark
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Motion
import dev.gswizz.shear.ui.theme.shearFlavor
import kotlin.random.Random

/**
 * The Cut: the shared URL in monospace, a blade sweeping left to right, and every removed run crumbling into pixels
 * while the survivors close ranks into the clean link.
 *
 * With nothing to cut the blade still sweeps, so the moment reads the same; with no link at all the wordmark stands in.
 * A wholesale plan crumbles the whole line and types the final URL beneath the blade's wake.
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
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val crumbColors = remember(flavor) { listOf(flavor.red, flavor.maroon, flavor.peach) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(LINE_HEIGHT_DP.dp * LINES)) {
        val maxWidth = constraints.maxWidth.toFloat()
        val line = remember(plan, maxWidth) { fit(plan, measurer, baseStyle, maxWidth) }
        val scene = line.scene
        var elapsed by remember { mutableFloatStateOf(0f) }
        val crumbs = remember(plan) { PixelField(CRUMB_CAPACITY, Random(plan.original.hashCode())) }
        val density = LocalDensity.current
        LaunchedEffect(line) {
            val start = withFrameNanos { it }
            var last = start
            val cutSoFar = FloatArray(line.layouts.size)
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - last) / NANOS_PER_SECOND
                last = now
                elapsed = (now - start) / NANOS_PER_MILLI
                for (i in line.layouts.indices) {
                    val cut = scene.cutFraction(i, elapsed)
                    if (cut > cutSoFar[i]) {
                        val left = scene.x(i, elapsed) + cutSoFar[i] * scene.widths[i]
                        val width = (cut - cutSoFar[i]) * scene.widths[i]
                        spawnCrumbs(crumbs, left, width, line.lineHeight, density.density)
                        cutSoFar[i] = cut
                    }
                }
                crumbs.step(dt, gravity = GRAVITY_DP * density.density)
                if (elapsed >= Motion.CUT_MS) break
            }
            onFinished()
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(LINE_HEIGHT_DP.dp * LINES)) {
            val t = elapsed
            val top = line.lineHeight / 2
            for ((i, layout) in line.layouts.withIndex()) {
                val x = scene.x(i, t)
                val cut = scene.cutFraction(i, t)
                if (cut >= 1f) continue
                val color = if (plan.segments[i].removed) flavor.red else flavor.text
                clipRect(
                    left = x + cut * scene.widths[i],
                    top = 0f,
                    right = x + scene.widths[i],
                    bottom = size.height,
                ) {
                    drawText(layout, color = color, topLeft = Offset(x, top))
                }
            }
            if (plan.wholesale && t > scene.sweepMs) {
                val typed = ((t - scene.sweepMs) / (Motion.CUT_MS - scene.sweepMs)).coerceIn(0f, 1f)
                val shown = plan.final.take((typed * plan.final.length).toInt())
                drawText(measurer.measure(shown, line.style), color = flavor.text, topLeft = Offset(0f, top))
            }
            for (i in 0 until crumbs.count) {
                drawRect(
                    color = crumbColors[crumbs.colorIndex[i]].copy(alpha = crumbs.alpha(i)),
                    topLeft = Offset(crumbs.x[i], crumbs.y[i]),
                    size = Size(crumbs.size[i], crumbs.size[i]),
                )
            }
            if (t < scene.sweepMs) {
                val bx = scene.bladeX(t)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor))),
                    topLeft = Offset(bx - BLADE_DP.dp.toPx() / 2, 0f),
                    size = Size(BLADE_DP.dp.toPx(), size.height),
                )
            }
        }
    }
}

/** The wordmark's own reveal, for shares that carried no link at all. */
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

/** The plan measured at a size that fits [maxWidth], shortening the first kept run from its middle if it must. */
private class FittedLine(val style: TextStyle, val layouts: List<TextLayoutResult>, val scene: CutScene) {
    val lineHeight: Float = layouts.maxOfOrNull { it.size.height.toFloat() } ?: 0f
}

private fun fit(plan: CutPlan, measurer: TextMeasurer, base: TextStyle, maxWidth: Float): FittedLine {
    var segments = plan.segments
    var style = base
    var layouts = segments.map { measurer.measure(it.text, style) }
    val natural = layouts.sumOf { it.size.width }.toFloat()
    if (natural > maxWidth) {
        val scaled = (base.fontSize.value * maxWidth / natural).coerceAtLeast(MIN_SP)
        style = base.copy(fontSize = scaled.sp)
        layouts = segments.map { measurer.measure(it.text, style) }
    }
    val firstKept = segments.indexOfFirst { !it.removed }
    while (firstKept >= 0 && layouts.sumOf { it.size.width } > maxWidth) {
        val text = segments[firstKept].text
        val shorter = shortenPath(text, SHORTEN_STEP)
        if (shorter == text) break
        segments = segments.toMutableList().also { it[firstKept] = it[firstKept].copy(text = shorter) }
        layouts = segments.map { measurer.measure(it.text, style) }
    }
    val widths = layouts.map { it.size.width.toFloat() }
    return FittedLine(style, layouts, CutScene(segments, widths, sweepMs = SWEEP_MS, slideMs = SLIDE_MS))
}

/**
 * Drops [drop] characters from the middle of [text]'s path, marking the gap with an ellipsis, so the host stays whole
 * and the last character survives. Without a path the whole text gives way from its middle. Already applied? Same text
 * comes back, so callers can stop.
 */
internal fun shortenPath(text: String, drop: Int): String {
    if (drop <= 0) return text
    val authorityEnd = text.indexOf("://").let { if (it < 0) -1 else text.indexOf('/', it + 3) }
    val regionStart = if (authorityEnd < 0) 0 else authorityEnd + 1
    val region =
        text.substring(regionStart).removePrefix(ELLIPSIS).let { r ->
            // Re-shorten an already shortened region by treating the ellipsis as the cut point.
            val cut = r.indexOf(ELLIPSIS)
            if (cut < 0) r else r.removeRange(cut, cut + 1)
        }
    val keep = (region.length - drop).coerceAtLeast(1)
    if (keep >= region.length && text.substring(regionStart).indexOf(ELLIPSIS) < 0) return text
    val tail = (keep + 1) / 2
    val head = keep - tail
    return text.substring(0, regionStart) + region.take(head) + ELLIPSIS + region.takeLast(tail)
}

private fun spawnCrumbs(field: PixelField, left: Float, width: Float, lineHeight: Float, density: Float) {
    val count = (width / (CRUMB_SPACING_DP * density)).toInt().coerceAtLeast(1)
    val random = Random((left * 31 + width * 17).toInt())
    repeat(count) {
        field.spawn(
            x = left + random.nextFloat() * width,
            y = lineHeight * (HALF + random.nextFloat()),
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
private const val SWEEP_MS = 500
private const val SLIDE_MS = 150
private const val LINE_HEIGHT_DP = 20
private const val LINES = 3
private const val BLADE_DP = 2
private const val MIN_SP = 10f
private const val SHORTEN_STEP = 2
private const val ELLIPSIS = "…"
private const val CRUMB_CAPACITY = 240
private const val CRUMB_SPACING_DP = 3f
private const val CRUMB_SPEED_DP = 60f
private const val CRUMB_MIN_DP = 2f
private const val CRUMB_MAX_DP = 4f
private const val CRUMB_COLORS = 3
private const val CRUMB_LIFE_S = 0.35f
private const val GRAVITY_DP = 500f
private const val HALF = 0.5f
