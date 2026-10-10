package com.odh.healthhub.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthRepository(private val context: Context) {

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"

        private val READ_TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class, ActiveCaloriesBurnedRecord::class,
            HeartRateRecord::class, RestingHeartRateRecord::class, OxygenSaturationRecord::class,
            SleepSessionRecord::class,
            WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class,
            BoneMassRecord::class, BodyWaterMassRecord::class, BasalMetabolicRateRecord::class,
            BloodPressureRecord::class,
        )

        val CORE_PERMISSIONS: Set<String> =
            READ_TYPES.map { HealthPermission.getReadPermission(it) }.toSet() +
                HealthPermission.getWritePermission(BloodPressureRecord::class)

        val ALL_PERMISSIONS: Set<String> = CORE_PERMISSIONS + setOf(
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        )
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)
    val available: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (!available) emptySet() else client.permissionController.getGrantedPermissions()

    suspend fun hasCorePermissions(): Boolean {
        val granted = grantedPermissions()
        // Usable as long as at least the main read permissions are on
        return READ_TYPES.any { HealthPermission.getReadPermission(it) in granted }
    }

    suspend fun hasBackgroundPermission(): Boolean =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in grantedPermissions()

    // ---------- helpers ----------

    private suspend fun <T> safe(block: suspend () -> T): T? =
        try { block() } catch (e: SecurityException) { null } catch (e: IllegalStateException) { null }

    private suspend fun <T : Record> read(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = filter, pageToken = token)
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    private suspend fun agg(metrics: Set<AggregateMetric<*>>, filter: TimeRangeFilter): AggregationResult? =
        safe { client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = filter)) }

    fun appName(pkg: String): String {
        if (pkg == context.packageName) return "Manual entry"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("renpho", true) || pkg.contains("qingniu", true) -> "Renpho"
                pkg.contains("mi.health") || pkg.contains("xiaomi") -> "Mi Fitness"
                pkg.contains("braun", true) || pkg.contains("kaz", true) -> "Braun"
                else -> pkg
            }
        }
    }

    // ---------- one day ----------

    suspend fun daySummary(date: LocalDate): DaySummary {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val day = TimeRangeFilter.between(start, end)
        val sources = mutableSetOf<String>()

        val act = agg(setOf(StepsRecord.COUNT_TOTAL), day)
        val cal = agg(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), day)
        val hr = agg(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX), day)
        val rhr = agg(setOf(RestingHeartRateRecord.BPM_AVG), day)
        listOfNotNull(act, cal, hr, rhr).forEach { r -> sources += r.dataOrigins.map { it.packageName } }

        val spo2 = safe { read(OxygenSaturationRecord::class, day) }.orEmpty()

        // Sleep: sessions that finished between 6pm the previous day and 6pm today
        val sleepWindowStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepWindowEnd = date.atTime(18, 0).atZone(zone).toInstant()
        val sessions = safe {
            read(SleepSessionRecord::class, TimeRangeFilter.between(sleepWindowStart.minus(Duration.ofHours(14)), sleepWindowEnd))
        }.orEmpty().filter { it.endTime > sleepWindowStart && it.endTime <= sleepWindowEnd }

        var deep = 0L; var light = 0L; var rem = 0L; var awake = 0L; var asleepFromStages = 0L
        sessions.forEach { s ->
            s.stages.forEach { st ->
                val m = Duration.between(st.startTime, st.endTime).toMinutes()
                when (st.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> { deep += m; asleepFromStages += m }
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> { light += m; asleepFromStages += m }
                    SleepSessionRecord.STAGE_TYPE_REM -> { rem += m; asleepFromStages += m }
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += m
                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> asleepFromStages += m
                }
            }
        }
        val hasStages = sessions.any { it.stages.isNotEmpty() }
        val sleepTotal = when {
            sessions.isEmpty() -> null
            hasStages -> asleepFromStages
            else -> sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }

        val weight = safe { read(WeightRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val fat = safe { read(BodyFatRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val lean = safe { read(LeanBodyMassRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val bone = safe { read(BoneMassRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val water = safe { read(BodyWaterMassRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val bmr = safe { read(BasalMetabolicRateRecord::class, day) }.orEmpty().maxByOrNull { it.time }
        val bp = safe { read(BloodPressureRecord::class, day) }.orEmpty().sortedBy { it.time }

        (spo2 + sessions + listOfNotNull(weight, fat, lean, bone, water, bmr) + bp)
            .forEach { sources += it.metadata.dataOrigin.packageName }

        return DaySummary(
            date = date,
            steps = act?.get(StepsRecord.COUNT_TOTAL),
            activeKcal = cal?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            hrAvg = hr?.get(HeartRateRecord.BPM_AVG),
            hrMin = hr?.get(HeartRateRecord.BPM_MIN),
            hrMax = hr?.get(HeartRateRecord.BPM_MAX),
            restingHr = rhr?.get(RestingHeartRateRecord.BPM_AVG),
            spo2Avg = spo2.takeIf { it.isNotEmpty() }?.map { it.percentage.value }?.average(),
            sleepMinutes = sleepTotal,
            deepMinutes = deep.takeIf { hasStages },
            lightMinutes = light.takeIf { hasStages },
            remMinutes = rem.takeIf { hasStages },
            awakeMinutes = awake.takeIf { hasStages },
            sleepStart = sessions.minOfOrNull { it.startTime },
            sleepEnd = sessions.maxOfOrNull { it.endTime },
            weightKg = weight?.weight?.inKilograms,
            bodyFatPct = fat?.percentage?.value,
            leanMassKg = lean?.mass?.inKilograms,
            boneMassKg = bone?.mass?.inKilograms,
            waterMassKg = water?.mass?.inKilograms,
            bmrKcal = bmr?.basalMetabolicRate?.inKilocaloriesPerDay,
            bp = bp.map {
                BpReading(
                    it.time,
                    it.systolic.inMillimetersOfMercury,
                    it.diastolic.inMillimetersOfMercury,
                    appName(it.metadata.dataOrigin.packageName),
                )
            },
            sources = sources.map(::appName).toSortedSet(),
        )
    }

    // ---------- trends ----------

    suspend fun series(metric: Metric, days: Int): List<DayValue> {
        val today = LocalDate.now()
        val first = today.minusDays((days - 1).toLong())
        val dates = (0 until days).map { first.plusDays(it.toLong()) }

        fun <T : Any> grouped(m: AggregateMetric<T>, convert: (T) -> Double): suspend () -> Map<LocalDate, Double> = {
            safe {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(m),
                        timeRangeFilter = TimeRangeFilter.between(first.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                ).mapNotNull { g -> g.result[m]?.let { g.startTime.toLocalDate() to convert(it) } }.toMap()
            }.orEmpty()
        }

        val rangeFilter = TimeRangeFilter.between(
            first.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()
        )
        fun Instant.localDate(): LocalDate = atZone(zone).toLocalDate()

        val values: Map<LocalDate, Pair<Double, Double?>> = when (metric) {
            Metric.STEPS -> grouped(StepsRecord.COUNT_TOTAL) { it.toDouble() }().mapValues { it.value to null }
            Metric.ACTIVE_KCAL -> grouped(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) { it.inKilocalories }().mapValues { it.value to null }
            Metric.RESTING_HR -> grouped(RestingHeartRateRecord.BPM_AVG) { it.toDouble() }().mapValues { it.value to null }
            Metric.AVG_HR -> grouped(HeartRateRecord.BPM_AVG) { it.toDouble() }().mapValues { it.value to null }
            Metric.WEIGHT -> grouped(WeightRecord.WEIGHT_AVG) { it.inKilograms }().mapValues { it.value to null }
            Metric.BODY_FAT -> safe { read(BodyFatRecord::class, rangeFilter) }.orEmpty()
                .groupBy { it.time.localDate() }
                .mapValues { (_, v) -> v.maxBy { it.time }.percentage.value to null }
            Metric.LEAN_MASS -> safe { read(LeanBodyMassRecord::class, rangeFilter) }.orEmpty()
                .groupBy { it.time.localDate() }
                .mapValues { (_, v) -> v.maxBy { it.time }.mass.inKilograms to null }
            Metric.SLEEP -> safe {
                read(SleepSessionRecord::class, TimeRangeFilter.between(
                    first.minusDays(1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()))
            }.orEmpty()
                // attribute each night to the day it ended (before 6pm)
                .groupBy { val z = it.endTime.atZone(zone); if (z.hour >= 18) z.toLocalDate().plusDays(1) else z.toLocalDate() }
                .mapValues { (_, sessions) ->
                    val mins = sessions.sumOf { s ->
                        if (s.stages.isEmpty()) Duration.between(s.startTime, s.endTime).toMinutes()
                        else s.stages.filter {
                            it.stage in setOf(
                                SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_LIGHT,
                                SleepSessionRecord.STAGE_TYPE_REM, SleepSessionRecord.STAGE_TYPE_SLEEPING,
                            )
                        }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    }
                    mins / 60.0 to null
                }
            Metric.BLOOD_PRESSURE -> safe { read(BloodPressureRecord::class, rangeFilter) }.orEmpty()
                .groupBy { it.time.localDate() }
                .mapValues { (_, v) ->
                    v.map { it.systolic.inMillimetersOfMercury }.average() to
                        v.map { it.diastolic.inMillimetersOfMercury }.average()
                }
        }
        return dates.map { d -> values[d].let { DayValue(d, it?.first, it?.second) } }
    }

    // ---------- writing ----------

    suspend fun addBloodPressure(time: Instant, systolic: Double, diastolic: Double) {
        client.insertRecords(
            listOf(
                BloodPressureRecord(
                    time = time,
                    zoneOffset = zone.rules.getOffset(time),
                    metadata = Metadata.manualEntry(),
                    systolic = Pressure.millimetersOfMercury(systolic),
                    diastolic = Pressure.millimetersOfMercury(diastolic),
                )
            )
        )
    }
}
