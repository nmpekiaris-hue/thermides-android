package gr.mpekiaris.thermides.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.mpekiaris.thermides.DayUi
import gr.mpekiaris.thermides.FoodRow
import gr.mpekiaris.thermides.MainViewModel
import gr.mpekiaris.thermides.WeightPoint
import gr.mpekiaris.thermides.data.FoodEntry
import gr.mpekiaris.thermides.data.Meal
import gr.mpekiaris.thermides.fasting.Fasting
import gr.mpekiaris.thermides.health.Burned
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun TodayScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val fastHours by vm.fastHours.collectAsStateWithLifecycle()
    val fastStart by vm.fastStart.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()
    val burned by vm.burned.collectAsStateWithLifecycle()
    val hasApiKey by vm.hasApiKey.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val goalWeight by vm.goalWeight.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var weighing by remember { mutableStateOf(false) }

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
            item { HeroCard(total = ui.total, goal = goal, burned = burned, isToday = isToday) }
            if (isToday) {
                item { WeightCard(points = weights, goal = goalWeight, onLog = { weighing = true }) }
                item { FastingCard(times = ui.recentTimes, fastHours = fastHours, fastStart = fastStart) }
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
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                    border = null,
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
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        FoodDialog(vm = vm, date = ui.date, existing = null, hasApiKey = hasApiKey, onDismiss = { adding = false })
    }
    editing?.let { entry ->
        FoodDialog(vm = vm, date = ui.date, existing = entry, hasApiKey = hasApiKey, onDismiss = { editing = null })
    }
    if (weighing) {
        WeightDialog(
            vm = vm,
            today = weights.lastOrNull { it.date == LocalDate.now() },
            latest = weights.lastOrNull(),
            onDismiss = { weighing = false },
        )
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
            Text(
                dayTitle(date),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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

// ---------------------------------------------------------------------------------------------
// Θερμίδες: φαγητό, κατανάλωση από το ρολόι, έλλειμμα
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeroCard(total: Int, goal: Int, burned: Burned?, isToday: Boolean) {
    val over = total > goal
    val gradient = if (over) listOf(HeroRed, HeroOrange) else listOf(HeroGreen, HeroTeal, HeroBlue)
    val soft = Color.White.copy(alpha = 0.8f)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (goal > 0) (total.toFloat() / goal).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.size(128.dp),
                        color = Color.White,
                        strokeWidth = 12.dp,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            kcal(total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text("από ${kcal(goal)}", style = MaterialTheme.typography.labelMedium, color = soft)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (over) "Πάνω από τον στόχο" else "Απομένουν",
                        style = MaterialTheme.typography.labelLarge,
                        color = soft,
                    )
                    Text(
                        "${kcal(if (over) total - goal else goal - total)} kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text("Στόχος ${kcal(goal)} kcal", style = MaterialTheme.typography.bodySmall, color = soft)
                }
            }

            // Φαγητό − κατανάλωση (ρολόι) = ισοζύγιο
            val spent = burned?.total
            val balance = spent?.let { it.roundToInt() - total }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroTile("🍽 Φαγητό", kcal(total))
                when {
                    spent != null -> HeroTile("🔥 Κατανάλωση", kcal(spent.roundToInt()))
                    burned?.active != null -> HeroTile("🔥 Ενεργές μόνο", kcal(burned.active.roundToInt()))
                    else -> HeroTile("🔥 Κατανάλωση", "—")
                }
                when {
                    balance == null -> HeroTile("⚖ Έλλειμμα", "—")
                    balance >= 0 -> HeroTile("⚖ Έλλειμμα", kcal(balance))
                    else -> HeroTile("⚖ Πλεόνασμα", kcal(-balance))
                }
            }
            Text(
                when {
                    spent == null ->
                        "Δεν ήρθαν συνολικές θερμίδες από το ρολόι, οπότε δεν υπολογίζεται έλλειμμα. Δες την καρτέλα «Υγεία»."
                    isToday -> "Κατανάλωση από το ρολόι μέχρι τον τελευταίο συγχρονισμό· το έλλειμμα αλλάζει μέσα στη μέρα."
                    else -> "Κατανάλωση = βασικός μεταβολισμός + δραστηριότητα, από το ρολόι."
                },
                style = MaterialTheme.typography.labelSmall,
                color = soft,
            )
        }
    }
}

@Composable
private fun RowScope.HeroTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Βάρος
// ---------------------------------------------------------------------------------------------

