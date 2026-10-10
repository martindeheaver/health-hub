# Health Hub

An Android app that brings together data from a Xiaomi smartwatch, a Renpho bioimpedance scale and a Braun blood pressure monitor, shows it on one dashboard, charts trends over time, and asks Claude for a daily analysis of what the numbers mean.

## How it works

Health Hub reads everything from **Health Connect**, Android's shared health data store. It doesn't talk to the devices directly.

```
Xiaomi watch ─► Mi Fitness ─┐
Renpho scale ─► Renpho app ─┼─► Health Connect ─► Health Hub ─► Claude API
Braun BP     ─► Braun app  ─┘        ▲
                    (or manual entry ┘ in Health Hub)
```

- **Today**: activity, heart, sleep, body composition and blood pressure for any day. It refreshes every minute while open, and whenever you return to the app.
- **Trends**: charts of a chosen metric over a week, month, 3 months or a year, with average, latest, change and range.
- **Insights**: Claude's analysis of each day. Every morning at about 7:30 it analyses yesterday in the background and sends a notification. If that job couldn't run, it catches up when you next open the app. You can also analyse any day on demand.

## Install

1. Open the repo's **Releases** page on your phone and download `HealthHub.apk` from the latest build.
2. Allow your browser to install unknown apps when prompted, then install.
3. Each push to `main` builds a new APK. It installs as an update and keeps your settings.

## First-time setup

1. Open Health Hub and tap **Allow access**. Allow everything, including *Access data in the background*, which the morning analysis needs.
2. Switch on Health Connect sharing in each source app:
   - **Mi Fitness**: Profile → Health Connect (or Add accounts → Health Connect).
   - **Renpho**: Me → Integrations / Third-party → Health Connect (older versions say Google Fit).
   - **Braun Healthy Heart**: look for Health Connect in settings. If it isn't there, use **Add reading** on the Today screen.
3. Under **Settings → Claude**, paste your API key and tap **Test connection**. Optionally add your height (used for BMI) and some context about yourself.

## Privacy

Data stays on the phone, except that when an analysis runs, that day's readings and two weeks of daily values are sent to the Claude API using your key. The key is stored in app-private storage, and the app opts out of Android backup.

Insights are AI-generated from consumer devices and aren't medical advice.

## Building

GitHub Actions builds the APK (`.github/workflows/build.yml`). To build locally, open the folder in Android Studio and run it.
