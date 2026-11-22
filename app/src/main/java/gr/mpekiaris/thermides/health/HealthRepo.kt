package gr.mpekiaris.thermides.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

enum class HealthStatus { UNAVAILABLE, NEEDS_UPDATE, NO_PERMISSION, READY }

/** Θερμίδες που κάηκαν μια ημέρα: [total] = βασικός μεταβολισμός + δραστηριότητα, [active] = μόνο δραστηριότητα. */
data class Burned(val total: Double?, val active: Double?)

data class BpReading(val time: Instant, val systolic: Int, val diastolic: Int, val source: String)

data class WeightReading(val time: Instant, val kg: Double, val source: String)

data class HeartReading(val time: Instant, val bpm: Long, val source: String)

data class DayStat(
    val date: LocalDate,
    val steps: Long?,
    val activeKcal: Double?,
    val hrAvg: Long?,
    val hrMin: Long?,
    val hrMax: Long?,
)

data class HealthSnapshot(
    val bloodPressure: List<BpReading>,
    val weights: List<WeightReading>,
    val lastHeartRate: HeartReading?,
    val restingHeartRate: HeartReading?,
    /** Τελευταίες ημέρες, η πιο πρόσφατη πρώτη. */
    val days: List<DayStat>,
    /** Ονόματα μετρήσεων για τις οποίες δεν έχει δοθεί άδεια. */
    val missing: List<String>,
    /** Βήματα σήμερα ανά εφαρμογή-πηγή (όπως τα έγραψε η καθεμία στο Health Connect). */
    val stepSources: List<SourceSteps>,
    /** Το σύνολο βημάτων σήμερα όπως το υπολογίζει το Health Connect (χωρίς διπλομέτρημα). */
    val stepsTotalToday: Long?,
)

data class SourceSteps(val source: String, val packageName: String, val steps: Long)

