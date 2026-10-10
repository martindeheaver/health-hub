package com.odh.healthhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.odh.healthhub.HealthHubApp
import com.odh.healthhub.data.Analysis
import com.odh.healthhub.data.DaySummary
import com.odh.healthhub.data.hm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(app: HealthHubApp, refreshTick: Int, onShowTrends: () -> Unit, modifier: Modifier = Modifier) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var summary by remember { mutableStateOf<DaySummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var showBp by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var analysing by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isToday = date == LocalDate.now()

    LaunchedEffect(date, refreshTick, reload) {
        loading = true
        error = null
        summary = runCatching { app.repo.daySummary(date) }
            .onFailure { error = it.message ?: it.toString() }.getOrNull()
        analysis = app.store.analysis(date)
        loading = false
    }
    // Auto-refresh while open on today, picking up new syncs from Mi Fitness / Renpho / Braun
    LaunchedEffect(date) {
        while (date == LocalDate.now()) { delay(60_000); reload++ }
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { date = date.plusDays(1) }, enabled = !isToday) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShowTrends, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ShowChart, null); Spacer(Modifier.width(8.dp)); Text("Show trends")
                }
                OutlinedButton(onClick = { reload++ }) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh")
                }
            }
        }

        error?.let { e -> item { Text("Couldn't read Health Connect: $e", color = MaterialTheme.colorScheme.error) } }

        val s = summary
        if (s != null) {
            if (!s.hasData) item {
                Text(
                    "No data for this day yet. Open Mi Fitness, Renpho or the Braun app to sync, and make sure each one has Health Connect sharing switched on.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionCard("Activity") {
                    StatGrid(listOf(
                        Triple("Steps", fmt(s.steps), ""),
                        Triple("Active calories", fmt(s.activeKcal, 0), "kcal"),
                    ))
                }
            }
            item {
                SectionCard("Heart") {
                    StatGrid(listOf(
                        Triple("Resting", fmt(s.restingHr), "bpm"),
                        Triple("Average", fmt(s.hrAvg), "bpm"),
                        Triple("Min – max", if (s.hrMin != null) "${s.hrMin}–${s.hrMax}" else "—", "bpm"),
                        Triple("SpO₂", fmt(s.spo2Avg, 0), "%"),
                    ))
                }
            }
            item {
                val zone = ZoneId.systemDefault()
                val t = DateTimeFormatter.ofPattern("HH:mm")
                SectionCard(
                    "Sleep",
                    subtitle = if (s.sleepStart != null && s.sleepEnd != null)
                        "${s.sleepStart.atZone(zone).format(t)} – ${s.sleepEnd.atZone(zone).format(t)}" else null,
                ) {
                    StatGrid(listOf(
                        Triple("Total", s.sleepMinutes?.let(::hm) ?: "—", ""),
                        Triple("Deep", s.deepMinutes?.let(::hm) ?: "—", ""),
                        Triple("Light", s.lightMinutes?.let(::hm) ?: "—", ""),
                        Triple("REM", s.remMinutes?.let(::hm) ?: "—", ""),
                    ))
                }
            }
            item {
                SectionCard("Body composition") {
                    StatGrid(listOf(
                        Triple("Weight", fmt(s.weightKg), "kg"),
                        Triple("BMI", fmt(s.bmi(app.store.heightCmValue)), ""),
                        Triple("Body fat", fmt(s.bodyFatPct), "%"),
                        Triple("Lean mass", fmt(s.leanMassKg), "kg"),
                        Triple("Bone mass", fmt(s.boneMassKg), "kg"),
                        Triple("Body water", fmt(s.waterMassKg), "kg"),
                        Triple("BMR", fmt(s.bmrKcal, 0), "kcal"),
                    ))
                }
            }
            item {
                SectionCard("Blood pressure") {
                    if (s.bp.isEmpty()) {
                        Text("No readings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val t = DateTimeFormatter.ofPattern("HH:mm")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.bp.forEach { r ->
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${r.systolic.toInt()}/${r.diastolic.toInt()}",
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    Text(" mmHg", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.weight(1f))
                                    Text("${r.time.atZone(ZoneId.systemDefault()).format(t)} · ${r.source}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showBp = true }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add reading")
                    }
                }
            }
            if (s.sources.isNotEmpty()) item {
                Text("Data from: ${s.sources.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                SectionCard("Claude's analysis", subtitle = analysis?.let {
                    "Generated " + java.time.Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                }) {
                    analysis?.let { InsightText(it.text); Spacer(Modifier.height(12.dp)) }
                    aiError?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
                    FilledTonalButton(
                        enabled = !analysing && s.hasData,
                        onClick = {
                            analysing = true; aiError = null
                            scope.launch {
                                app.analyzer.analyse(date, force = true)
                                    .onSuccess { analysis = it }
                                    .onFailure { aiError = it.message ?: it.toString() }
                                analysing = false
                            }
                        },
                    ) {
                        if (analysing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(when {
                            analysing -> "Analysing…"
                            analysis != null -> "Re-analyse"
                            isToday -> "Analyse today so far"
                            else -> "Analyse this day"
                        })
                    }
                    if (app.store.apiKey.isBlank()) {
                        Text("Add your Claude API key in Settings first.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(
                    "Insights are generated by AI from consumer-device data and aren't medical advice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showBp) BloodPressureDialog(
        onDismiss = { showBp = false },
        onSave = { sys, dia ->
            showBp = false
            scope.launch {
                val time = if (date == LocalDate.now()) java.time.Instant.now()
                else date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
                runCatching { app.repo.addBloodPressure(time, sys, dia) }
                    .onFailure { error = "Couldn't save reading: ${it.message}" }
                reload++
            }
        },
    )
}

@Composable
private fun BloodPressureDialog(onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    val s = sys.toDoubleOrNull(); val d = dia.toDoubleOrNull()
    val valid = s != null && d != null && s in 60.0..260.0 && d in 30.0..160.0 && s > d
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add blood pressure") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sys, { sys = it.filter(Char::isDigit) }, label = { Text("Systolic (top)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(dia, { dia = it.filter(Char::isDigit) }, label = { Text("Diastolic (bottom)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Box { Text("Saved to Health Connect.", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(s!!, d!!) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
