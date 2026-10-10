package com.odh.healthhub.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.odh.healthhub.HealthHubApp
import com.odh.healthhub.ai.ClaudeClient
import com.odh.healthhub.data.HealthRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: HealthHubApp, refreshTick: Int, requestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val store = app.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }
    var about by remember { mutableStateOf(store.aboutMe) }
    var height by remember { mutableStateOf(store.heightCm) }
    var auto by remember { mutableStateOf(store.autoAnalyse) }
    var status by remember { mutableStateOf<String?>(null) }
    var hcStatus by remember { mutableStateOf("Checking…") }

    LaunchedEffect(refreshTick) {
        hcStatus = if (!app.repo.available) "Health Connect not available" else {
            val g = app.repo.grantedPermissions()
            val core = HealthRepository.CORE_PERMISSIONS.count { it in g }
            val bg = if (app.repo.hasBackgroundPermission()) "on" else "off"
            "$core of ${HealthRepository.CORE_PERMISSIONS.size} data types allowed · background access $bg"
        }
    }

    fun save() {
        store.apiKey = apiKey; store.model = model; store.aboutMe = about; store.heightCm = height; store.autoAnalyse = auto
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Health Connect", subtitle = hcStatus) {
            Text(
                "In Mi Fitness, Renpho and the Braun app, switch on Health Connect sharing so their data lands here. " +
                    "If the Braun app doesn't support it, add readings by hand on the Today screen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = requestPermissions) { Text("Permissions") }
                OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                }) { Text("Open Health Connect") }
            }
        }

        SectionCard("Claude") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Analyse yesterday automatically each morning", Modifier.weight(1f))
                    Switch(auto, { auto = it })
                }
                OutlinedButton(onClick = {
                    save(); status = "Testing…"
                    scope.launch {
                        status = runCatching { ClaudeClient(store.apiKey, store.model).complete("Reply with just: OK", "Test", 10) }
                            .fold({ "Connected ✓ ($it)" }, { "Failed: ${it.message}" })
                    }
                }) { Text("Test connection") }
            }
        }

        SectionCard("About you", subtitle = "Optional context that helps Claude interpret your numbers") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(height, { height = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Height (cm)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(about, { about = it }, label = { Text("Age, sex, goals, conditions, medication…") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        }

        Button(onClick = { save(); status = "Saved" }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        status?.let { Text(it) }
        Text(
            "Your API key and settings are stored only on this phone. When an analysis runs, that day's readings and two weeks of daily values are sent to the Claude API.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