@Composable
private fun WeightCard(points: List<WeightPoint>, goal: Float?, onLog: () -> Unit) {
    val today = LocalDate.now()
    val latest = points.lastOrNull()
    val weighedToday = latest?.date == today
    val previous = latest?.let { l -> points.lastOrNull { it.date < l.date } }
    val first = points.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "⚖ Βάρος",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        latest?.let { kgText(it.kg) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        when {
                            latest == null -> "Ξεκίνα με το πρώτο σου ζύγισμα."
                            weighedToday -> "Σημερινό ζύγισμα ✓"
                            else -> "Δεν ζυγίστηκες σήμερα · τελευταίο ${dayShort(latest.date)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (weighedToday) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (weighedToday || latest == null) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.error,
                    )
                }
                if (weighedToday) {
                    FilledTonalButton(onClick = onLog) { Text("Αλλαγή") }
                } else {
                    Button(onClick = onLog) { Text("Ζύγισμα") }
                }
            }

            if (latest != null && first != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightStat(
                        if (previous != null && previous.date == latest.date.minusDays(1)) "Από χθες" else "Από προηγ.",
                        previous?.let { kgDelta(latest.kg - it.kg) } ?: "—",
                    )
                    WeightStat("Από ${dayMonth(first.date)}", if (first !== latest) kgDelta(latest.kg - first.kg) else "—")
                    WeightStat("Στόχος", goal?.let { kgText(it.toDouble()) } ?: "—")
                    WeightStat(
                        "Απομένουν",
                        goal?.let { g ->
                            val left = latest.kg - g
                            if (left <= 0.049) "🎉" else kgText(left)
                        } ?: "—",
                    )
                }
            }

            if (goal != null && latest != null && first != null && first.kg - goal > 0.05) {
                val done = ((first.kg - latest.kg) / (first.kg - goal)).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { done },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                )
                Text(
                    "${(done * 100).roundToInt()}% της διαδρομής προς τον στόχο",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else if (goal == null) {
                Text(
                    "Όρισε στόχο βάρους στις Ρυθμίσεις για να βλέπεις πόσο απομένει.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            if (points.size >= 2) {
                WeightChart(points.takeLast(30), goal)
                Row(Modifier.fillMaxWidth()) {
                    val shown = points.takeLast(30)
                    Text(
                        dayMonth(shown.first().date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        dayMonth(shown.last().date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.WeightStat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
        )
    }
}

/** Γραμμή πορείας βάρους (στον άξονα x οι πραγματικές ημέρες) και διακεκομμένη γραμμή στόχου. */
@Composable
private fun WeightChart(points: List<WeightPoint>, goal: Float?) {
    val line = MaterialTheme.colorScheme.tertiary
    val goalColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val values = points.map { it.kg } + listOfNotNull(goal?.toDouble())
        val min = values.min() - 0.3
        val max = values.max() + 0.3
        val span = (max - min).coerceAtLeast(0.1)
        val firstDay = points.first().date.toEpochDay()
        val days = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1)
        val pad = 6.dp.toPx()

        fun x(p: WeightPoint) = pad + (size.width - 2 * pad) * (p.date.toEpochDay() - firstDay).toFloat() / days
        fun y(kg: Double) = pad + (size.height - 2 * pad) * (1f - ((kg - min) / span).toFloat())

        if (goal != null) {
            val gy = y(goal.toDouble())
            drawLine(
                color = goalColor,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p), y(p.kg)) else path.lineTo(x(p), y(p.kg))
        }
        drawPath(path, color = line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { p -> drawCircle(color = line, radius = 3.5.dp.toPx(), center = Offset(x(p), y(p.kg))) }
    }
}

@Composable
private fun WeightDialog(vm: MainViewModel, today: WeightPoint?, latest: WeightPoint?, onDismiss: () -> Unit) {
    var text by remember {
        mutableStateOf((today ?: latest)?.let { String.format(java.util.Locale.US, "%.1f", it.kg) } ?: "")
    }
    val value = text.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value in 20.0..400.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Σημερινό ζύγισμα") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Βάρος (kg)") },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Ζυγίσου το πρωί, νηστικός, πάντα την ίδια ώρα, για να συγκρίνονται οι μέρες μεταξύ τους.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (today != null && today.manual) {
                    TextButton(onClick = { vm.deleteWeight(today.date); onDismiss() }) {
                        Text("Διαγραφή σημερινού ζυγίσματος", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    value?.let { vm.saveWeight(LocalDate.now(), it) }
                    onDismiss()
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Νηστεία
// ---------------------------------------------------------------------------------------------

@Composable
private fun FastingCard(times: List<Long>, fastHours: Int, fastStart: Int) {
    // Η κάρτα «ζει»: ξαναϋπολογίζεται κάθε μισό λεπτό.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val status = Fasting.status(times, now, fastStart, fastHours, ZoneId.systemDefault())
    val open = status is Fasting.Status.Open
    val content = if (open) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (open) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
            contentColor = content,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "⏱ Νηστεία ${Fasting.label(fastHours)} · παράθυρο ${Fasting.windowLabel(fastStart, fastHours)}",
                style = MaterialTheme.typography.labelLarge,
            )
            when (status) {
                is Fasting.Status.Open -> {
                    Text(
                        "Παράθυρο ανοιχτό – κλείνει ${hm(status.closesAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Απομένουν ${duration(status.closesAt - now)}", style = MaterialTheme.typography.bodyMedium)
                    val window = Fasting.windowMinutes(fastHours) * 60_000L
                    LinearProgressIndicator(
                        progress = { (1f - (status.closesAt - now).toFloat() / window).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = content.copy(alpha = 0.15f),
                    )
                }
                is Fasting.Status.Closed -> {
                    Text(
                        "Νηστεία ${duration(now - status.since)} – τρως ξανά ${hmRelative(status.opensAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Το παράθυρο ανοίγει σε ${duration(status.opensAt - now)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val total = (status.opensAt - status.since).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { ((now - status.since).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = content.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Γεύματα
// ---------------------------------------------------------------------------------------------

@Composable
private fun MealCard(meal: Meal, rows: List<FoodRow>, onEdit: (FoodEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(meal.accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Text(meal.emoji) }
                Text(
                    meal.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Surface(color = meal.accent.copy(alpha = 0.22f), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "${kcal(rows.sumOf { it.entry.kcal })} kcal",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
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
                    Text(kcal(row.entry.kcal), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7 ημέρες
// ---------------------------------------------------------------------------------------------

@Composable
private fun WeekCard(ui: DayUi, goal: Int) {
    if (ui.week.isEmpty()) return
    val max = maxOf(goal, ui.week.maxOf { it.second }, 1)
    val average = ui.week.filter { it.second > 0 }.map { it.second }.average().takeIf { !it.isNaN() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "📊 7 ημέρες",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
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
                                    .background(barColor)
                            )
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
