package gr.mpekiaris.thermides.ui

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import gr.mpekiaris.thermides.MainViewModel
import gr.mpekiaris.thermides.ai.ImageUtil
import gr.mpekiaris.thermides.data.FoodEntry
import gr.mpekiaris.thermides.data.Meal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Προσθήκη (existing == null) ή επεξεργασία φαγητού, με εκτίμηση θερμίδων από το Claude. */
@Composable
fun FoodDialog(
    vm: MainViewModel,
    date: LocalDate,
    existing: FoodEntry?,
    hasApiKey: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val startTime = remember(existing) {
        existing?.let { Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalTime() }
            ?: LocalTime.now()
    }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kcalText by remember(existing) { mutableStateOf(existing?.kcal?.toString() ?: "") }
    var hour by remember(existing) { mutableIntStateOf(startTime.hour) }
    var minute by remember(existing) { mutableIntStateOf(startTime.minute) }
    var meal by remember(existing) { mutableStateOf(existing?.let { Meal.of(it.meal) } ?: Meal.forHour(startTime.hour)) }
    var mealTouched by remember(existing) { mutableStateOf(existing != null) }

    var busy by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    fun estimate(photo: Uri?) {
        if (busy) return
        busy = true
        aiNote = null
        aiError = null
        scope.launch {
            try {
                val jpeg = photo?.let { withContext(Dispatchers.IO) { ImageUtil.loadJpeg(context, it) } }
                vm.estimate(name.trim().ifBlank { null }, jpeg)
                    .onSuccess {
                        if (it.name.isNotBlank()) name = it.name
                        kcalText = it.kcal.toString()
                        aiNote = it.note.ifBlank { null }
                    }
                    .onFailure { aiError = it.message ?: "Η εκτίμηση απέτυχε." }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError = e.message ?: "Δεν διαβάστηκε η φωτογραφία."
            } finally {
                busy = false
            }
        }
    }

    val photoUri = remember {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "meal.jpg"))
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) estimate(photoUri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) estimate(uri)
    }

    val kcalValue = kcalText.trim().toIntOrNull()
    val valid = name.isNotBlank() && kcalValue != null && kcalValue in 0..20_000

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Νέο φαγητό" else "Επεξεργασία") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Τι έφαγες") },
                    placeholder = { Text("π.χ. 2 αυγά με 1 φέτα ψωμί") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { text -> kcalText = text.filter { it.isDigit() }.take(5) },
                    label = { Text("Θερμίδες (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- Εκτίμηση με Claude ---
                if (hasApiKey) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { estimate(null) }, enabled = !busy && name.isNotBlank()) {
                            Text("✨ Εκτίμηση")
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    takePicture.launch(photoUri)
                                } catch (e: Exception) {
                                    aiError = "Δεν βρέθηκε εφαρμογή κάμερας."
                                }
                            },
                            enabled = !busy,
                        ) { Text("📷") }
                        OutlinedButton(
                            onClick = {
                                try {
                                    pickImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    aiError = "Δεν άνοιξε η συλλογή."
                                }
                            },
                            enabled = !busy,
                        ) { Text("🖼") }
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "Το Claude υπολογίζει…",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    aiNote?.let {
                        Text(
                            "Εκτίμηση Claude: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Για εκτίμηση θερμίδων από κείμενο ή φωτογραφία, βάλε το Claude API key στις Ρυθμίσεις.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                aiError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ώρα", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                                if (!mealTouched) meal = Meal.forHour(h)
                            },
                            hour,
                            minute,
                            true,
                        ).show()
                    }) { Text(hm(hour, minute)) }
                }

                Meal.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { option ->
                            FilterChip(
                                selected = meal == option,
                                onClick = {
                                    meal = option
                                    mealTouched = true
                                },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (existing != null) {
                    TextButton(onClick = { vm.delete(existing); onDismiss() }) {
                        Text("Διαγραφή φαγητού", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    val time = vm.timeOn(date, LocalTime.of(hour, minute))
                    val value = kcalValue ?: return@TextButton
                    if (existing == null) {
                        vm.add(name, value, time, meal)
                    } else {
                        vm.update(existing.copy(name = name.trim(), kcal = value, time = time, meal = meal.name))
                    }
                    onDismiss()
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Άκυρο") } },
    )
}
