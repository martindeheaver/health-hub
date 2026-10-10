package com.odh.healthhub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odh.healthhub.HealthHubApp
import com.odh.healthhub.data.DayValue
import com.odh.healthhub.data.Metric
import java.time.format.DateTimeFormatter

@Composable
fun TrendsScreen(app: HealthHubApp, refreshTick: Int, modifier: Modifier = Modifier) {
    var metric by remember { mutableStateOf(Metric.STEPS) }
    var days by remember { mutableIntStateOf(30) }
    var points by remember { mutableStateOf<List<DayValue>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(metric, days, refreshTick) {
        loading = true
        points = runCatching { app.repo.series(metric, days) }.getOrDefault(emptyList())
        loading = false
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { m ->
                FilterChip(selected = m == metric, onClick = { metric = m }, label = { Text(m.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "Week", 30 to "Month", 90 to "3 months", 365 to "Year").forEach { (d, label) ->
                FilterChip(selected = d == days, onClick = { days = d }, label = { Text(label) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        val present = points.filter { it.a != null }
        SectionCard(metric.label, subtitle = metric.unit) {
            if (present.isEmpty()) {
                Text("No ${metric.label.lowercase()} data in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val all = points.flatMap { listOfNotNull(it.a, it.b) }
                Row {
                    Text(fmt(all.max(), metric.decimals), style = MaterialTheme.typography.labelSmall)
                }
                LineChart(points, Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp))
                Row {
                    Text(fmt(all.min(), metric.decimals), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth()) {
                    val f = DateTimeFormatter.ofPattern("d MMM")
                    Text(points.first().date.format(f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(points.last().date.format(f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (metric == Metric.BLOOD_PRESSURE) {
                    Text("Top line systolic, bottom line diastolic (daily averages).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (present.isNotEmpty()) {
            SectionCard("Summary") {
                StatGrid(stats(present, metric))
            }
        }
    }
}

private fun stats(present: List<DayValue>, metric: Metric): List<Triple<String, String, String>> {
    val d = metric.decimals
    fun both(sel: (DayValue) -> Double?): Pair<String, String> {
        val v = present.mapNotNull(sel)
        return fmt(v.average(), d) to fmt(v.last() - v.first(), d).let { if (!it.startsWith("-") && it != "—") "+$it" else it }
    }
    return if (metric == Metric.BLOOD_PRESSURE) {
        val sys = present.mapNotNull { it.a }; val dia = present.mapNotNull { it.b }
        listOf(
            Triple("Average", "${fmt(sys.average(), 0)}/${fmt(dia.average(), 0)}", "mmHg"),
            Triple("Latest", "${fmt(sys.last(), 0)}/${fmt(dia.lastOrNull(), 0)}", "mmHg"),
            Triple("Highest", "${fmt(sys.max(), 0)}/${fmt(dia.maxOrNull(), 0)}", "mmHg"),
            Triple("Days with readings", present.size.toString(), ""),
        )
    } else {
        val v = present.mapNotNull { it.a }
        val (avg, change) = both { it.a }
        listOf(
            Triple("Average", avg, metric.unit),
            Triple("Latest", fmt(v.last(), d), metric.unit),
            Triple("Change", change, metric.unit),
            Triple("Range", "${fmt(v.min(), d)}–${fmt(v.max(), d)}", metric.unit),
            Triple("Days with data", present.size.toString(), ""),
        )
    }
}