/** Ανάγνωση από το Health Connect. Η εφαρμογή δεν γράφει ποτέ εκεί. */
class HealthRepo(context: Context) {
    private val app = context.applicationContext
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(app) }

    companion object {
        val P_BP = HealthPermission.getReadPermission(BloodPressureRecord::class)
        val P_HR = HealthPermission.getReadPermission(HeartRateRecord::class)
        val P_RHR = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val P_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val P_ACTIVE = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val P_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
        val P_TOTAL = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

        val PERMISSIONS: Set<String> = setOf(P_BP, P_HR, P_RHR, P_STEPS, P_ACTIVE, P_TOTAL, P_WEIGHT)

        private val LABELS = linkedMapOf(
            P_BP to "πίεση",
            P_HR to "παλμοί",
            P_RHR to "παλμοί ηρεμίας",
            P_STEPS to "βήματα",
            P_ACTIVE to "ενεργές θερμίδες",
            P_TOTAL to "συνολικές θερμίδες",
            P_WEIGHT to "βάρος",
        )

        /** Το Health Connect δίνει προεπιλεγμένα μόνο τις τελευταίες 30 ημέρες πριν από την άδεια. */
        const val HISTORY_DAYS = 30L
        const val STAT_DAYS = 14L

        fun sourceLabel(packageName: String): String = when {
            packageName.contains("omron", ignoreCase = true) -> "OMRON connect"
            packageName.contains("zepp", ignoreCase = true) || packageName.contains("huami", ignoreCase = true) -> "Zepp"
            packageName.contains("samsung", ignoreCase = true) || packageName.contains("shealth", ignoreCase = true) -> "Samsung Health"
            packageName.contains("fitbit", ignoreCase = true) -> "Fitbit"
            packageName.contains("garmin", ignoreCase = true) -> "Garmin"
            packageName.contains("huawei", ignoreCase = true) -> "Huawei Health"
            packageName.contains("xiaomi", ignoreCase = true) || packageName.contains("mi.health", ignoreCase = true) -> "Mi Fitness"
            packageName.contains("google.android.apps.fitness") -> "Google Fit"
            packageName.contains("withings", ignoreCase = true) -> "Withings"
            else -> packageName.substringAfterLast('.')
        }
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(app)

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun status(): HealthStatus = when (sdkStatus()) {
        HealthConnectClient.SDK_AVAILABLE ->
            if (runCatching { granted() }.getOrDefault(emptySet()).any { it in PERMISSIONS }) HealthStatus.READY
            else HealthStatus.NO_PERMISSION
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.UNAVAILABLE
    }

    /** Θερμίδες που κάηκαν μια ημέρα (για σήμερα: μέχρι τώρα), ή null αν δεν υπάρχει άδεια. */
    suspend fun burned(day: LocalDate): Burned? {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return null
        val granted = granted()
        if (P_ACTIVE !in granted && P_TOTAL !in granted) return null
        val filter = TimeRangeFilter.between(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
        // Ξεχωριστές κλήσεις: μια μετρική χωρίς άδεια ή με σφάλμα δεν πρέπει να ρίξει την άλλη.
        val total = if (P_TOTAL in granted) runCatching {
            client.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), filter))[
                TotalCaloriesBurnedRecord.ENERGY_TOTAL
            ]?.inKilocalories
        }.getOrNull() else null
        val active = if (P_ACTIVE in granted) runCatching {
            client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), filter))[
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            ]?.inKilocalories
        }.getOrNull() else null
        return Burned(total = total?.takeIf { it > 0 }, active = active?.takeIf { it > 0 })
    }

    suspend fun snapshot(): HealthSnapshot {
        val granted = granted()
        val since = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS)
        val range = TimeRangeFilter.after(since)

        val bp = if (P_BP in granted) {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 60,
                )
            ).records.map {
                BpReading(
                    time = it.time,
                    systolic = Math.round(it.systolic.inMillimetersOfMercury).toInt(),
                    diastolic = Math.round(it.diastolic.inMillimetersOfMercury).toInt(),
                    source = sourceLabel(it.metadata.dataOrigin.packageName),
                )
            }
        } else emptyList()

        val weights = if (P_WEIGHT in granted) {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 60,
                )
            ).records.map {
                WeightReading(it.time, it.weight.inKilograms, sourceLabel(it.metadata.dataOrigin.packageName))
            }
        } else emptyList()

        val lastHr = if (P_HR in granted) {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(7, ChronoUnit.DAYS)),
                    ascendingOrder = false,
                    pageSize = 5,
                )
            ).records
                .flatMap { rec ->
                    val source = sourceLabel(rec.metadata.dataOrigin.packageName)
                    rec.samples.map { HeartReading(it.time, it.beatsPerMinute, source) }
                }
                .maxByOrNull { it.time }
        } else null

        val resting = if (P_RHR in granted) {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 1,
                )
            ).records.firstOrNull()?.let {
                HeartReading(it.time, it.beatsPerMinute, sourceLabel(it.metadata.dataOrigin.packageName))
            }
        } else null

        return HealthSnapshot(
            bloodPressure = bp,
            weights = weights,
            lastHeartRate = lastHr,
            restingHeartRate = resting,
            days = dayStats(granted),
            missing = LABELS.filterKeys { it !in granted }.values.toList(),
            stepSources = if (P_STEPS in granted) stepSourcesToday() else emptyList(),
            stepsTotalToday = if (P_STEPS in granted) {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(
                            LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()
                        ),
                    )
                )[StepsRecord.COUNT_TOTAL]
            } else null,
        )
    }

    /** Διαβάζει όλες τις εγγραφές βημάτων της ημέρας και τις αθροίζει ανά εφαρμογή που τις έγραψε. */
    private suspend fun stepSourcesToday(): List<SourceSteps> {
        val today = LocalDate.now()
        val filter = TimeRangeFilter.between(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
        val totals = LinkedHashMap<String, Long>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 5000,
                    pageToken = token,
                )
            )
            response.records.forEach { rec ->
                val pkg = rec.metadata.dataOrigin.packageName
                totals[pkg] = (totals[pkg] ?: 0L) + rec.count
            }
            token = response.pageToken
            pages++
        } while (token != null && pages < 10)
        return totals.map { SourceSteps(sourceLabel(it.key), it.key, it.value) }.sortedByDescending { it.steps }
    }

    private suspend fun dayStats(granted: Set<String>): List<DayStat> {
        val today = LocalDate.now()
        val from = today.minusDays(STAT_DAYS - 1)
        val filter = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay())

        val steps = HashMap<LocalDate, Long>()
        val active = HashMap<LocalDate, Double>()
        val hr = HashMap<LocalDate, Triple<Long?, Long?, Long?>>()

        // Ξεχωριστή κλήση ανά άδεια: μία μετρική χωρίς άδεια θα έριχνε όλη τη συνάθροιση.
        if (P_STEPS in granted) {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), filter, Period.ofDays(1))
            ).forEach { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { steps[g.startTime.toLocalDate()] = it } }
        }
        if (P_ACTIVE in granted) {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), filter, Period.ofDays(1)
                )
            ).forEach { g ->
                g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                    active[g.startTime.toLocalDate()] = it.inKilocalories
                }
            }
        }
        if (P_HR in granted) {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
                    filter,
                    Period.ofDays(1),
                )
            ).forEach { g ->
                hr[g.startTime.toLocalDate()] = Triple(
                    g.result[HeartRateRecord.BPM_AVG],
                    g.result[HeartRateRecord.BPM_MIN],
                    g.result[HeartRateRecord.BPM_MAX],
                )
            }
        }

        return (0 until STAT_DAYS).map { today.minusDays(it) }.map { d ->
            DayStat(d, steps[d], active[d], hr[d]?.first, hr[d]?.second, hr[d]?.third)
        }
    }
}
