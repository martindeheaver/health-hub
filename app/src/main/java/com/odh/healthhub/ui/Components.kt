package com.odh.healthhub.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odh.healthhub.data.DayValue

@Composable
fun HealthHubTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF7FD3B0), tertiary = Color(0xFFF2B8B5))
        else -> lightColorScheme(primary = Color(0xFF1F6F54), tertiary = Color(0xFFB3261E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun fmt(v: Double?, decimals: Int = 1): String = v?.let { "%,.${decimals}f".format(it) } ?: "—"
fun fmt(v: Long?): String = v?.let { "%,d".format(it) } ?: "—"

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** A grid of label/value tiles, two per row. */
@Composable
fun StatGrid(items: List<Triple<String, String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value, unit) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            if (value != "—" && unit.isNotEmpty()) {
                                Text(
                                    " $unit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Line chart of daily values; draws a second line for [DayValue.b] when present. */
@Composable
fun LineChart(points: List<DayValue>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val all = points.flatMap { listOfNotNull(it.a, it.b) }
    if (all.isEmpty()) return
    var lo = all.min(); var hi = all.max()
    if (hi - lo < 1e-6) { lo -= 1; hi += 1 }
    val pad = (hi - lo) * 0.1
    lo -= pad; hi += pad

    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun x(i: Int) = if (points.size == 1) w / 2 else i * w / (points.size - 1)
        fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()

        for (k in 0..3) {
            val gy = h * k / 3f
            drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }

        fun drawSeries(sel: (DayValue) -> Double?, color: Color) {
            val path = Path()
            var started = false
            val dots = mutableListOf<Offset>()
            points.forEachIndexed { i, p ->
                val v = sel(p) ?: return@forEachIndexed
                val o = Offset(x(i), y(v))
                if (!started) { path.moveTo(o.x, o.y); started = true } else path.lineTo(o.x, o.y)
                dots += o
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val r = if (points.size > 60) 1.5.dp.toPx() else 3.dp.toPx()
            dots.forEach { drawCircle(color, r, it) }
        }
        drawSeries({ it.a }, primary)
        if (points.any { it.b != null }) drawSeries({ it.b }, second)
    }
}

/** Renders the simple markdown Claude is asked to use: ## headings, - bullets, **bold**. */
@Composable
fun InsightText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("#") -> Text(
                    line.trimStart('#', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                    Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(bold(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(bold(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun bold(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(part)
        if (i % 2 == 1) pop()
    }
}
