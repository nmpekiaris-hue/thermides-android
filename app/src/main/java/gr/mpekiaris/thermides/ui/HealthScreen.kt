package gr.mpekiaris.thermides.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.mpekiaris.thermides.MainViewModel
import gr.mpekiaris.thermides.health.BpReading
import gr.mpekiaris.thermides.health.DayStat
import gr.mpekiaris.thermides.health.HealthRepo
import gr.mpekiaris.thermides.health.HealthSnapshot
import gr.mpekiaris.thermides.health.HealthStatus
import gr.mpekiaris.thermides.health.WeightReading
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HealthScreen(vm: MainViewModel) {
    val status by vm.healthStatus.collectAsStateWithLifecycle()
    val snapshot by vm.healthSnapshot.collectAsStateWithLifecycle()
    val loading by vm.healthLoading.collectAsStateWithLifecycle()
    val error by vm.healthError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requestPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.refreshHealth() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Υγεία", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refreshHealth() }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Ανανέωση")
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        error?.let { message ->
            item {
                Text(
                    "Δεν διαβάστηκαν τα δεδομένα: $message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when (status) {
            HealthStatus.UNAVAILABLE -> item {
                InfoCard(
                    title = "Το Health Connect δεν είναι διαθέσιμο",
                    body = "Η συσκευή δεν υποστηρίζει το Health Connect ή είναι απενεργοποιημένο.",
                )
            }

            HealthStatus.NEEDS_UPDATE -> item {
                InfoCard(
                    title = "Χρειάζεται ενημέρωση του Health Connect",
                    body = "Εγκατάστησε ή ενημέρωσε το Health Connect από το Play Store και ξαναδοκίμασε.",
                    action = "Άνοιγμα Play Store",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }

            HealthStatus.NO_PERMISSION -> item {
                InfoCard(
                    title = "Σύνδεση με το Health Connect",
                    body = "Η εφαρμογή διαβάζει (δεν γράφει) πίεση, παλμούς, βήματα, ενεργές θερμίδες και βάρος " +
                        "που στέλνουν στο Health Connect το OMRON connect και η εφαρμογή του ρολογιού σου.",
                    action = "Δώσε άδεια ανάγνωσης",
                    onAction = { requestPermissions.launch(HealthRepo.PERMISSIONS) },
                )
            }

            HealthStatus.READY -> {
                val data = snapshot
                if (data != null) {
                    if (data.missingPermissions > 0) {
                        item {
                            InfoCard(
                                title = "Λείπουν ${data.missingPermissions} άδειες",
                                body = "Κάποιες μετρήσεις δεν εμφανίζονται επειδή δεν έχει δοθεί η αντίστοιχη άδεια.",
                                action = "Δώσε τις άδειες",
                                onAction = { requestPermissions.launch(HealthRepo.PERMISSIONS) },
                            )
                        }
                    }
                    item { LatestCard(data) }
                    item { BloodPressureCard(data.bloodPressure) }
                    item { DaysCard(data.days) }
                    item { WeightCard(data.weights) }
                    item {
                        Text(
                            "Το Health Connect δίνει ιστορικό έως 30 ημέρες πριν από τη στιγμή που έδωσες την άδεια. " +
                                "Αν λείπει κάτι, έλεγξε ότι το OMRON connect και η εφαρμογή του ρολογιού " +
                                "έχουν ενεργό τον συγχρονισμό με το Health Connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun kg(value: Double) = String.format(Locale("el", "GR"), "%.1f kg", value)

@Composable
private fun LatestCard(data: HealthSnapshot) {
    val today = data.days.firstOrNull { it.date == LocalDate.now() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Τελευταίες μετρήσεις", style = MaterialTheme.typography.titleMedium)
            val bp = data.bloodPressure.firstOrNull()
            Metric(
                "Πίεση",
                bp?.let { "${it.systolic}/${it.diastolic} mmHg" } ?: "—",
                bp?.let { "${stamp(it.time)} · ${it.source}" },
            )
            HorizontalDivider()
            val hr = data.lastHeartRate
            Metric("Παλμοί", hr?.let { "${it.bpm} bpm" } ?: "—", hr?.let { "${stamp(it.time)} · ${it.source}" })
            data.restingHeartRate?.let {
                HorizontalDivider()
                Metric("Παλμοί ηρεμίας", "${it.bpm} bpm", stamp(it.time))
            }
            HorizontalDivider()
            Metric("Βήματα σήμερα", today?.steps?.let { kcal(it.toInt()) } ?: "—", null)
            HorizontalDivider()
            Metric("Ενεργές θερμίδες σήμερα", today?.activeKcal?.let { "${kcal(it.roundToInt())} kcal" } ?: "—", null)
            HorizontalDivider()
            val weight = data.weights.firstOrNull()
            Metric("Βάρος", weight?.let { kg(it.kg) } ?: "—", weight?.let { "${stamp(it.time)} · ${it.source}" })
        }
    }
}

@Composable
private fun BloodPressureCard(readings: List<BpReading>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Ιστορικό πίεσης", style = MaterialTheme.typography.titleMedium)
            if (readings.isEmpty()) {
                Empty("Δεν βρέθηκαν μετρήσεις πίεσης τις τελευταίες 30 ημέρες.")
            } else {
                val avgSys = readings.map { it.systolic }.average().roundToInt()
                val avgDia = readings.map { it.diastolic }.average().roundToInt()
                Text(
                    "Μέσος όρος ${readings.size} μετρήσεων: $avgSys/$avgDia mmHg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                readings.take(20).forEach { reading ->
                    HistoryRow(stamp(reading.time), "${reading.systolic}/${reading.diastolic}")
                }
            }
        }
    }
}

@Composable
private fun DaysCard(days: List<DayStat>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Δραστηριότητα ανά ημέρα", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                HeaderCell("Ημέρα", 1.3f)
                HeaderCell("Βήματα", 1f)
                HeaderCell("Ενεργές", 1f)
                HeaderCell("Παλμοί", 1.4f)
            }
            if (days.all { it.steps == null && it.activeKcal == null && it.hrAvg == null }) {
                Empty("Δεν βρέθηκαν δεδομένα από το ρολόι.")
            } else {
                days.forEach { day ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Cell(if (day.date == LocalDate.now()) "Σήμερα" else dayShort(day.date), 1.3f)
                        Cell(day.steps?.let { kcal(it.toInt()) } ?: "—", 1f)
                        Cell(day.activeKcal?.let { kcal(it.roundToInt()) } ?: "—", 1f)
                        Cell(
                            if (day.hrAvg != null) "${day.hrAvg} (${day.hrMin ?: "—"}–${day.hrMax ?: "—"})" else "—",
                            1.4f,
                        )
                    }
                }
                Text(
                    "Παλμοί: μέσος όρος (ελάχιστο–μέγιστο)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WeightCard(weights: List<WeightReading>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Ιστορικό βάρους", style = MaterialTheme.typography.titleMedium)
            if (weights.isEmpty()) {
                Empty("Δεν βρέθηκαν μετρήσεις βάρους τις τελευταίες 30 ημέρες.")
            } else {
                if (weights.size > 1) {
                    val change = weights.first().kg - weights.last().kg
                    Text(
                        "Μεταβολή περιόδου: " + String.format(Locale("el", "GR"), "%+.1f kg", change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                }
                weights.take(20).forEach { HistoryRow(stamp(it.time), kg(it.kg)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(left, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(right, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
}
