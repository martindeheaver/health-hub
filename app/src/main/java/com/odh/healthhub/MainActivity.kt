package com.odh.healthhub

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.odh.healthhub.data.HealthRepository
import com.odh.healthhub.ui.HealthHubTheme
import com.odh.healthhub.ui.InsightsScreen
import com.odh.healthhub.ui.SettingsScreen
import com.odh.healthhub.ui.TodayScreen
import com.odh.healthhub.ui.TrendsScreen
import java.time.LocalDate

enum class Tab(val label: String) { TODAY("Today"), TRENDS("Trends"), INSIGHTS("Insights"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {

    private val app get() = application as HealthHubApp

    private var tab by mutableStateOf(Tab.TODAY)
    private var refreshTick by mutableIntStateOf(0)
    private var hasPermissions by mutableStateOf<Boolean?>(null)
    private var sdkStatus by mutableIntStateOf(HealthConnectClient.SDK_UNAVAILABLE)

    private val healthPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            refreshTick++
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestHealthPermissions() = healthPermissionLauncher.launch(HealthRepository.ALL_PERMISSIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("open_insights", false) == true) tab = Tab.INSIGHTS
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            HealthHubTheme {
                LaunchedEffect(refreshTick) {
                    sdkStatus = app.repo.sdkStatus()
                    hasPermissions = if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) app.repo.hasCorePermissions() else false
                }
                LaunchedEffect(hasPermissions) {
                    // Catch-up: if the morning job couldn't run, analyse yesterday when the app opens
                    if (hasPermissions == true && app.store.autoAnalyse && app.store.apiKey.isNotBlank()) {
                        val y = LocalDate.now().minusDays(1)
                        if (app.store.analysis(y) == null) app.analyzer.analyse(y)
                    }
                }
                AppScaffold()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_insights", false)) tab = Tab.INSIGHTS
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScaffold() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (tab == Tab.TODAY) "Health Hub" else tab.label) },
                    actions = {
                        IconButton(onClick = { tab = Tab.SETTINGS }) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == Tab.TODAY, { tab = Tab.TODAY }, { Icon(Icons.Filled.Favorite, null) }, label = { Text("Today") })
                    NavigationBarItem(tab == Tab.TRENDS, { tab = Tab.TRENDS }, { Icon(Icons.Filled.ShowChart, null) }, label = { Text("Trends") })
                    NavigationBarItem(tab == Tab.INSIGHTS, { tab = Tab.INSIGHTS }, { Icon(Icons.Filled.AutoAwesome, null) }, label = { Text("Insights") })
                }
            },
        ) { padding ->
            val mod = Modifier.padding(padding)
            when {
                tab == Tab.SETTINGS -> SettingsScreen(app, refreshTick, ::requestHealthPermissions, mod)
                sdkStatus != HealthConnectClient.SDK_AVAILABLE -> HealthConnectMissing(mod)
                hasPermissions == false -> PermissionsNeeded(mod)
                hasPermissions == null -> Unit
                tab == Tab.TODAY -> TodayScreen(app, refreshTick, onShowTrends = { tab = Tab.TRENDS }, modifier = mod)
                tab == Tab.TRENDS -> TrendsScreen(app, refreshTick, mod)
                tab == Tab.INSIGHTS -> InsightsScreen(app, refreshTick, mod)
            }
        }
    }

    @Composable
    private fun PermissionsNeeded(modifier: Modifier) = Centered(modifier) {
        Text("Connect your health data", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Health Hub reads your watch, scale and blood pressure data from Health Connect. " +
                "Tap below and allow all, including \"Access data in the background\" so the daily analysis can run on its own.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = ::requestHealthPermissions) { Text("Allow access") }
    }

    @Composable
    private fun HealthConnectMissing(modifier: Modifier) = Centered(modifier) {
        Text("Health Connect needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This phone needs the Health Connect app (built into Android 14 and later). Install or update it, then reopen Health Hub.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthRepository.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding"))
                    .setPackage("com.android.vending")
            )
        }) { Text("Get Health Connect") }
    }

    @Composable
    private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
