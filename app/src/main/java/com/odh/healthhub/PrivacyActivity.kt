package com.odh.healthhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.odh.healthhub.ui.HealthHubTheme

/** Shown by Health Connect when you tap "privacy policy" for this app. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                        Text("Health Hub privacy", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Health Hub reads activity, heart, sleep, body composition and blood pressure data from " +
                                "Health Connect to show it on your phone. Data stays on this device, except that " +
                                "when an analysis runs, that day's readings (and the previous two weeks' daily " +
                                "values) are sent to Anthropic's Claude API using your own API key to generate " +
                                "insights. Nothing is sent anywhere else. Blood pressure readings you type in are " +
                                "saved to Health Connect.",
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
