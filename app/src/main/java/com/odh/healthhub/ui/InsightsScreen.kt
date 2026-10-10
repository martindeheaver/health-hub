package com.odh.healthhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.odh.healthhub.HealthHubApp
import com.odh.healthhub.data.Analysis
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(app: HealthHubApp, refreshTick: Int, modifier: Modifier = Modifier) {
    var list by remember { mutableStateOf<List<Analysis>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<LocalDate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick, reload) {
        list = app.store.allAnalyses()
        if (expanded == null) expanded = list.firstOrNull()?.date
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Each morning Claude analyses yesterday's full data automatically. You can also run it for any day from the Today screen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val yesterday = LocalDate.now().minusDays(1)
            FilledTonalButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    app.analyzer.analyse(yesterday, force = true)
                        .onSuccess { expanded = it.date }
                        .onFailure { error = it.message }
                    busy = false; reload++
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Analysing…" else "Analyse yesterday now")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (list.isEmpty()) item {
            Text("No insights yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { it.date.toString() }) { a ->
            val open = expanded == a.date
            Card(
                Modifier.fillMaxWidth().clickable { expanded = if (open) null else a.date },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        InsightText(a.text)
                    } else {
                        // first line of the summary as a preview
                        val preview = a.text.lines().map { it.trim() }
                            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.replace("**", "")
                        preview?.let {
                            Text(it, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
