package gr.mpekiaris.thermides

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.mpekiaris.thermides.ai.ClaudeClient
import gr.mpekiaris.thermides.data.AppDb
import gr.mpekiaris.thermides.data.FoodEntry
import gr.mpekiaris.thermides.data.FrequentFood
import gr.mpekiaris.thermides.data.Meal
import gr.mpekiaris.thermides.data.Prefs
import gr.mpekiaris.thermides.fasting.Fasting
import gr.mpekiaris.thermides.health.HealthRepo
import gr.mpekiaris.thermides.health.HealthSnapshot
import gr.mpekiaris.thermides.health.HealthStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class FoodRow(val entry: FoodEntry, val outsideWindow: Boolean)

data class DayUi(
    val date: LocalDate = LocalDate.now(),
    val rows: List<FoodRow> = emptyList(),
    val total: Int = 0,
    /** 7 ημέρες που τελειώνουν στην επιλεγμένη: ημερομηνία -> kcal. */
    val week: List<Pair<LocalDate, Int>> = emptyList(),
    /** Ώρες όλων των πρόσφατων γευμάτων (αύξουσα σειρά), για την κάρτα νηστείας. */
    val recentTimes: List<Long> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).food()
    private val prefs = Prefs(app)
    private val health = HealthRepo(app)

    val day = MutableStateFlow(LocalDate.now())
    val goal = MutableStateFlow(prefs.goal)
    val fastHours = MutableStateFlow(prefs.fastHours)
    val hasApiKey = MutableStateFlow(false)

    val healthStatus = MutableStateFlow(HealthStatus.UNAVAILABLE)
    val healthSnapshot = MutableStateFlow<HealthSnapshot?>(null)
    val healthLoading = MutableStateFlow(false)
    val healthError = MutableStateFlow<String?>(null)

    /** Ενεργές θερμίδες της επιλεγμένης ημέρας από το Health Connect (null = δεν υπάρχουν). */
    val activeKcal = MutableStateFlow<Double?>(null)

    private fun startOf(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val ui: StateFlow<DayUi> = combine(
        day,
        day.flatMapLatest { d -> dao.between(startOf(d.minusDays(7)), startOf(d.plusDays(1))) },
        fastHours,
    ) { d, all, fast ->
        // Το flatMapLatest μπορεί να δώσει για μια στιγμή τη λίστα της προηγούμενης ημέρας· είναι ακίνδυνο,
        // γιατί φιλτράρουμε ξανά με βάση την ημερομηνία.
        val sorted = all.sortedBy { it.time }
        val outside = Fasting.outside(sorted.map { it.time }, fast)
        val from = startOf(d)
        val to = startOf(d.plusDays(1))
        val rows = sorted.indices
            .filter { sorted[it].time in from until to }
            .map { FoodRow(sorted[it], outside[it]) }
        val week = (6 downTo 0).map { d.minusDays(it.toLong()) }.map { date ->
            val a = startOf(date)
            val b = startOf(date.plusDays(1))
            date to sorted.filter { it.time in a until b }.sumOf { it.kcal }
        }
        DayUi(d, rows, rows.sumOf { it.entry.kcal }, week, sorted.map { it.time })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUi())

    val frequent: StateFlow<List<FrequentFood>> =
        dao.frequent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { hasApiKey.value = prefs.apiKey != null }
        viewModelScope.launch { day.collectLatest { loadActiveKcal(it) } }
        refreshHealth()
    }

    // ---- Ημέρα ----

    fun shiftDay(delta: Long) {
        val next = day.value.plusDays(delta)
        if (!next.isAfter(LocalDate.now())) day.value = next
    }

    fun goToday() {
        day.value = LocalDate.now()
    }

    // ---- Φαγητά ----

    fun timeOn(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun add(name: String, kcal: Int, time: Long, meal: Meal) = viewModelScope.launch {
        dao.insert(FoodEntry(name = name.trim(), kcal = kcal, time = time, meal = meal.name))
    }

    fun update(entry: FoodEntry) = viewModelScope.launch { dao.update(entry) }

    fun delete(entry: FoodEntry) = viewModelScope.launch { dao.delete(entry) }

    /** Συχνό φαγητό με ένα πάτημα: τώρα, στην επιλεγμένη ημέρα. */
    fun quickAdd(food: FrequentFood) {
        val now = LocalTime.now().withSecond(0).withNano(0)
        add(food.name, food.kcal, timeOn(day.value, now), Meal.forHour(now.hour))
    }

    fun resetDay() = viewModelScope.launch {
        val d = day.value
        dao.deleteBetween(startOf(d), startOf(d.plusDays(1)))
    }

    // ---- Ρυθμίσεις ----

    fun setGoal(value: Int) {
        prefs.goal = value
        goal.value = value
    }

    fun setFastHours(value: Int) {
        prefs.fastHours = value
        fastHours.value = value
    }

    /** @return false αν η ασφαλής αποθήκευση δεν είναι διαθέσιμη στη συσκευή. */
    fun saveApiKey(value: String): Boolean {
        if (!prefs.secureAvailable) return false
        prefs.apiKey = value
        hasApiKey.value = prefs.apiKey != null
        return true
    }

    fun clearApiKey() {
        prefs.apiKey = null
        hasApiKey.value = false
    }

    // ---- Claude ----

    suspend fun estimate(text: String?, jpeg: ByteArray?): Result<ClaudeClient.Estimate> {
        val key = prefs.apiKey
            ?: return Result.failure(IllegalStateException("Βάλε πρώτα το Claude API key στις Ρυθμίσεις."))
        return try {
            Result.success(ClaudeClient.estimate(key, text, jpeg))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Health Connect ----

    fun refreshHealth() = viewModelScope.launch {
        healthLoading.value = true
        healthError.value = null
        try {
            val status = health.status()
            healthStatus.value = status
            if (status == HealthStatus.READY) {
                healthSnapshot.value = health.snapshot()
            } else {
                healthSnapshot.value = null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            healthError.value = e.message ?: e.javaClass.simpleName
        } finally {
            healthLoading.value = false
        }
        loadActiveKcal(day.value)
    }

    private suspend fun loadActiveKcal(date: LocalDate) {
        activeKcal.value = try {
            health.activeKcal(date)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
