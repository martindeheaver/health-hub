package com.odh.healthhub.ai

import com.odh.healthhub.data.Analysis
import com.odh.healthhub.data.HealthRepository
import com.odh.healthhub.data.Metric
import com.odh.healthhub.data.Store
import java.time.LocalDate

/** Sends a day's data (plus recent context) to Claude and saves the insight. */
class Analyzer(private val repo: HealthRepository, private val store: Store) {

    private val system = """
        You are a careful health-data analyst reviewing one person's daily readings from consumer devices:
        a Xiaomi smartwatch (activity, heart rate, sleep, SpO2), a Renpho bioimpedance scale (weight and body
        composition) and a Braun upper-arm blood pressure monitor. You'll get one day's data plus the previous
        two weeks for context.

        Explain what the numbers suggest about their health in plain, friendly British English. Be specific:
        refer to the actual values and how they compare with their recent baseline and with commonly used
        reference ranges (e.g. NHS blood pressure categories, typical resting heart rate, 7–9 hours of sleep).

        Rules:
        - You are not diagnosing. Don't invent data; if something is missing, say so briefly or skip it.
        - Note device limitations where relevant (e.g. bioimpedance body fat varies with hydration and time of
          day; wrist sleep staging and SpO2 are estimates).
        - Clearly flag anything that warrants medical attention: blood pressure at or above 180/120 → advise
          seeking urgent medical advice (NHS 111, or 999 with symptoms such as chest pain, breathlessness or
          weakness); readings regularly at or above 140/90 → suggest booking a GP check; SpO2 persistently under
          92%, or resting heart rate persistently above 100 or below 40 with symptoms → suggest speaking to a GP.
        - Keep it to about 250–350 words.

        Format using only: lines starting with "## " for section headings, lines starting with "- " for bullets,
        and **bold** for emphasis. Use these sections in order:
        ## Summary (2–3 sentences)
        ## What looks good
        ## Worth watching
        ## Suggestions for today
        ## Trends
    """.trimIndent()

    suspend fun analyse(date: LocalDate, force: Boolean = false): Result<Analysis> = runCatching {
        if (!force) store.analysis(date)?.let { return@runCatching it }

        val day = repo.daySummary(date)
        if (!day.hasData) throw IllegalStateException("No health data found for $date yet.")

        val context = recentContext(date)
        val about = store.aboutMe.trim()
        val user = buildString {
            if (about.isNotEmpty()) append("About me: $about\n")
            store.heightCm.takeIf { it.isNotBlank() }?.let { append("Height: $it cm\n") }
            append("\n=== Today's data ===\n")
            append(day.toPromptText(store.heightCmValue))
            append("\n\n=== Previous 14 days (daily values, blank = no data) ===\n")
            append(context)
            if (date == LocalDate.now()) append("\n\nNote: this is today, so activity totals are only partial so far.")
        }

        val text = ClaudeClient(store.apiKey, store.model).complete(system, user)
        Analysis(date, text, System.currentTimeMillis(), store.model).also(store::saveAnalysis)
    }

    private suspend fun recentContext(date: LocalDate): String {
        val metrics = listOf(
            Metric.STEPS, Metric.RESTING_HR, Metric.SLEEP, Metric.WEIGHT, Metric.BODY_FAT, Metric.BLOOD_PRESSURE,
        )
        // series() runs up to today; take the 14 days before the analysed date
        val daysBack = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now()).toInt()
        val span = 14 + daysBack + 1
        val data = metrics.associateWith { m ->
            runCatching { repo.series(m, span) }.getOrDefault(emptyList())
                .filter { it.date < date && it.date >= date.minusDays(14) }
                .associateBy { it.date }
        }
        fun f(v: Double?, d: Int) = v?.let { "%.${d}f".format(it) } ?: ""
        val header = "date | steps | resting HR | sleep h | weight kg | body fat % | BP avg"
        val rows = (14 downTo 1).map { date.minusDays(it.toLong()) }.map { d ->
            val bp = data[Metric.BLOOD_PRESSURE]?.get(d)
            listOf(
                d.toString(),
                f(data[Metric.STEPS]?.get(d)?.a, 0),
                f(data[Metric.RESTING_HR]?.get(d)?.a, 0),
                f(data[Metric.SLEEP]?.get(d)?.a, 1),
                f(data[Metric.WEIGHT]?.get(d)?.a, 1),
                f(data[Metric.BODY_FAT]?.get(d)?.a, 1),
                if (bp?.a != null) "${bp.a.toInt()}/${bp.b?.toInt()}" else "",
            ).joinToString(" | ")
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
