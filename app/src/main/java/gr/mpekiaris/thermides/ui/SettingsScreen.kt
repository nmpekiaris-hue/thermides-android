package gr.mpekiaris.thermides.ui

import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gr.mpekiaris.thermides.MainViewModel
import gr.mpekiaris.thermides.ai.ClaudeClient
import gr.mpekiaris.thermides.fasting.Fasting

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val goal by vm.goal.collectAsStateWithLifecycle()
    val fastHours by vm.fastHours.collectAsStateWithLifecycle()
    val hasApiKey by vm.hasApiKey.collectAsStateWithLifecycle()
    val fastStart by vm.fastStart.collectAsStateWithLifecycle()
    val goalWeight by vm.goalWeight.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ρυθμίσεις", style = MaterialTheme.typography.titleLarge)

        // --- Στόχος ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ημερήσιος στόχος", style = MaterialTheme.typography.titleMedium)
                var goalText by remember(goal) { mutableStateOf(goal.toString()) }
                val parsed = goalText.toIntOrNull()
                val valid = parsed != null && parsed in 500..10_000
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { text -> goalText = text.filter { it.isDigit() }.take(5) },
                        label = { Text("kcal την ημέρα") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { parsed?.let { vm.setGoal(it) } }, enabled = valid && parsed != goal) {
                        Text("Αποθήκευση")
                    }
                }
                if (!valid) {
                    Text(
                        "Δώσε τιμή από 500 έως 10.000 kcal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // --- Στόχος βάρους ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Στόχος βάρους", style = MaterialTheme.typography.titleMedium)
                var weightText by remember(goalWeight) {
                    mutableStateOf(goalWeight?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "")
                }
                val parsedWeight = weightText.replace(',', '.').toFloatOrNull()
                val weightValid = parsedWeight != null && parsedWeight in 30f..300f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { text ->
                            weightText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                        },
                        label = { Text("kg") },
                        singleLine = true,
                        isError = weightText.isNotBlank() && !weightValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.setGoalWeight(parsedWeight) },
                        enabled = weightValid && parsedWeight != goalWeight,
                    ) { Text("Αποθήκευση") }
                }
                if (goalWeight != null) {
                    TextButton(onClick = { vm.setGoalWeight(null) }) { Text("Κατάργηση στόχου") }
                }
                Text(
                    "Το καθημερινό ζύγισμα γίνεται από την αρχική οθόνη. Αν το βάρος έρχεται και από το " +
                        "Garmin μέσω Health Connect, μπαίνει αυτόματα στην πορεία.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Νηστεία ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Διαλειμματική νηστεία", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fasting.PLANS.forEach { hours ->
                        FilterChip(
                            selected = fastHours == hours,
                            onClick = { vm.setFastHours(hours) },
                            label = { Text(Fasting.label(hours)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Το παράθυρο φαγητού ανοίγει στις", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> vm.setFastStart(h * 60 + m) },
                            fastStart / 60,
                            fastStart % 60,
                            true,
                        ).show()
                    }) { Text(Fasting.clock(fastStart)) }
                }
                Text(
                    "Σταθερό παράθυρο φαγητού κάθε μέρα: ${Fasting.windowLabel(fastStart, fastHours)} " +
                        "(νηστεία $fastHours ώρες). Ό,τι φας εκτός αυτών των ωρών σημειώνεται «εκτός παραθύρου».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Claude API ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Εκτίμηση θερμίδων με Claude", style = MaterialTheme.typography.titleMedium)
                var keyText by remember { mutableStateOf("") }
                var message by remember { mutableStateOf<String?>(null) }
                Text(
                    if (hasApiKey) "Υπάρχει αποθηκευμένο API key ✓" else "Δεν έχει οριστεί API key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it.trim() },
                    label = { Text(if (hasApiKey) "Νέο API key (αντικατάσταση)" else "Claude API key") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = keyText.length >= 20,
                        onClick = {
                            message = if (vm.saveApiKey(keyText)) {
                                keyText = ""
                                "Αποθηκεύτηκε κρυπτογραφημένο στη συσκευή."
                            } else {
                                "Η ασφαλής αποθήκευση δεν είναι διαθέσιμη σε αυτή τη συσκευή."
                            }
                        },
                    ) { Text("Αποθήκευση") }
                    if (hasApiKey) {
                        TextButton(onClick = { vm.clearApiKey(); message = "Το API key διαγράφηκε." }) {
                            Text("Διαγραφή", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Το κλειδί μένει μόνο στο κινητό (EncryptedSharedPreferences) και στέλνεται μόνο στο " +
                        "api.anthropic.com. Κάθε εκτίμηση χρεώνεται στον λογαριασμό σου στο Claude API " +
                        "(μοντέλο ${ClaudeClient.MODEL}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Health Connect ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Διαχείριση αδειών και εφαρμογών που στέλνουν δεδομένα (OMRON connect, ρολόι).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Άνοιγμα Health Connect") }
            }
        }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Text(
            "Θερμίδες ${version ?: ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
