package gr.mpekiaris.thermides.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.mpekiaris.thermides.DayUi
import gr.mpekiaris.thermides.FoodRow
import gr.mpekiaris.thermides.MainViewModel
import gr.mpekiaris.thermides.data.FoodEntry
import gr.mpekiaris.thermides.data.Meal
import gr.mpekiaris.thermides.fasting.Fasting
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val fastHours by vm.fastHours.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()
    val activeKcal by vm.activeKcal.collectAsStateWithLifecycle()
    val hasApiKey by vm.hasApiKey.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val isToday = ui.date == LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DayHeader(
                    date = ui.date,
                    canGoForward = !isToday,
                    onBack = { vm.shiftDay(-1) },
                    onForward = { vm.shiftDay(1) },
                    onToday = { vm.goToday() },
                )
            }
            item { ProgressCard(total = ui.total, goal = goal, activeKcal = activeKcal) }
            if (isToday) {
                item { FastingCard(times = ui.recentTimes, fastHours = fastHours) }
            }
            if (frequent.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Συχνά φαγητά")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(frequent) { food ->
                                AssistChip(
                                    onClick = { vm.quickAdd(food) },
                                    label = { Text("${food.name} · ${food.kcal}") },
                                )
                            }
                        }
                    }
                }
            }
            if (ui.rows.isEmpty()) {
                item {
                    Text(
                        "Δεν έχεις καταγράψει φαγητό αυτή την ημέρα.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Meal.entries.forEach { meal ->
                    val rows = ui.rows.filter { Meal.of(it.entry.meal) == meal }
                    if (rows.isNotEmpty()) {
                        item(key = "meal-${meal.name}") { MealCard(meal, rows, onEdit = { editing = it }) }
                    }
                }
            }
            item { WeekCard(ui, goal) }
            if (ui.rows.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Μηδενισμός ημέρας", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Φαγητό") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        FoodDialog(vm = vm, date = ui.date, existing = null, hasApiKey = hasApiKey, onDismiss = { adding = false })
    }
    editing?.let { entry ->
        FoodDialog(vm = vm, date = ui.date, existing = entry, hasApiKey = hasApiKey, onDismiss = { editing = null })
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Μηδενισμός ημέρας") },
            text = {
                Text(
                    "Θα διαγραφούν και τα ${ui.rows.size} φαγητά (${kcal(ui.total)} kcal) " +
                        "της ημέρας «${dayTitle(ui.date)}». Δεν αναιρείται."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.resetDay(); confirmReset = false }) {
                    Text("Διαγραφή", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Άκυρο") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun DayHeader(
    date: LocalDate,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Προηγούμενη ημέρα")
        }
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onToday).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(dayTitle(date), style = MaterialTheme.typography.titleLarge)
            if (canGoForward) {
                Text(
                    "πάτα για σήμερα",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Επόμενη ημέρα")
        }
    }
}

@Composable
private fun ProgressCard(total: Int, goal: Int, activeKcal: Double?) {
    val over = total > goal
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (goal > 0) (total.toFloat() / goal).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.size(132.dp),
                    color = color,
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(kcal(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "από ${kcal(goal)} kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (over) {
                    Stat("Πάνω από τον στόχο", "${kcal(total - goal)} kcal", color)
                } else {
                    Stat("Απομένουν", "${kcal(goal - total)} kcal", color)
                }
                if (activeKcal != null) {
                    val active = activeKcal.roundToInt()
                    Stat("Ενεργές (άσκηση)", "${kcal(active)} kcal")
                    Stat("Καθαρές (φαγητό − ενεργές)", "${kcal(total - active)} kcal")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FastingCard(times: List<Long>, fastHours: Int) {
    // Η κάρτα «ζει»: ξαναϋπολογίζεται κάθε μισό λεπτό.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val status = Fasting.status(times, now, fastHours)
    val open = status is Fasting.Status.Open

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (open) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Διαλειμματική νηστεία ${Fasting.label(fastHours)}",
                style = MaterialTheme.typography.labelLarge,
            )
            when (status) {
                is Fasting.Status.Open -> {
                    Text(
                        "Παράθυρο ανοιχτό – κλείνει ${hm(status.closesAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Απομένουν ${duration(status.closesAt - now)}", style = MaterialTheme.typography.bodyMedium)
                    val window = (24 - fastHours) * Fasting.HOUR
                    LinearProgressIndicator(
                        progress = { (1f - (status.closesAt - now).toFloat() / window).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is Fasting.Status.Closed -> {
                    val elapsed = now - status.lastMeal
                    val done = now >= status.eatAgainAt
                    Text(
                        if (done) "Νηστεία ${duration(elapsed)} – μπορείς να φας"
                        else "Νηστεία ${duration(elapsed)} – τρως ξανά ${hmRelative(status.eatAgainAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (done) "Συμπλήρωσες τις $fastHours ώρες. Το παράθυρο ανοίγει με το πρώτο γεύμα."
                        else "Τελευταίο γεύμα ${hmRelative(status.lastMeal)} · απομένουν ${duration(status.eatAgainAt - now)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { (elapsed.toFloat() / (fastHours * Fasting.HOUR)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Fasting.Status.NoData -> {
                    Text(
                        "Το παράθυρο ανοίγει με το πρώτο γεύμα",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCard(meal: Meal, rows: List<FoodRow>, onEdit: (FoodEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${kcal(rows.sumOf { it.entry.kcal })} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(row.entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        hm(row.entry.time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (row.outsideWindow) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text(
                                    "εκτός παραθύρου",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text("${kcal(row.entry.kcal)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(ui: DayUi, goal: Int) {
    if (ui.week.isEmpty()) return
    val max = maxOf(goal, ui.week.maxOf { it.second }, 1)
    val average = ui.week.filter { it.second > 0 }.map { it.second }.average().takeIf { !it.isNaN() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("7 ημέρες", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (average != null) {
                    Text(
                        "μ.ό. ${kcal(average.roundToInt())} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.week.forEach { (date, value) ->
                    val selected = date == ui.date
                    val barColor = when {
                        value > goal -> MaterialTheme.colorScheme.error
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (value > 0) value.toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((value.toFloat() / max).coerceIn(0.01f, 1f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .then(Modifier)
                            ) {
                                Surface(color = barColor, modifier = Modifier.fillMaxSize()) {}
                            }
                        }
                        Text(
                            weekday(date),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                "Στόχος ${kcal(goal)} kcal · κόκκινο = πάνω από τον στόχο",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
