package com.odh.healthhub.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BpReading(
    val time: Instant,
    val systolic: Double,
    val diastolic: Double,
    val source: String,
)

data class DaySummary(
    val date: LocalDate,
    // activity
    val steps: Long? = null,
    val activeKcal: Double? = null,
    // heart
    val hrAvg: Long? = null,
    val hrMin: Long? = null,
    val hrMax: Long? = null,
    val restingHr: Long? = null,
    val spo2Avg: Double? = null,
    // sleep (the night that ended on this date)
    val sleepMinutes: Long? = null,
    val deepMinutes: Long? = null,
    val lightMinutes: Long? = null,
    val remMinutes: Long? = null,
    val awakeMinutes: Long? = null,
    val sleepStart: Instant? = null,
    val sleepEnd: Instant? = null,
    // body composition (latest reading of the day)
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val leanMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val waterMassKg: Double? = null,
    val bmrKcal: Double? = null,
    // blood pressure
    val bp: List<BpReading> = emptyList(),
    val sources: Set<String> = emptySet(),
) {
    val hasData: Boolean
        get() = listOf(steps, activeKcal, hrAvg, restingHr, spo2Avg, sleepMinutes, weightKg, bodyFatPct)
            .any { it != null } || bp.isNotEmpty()

    fun bmi(heightCm: Double?): Double? {
        if (heightCm == null || heightCm <= 0 || weightKg == null) return null
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    /** Plain-text version of the day, sent to Claude. */
    fun toPromptText(heightCm: Double?): String {
        val zone = ZoneId.systemDefault()
        val t = DateTimeFormatter.ofPattern("HH:mm")
        fun line(label: String, v: Any?) = if (v == null) null else "- $label: $v"
        return listOfNotNull(
            "Date: ${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))}",
            "Activity:",
            line("Steps", steps),
            line("Active calories (kcal)", activeKcal?.let { "%.0f".format(it) }),
            "Heart:",
            line("Average heart rate (bpm)", hrAvg),
            line("Min / max heart rate (bpm)", if (hrMin != null && hrMax != null) "$hrMin / $hrMax" else null),
            line("Resting heart rate (bpm)", restingHr),
            line("Average SpO2 (%)", spo2Avg?.let { "%.1f".format(it) }),
            "Sleep (night ending this morning):",
            line("Total sleep", sleepMinutes?.let(::hm)),
            line("Bed / wake", if (sleepStart != null && sleepEnd != null)
                "${sleepStart.atZone(zone).format(t)} – ${sleepEnd.atZone(zone).format(t)}" else null),
            line("Deep", deepMinutes?.let(::hm)),
            line("Light", lightMinutes?.let(::hm)),
            line("REM", remMinutes?.let(::hm)),
            line("Awake", awakeMinutes?.let(::hm)),
            "Body composition (bioimpedance scale):",
            line("Weight (kg)", weightKg?.let { "%.1f".format(it) }),
            line("BMI", bmi(heightCm)?.let { "%.1f".format(it) }),
            line("Body fat (%)", bodyFatPct?.let { "%.1f".format(it) }),
            line("Lean body mass (kg)", leanMassKg?.let { "%.1f".format(it) }),
            line("Bone mass (kg)", boneMassKg?.let { "%.1f".format(it) }),
            line("Body water mass (kg)", waterMassKg?.let { "%.1f".format(it) }),
            line("BMR (kcal/day)", bmrKcal?.let { "%.0f".format(it) }),
            "Blood pressure:",
            if (bp.isEmpty()) "- No readings" else bp.joinToString("\n") {
                "- ${it.time.atZone(zone).format(t)}: ${it.systolic.toInt()}/${it.diastolic.toInt()} mmHg"
            },
        ).joinToString("\n")
    }
}

fun hm(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

enum class Metric(val label: String, val unit: String, val decimals: Int) {
    STEPS("Steps", "steps", 0),
    ACTIVE_KCAL("Active calories", "kcal", 0),
    RESTING_HR("Resting HR", "bpm", 0),
    AVG_HR("Average HR", "bpm", 0),
    SLEEP("Sleep", "hours", 1),
    WEIGHT("Weight", "kg", 1),
    BODY_FAT("Body fat", "%", 1),
    LEAN_MASS("Lean mass", "kg", 1),
    BLOOD_PRESSURE("Blood pressure", "mmHg", 0),
}

/** One day in a trend series. [b] is only used for diastolic blood pressure. */
data class DayValue(val date: LocalDate, val a: Double?, val b: Double? = null)
