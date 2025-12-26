package com.example.senioroslauncher.ui.medication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.senioroslauncher.R
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.database.entity.MedicationEntity
import com.example.senioroslauncher.data.database.entity.MedicationFrequency
import com.example.senioroslauncher.data.database.entity.MedicationScheduleEntity
import com.example.senioroslauncher.data.guardian.MedicationNotifier
import com.example.senioroslauncher.services.MedicationReminderService
import com.example.senioroslauncher.ui.components.LargeActionButton
import com.example.senioroslauncher.ui.components.SeniorTopAppBar
import com.example.senioroslauncher.ui.theme.*
import com.example.senioroslauncher.util.LocaleHelper
import kotlinx.coroutines.launch
import java.util.Date

class MedicationActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleHelper.getLanguageCode(newBase)
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                MedicationScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SeniorLauncherApp
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<MedicationEntity?>(null) }

    val medications by app.database.medicationDao()
        .getAllActiveMedications()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            SeniorTopAppBar(
                title = "Medications",
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MedicationOrange,
                contentColor = White
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Medication", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { paddingValues ->
        if (medications.isEmpty()) {
            EmptyMedicationState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(medications, key = { it.id }) { medication ->
                    MedicationCard(
                        medication = medication,
                        onTakenClick = {
                            // Log as taken
                        },
                        onEditClick = { editingMedication = medication },
                        onDeleteClick = {
                            scope.launch {
                                app.database.medicationDao().delete(medication)
                                // Notify guardians
                                MedicationNotifier.notifyMedicationDeleted(context, medication)
                            }
                        }
                    )
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingMedication != null) {
        AddMedicationDialog(
            medication = editingMedication,
            onDismiss = {
                showAddDialog = false
                editingMedication = null
            },
            onSave = { name, dosage, frequency, notes, times ->
                scope.launch {
                    val isUpdate = editingMedication != null
                    val medicationId = if (isUpdate) {
                        app.database.medicationDao().update(
                            editingMedication!!.copy(
                                name = name,
                                dosage = dosage,
                                frequency = frequency,
                                notes = notes,
                                updatedAt = Date()
                            )
                        )
                        editingMedication!!.id
                    } else {
                        app.database.medicationDao().insert(
                            MedicationEntity(
                                name = name,
                                dosage = dosage,
                                frequency = frequency,
                                notes = notes
                            )
                        )
                    }

                    // Save schedules
                    app.database.medicationScheduleDao().deleteAllForMedication(medicationId)
                    times.forEach { (hour, minute) ->
                        app.database.medicationScheduleDao().insert(
                            MedicationScheduleEntity(
                                medicationId = medicationId,
                                hour = hour,
                                minute = minute
                            )
                        )
                    }

                    // Schedule alarms for this medication
                    MedicationReminderService.scheduleMedicationReminders(context, medicationId)

                    // Get the saved medication to notify guardians
                    val savedMedication = app.database.medicationDao().getMedicationByIdSync(medicationId)
                    if (savedMedication != null) {
                        if (isUpdate) {
                            MedicationNotifier.notifyMedicationUpdated(context, savedMedication)
                        } else {
                            MedicationNotifier.notifyMedicationAdded(context, savedMedication)
                        }
                    }
                }
                showAddDialog = false
                editingMedication = null
            }
        )
    }
}

@Composable
private fun EmptyMedicationState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Medication,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MedicationOrange.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Medications",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your medications to get reminders when it's time to take them",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun MedicationCard(
    medication: MedicationEntity,
    onTakenClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardOrange)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VeryDarkGray
                    )
                    Text(
                        text = medication.dosage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkGray
                    )
                    Text(
                        text = medication.frequency.toDisplayString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MedicationOrange
                    )
                }
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = DarkGray
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = EmergencyRed
                        )
                    }
                }
            }

            if (medication.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = medication.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTakenClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark as Taken", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Medication?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Are you sure you want to delete ${medication.name}?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMedicationDialog(
    medication: MedicationEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, MedicationFrequency, String, List<Pair<Int, Int>>) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SeniorLauncherApp

    var name by remember { mutableStateOf(medication?.name ?: "") }
    var dosage by remember { mutableStateOf(medication?.dosage ?: "") }
    var frequency by remember { mutableStateOf(medication?.frequency ?: MedicationFrequency.DAILY) }
    var notes by remember { mutableStateOf(medication?.notes ?: "") }
    var times by remember { mutableStateOf(listOf(Pair(8, 0))) }
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableIntStateOf(-1) }

    // Load existing schedules when editing
    LaunchedEffect(medication?.id) {
        medication?.let { med ->
            val schedules = app.database.medicationScheduleDao().getSchedulesForMedicationSync(med.id)
            if (schedules.isNotEmpty()) {
                times = schedules.map { Pair(it.hour, it.minute) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (medication != null) "Edit Medication" else "Add Medication",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage (e.g., 10mg, 2 tablets)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                // Frequency selector
                Text("Frequency", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MedicationFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq.toDisplayString()) }
                        )
                    }
                }

                // Times
                Text("Reminder Times", style = MaterialTheme.typography.labelLarge)
                times.forEachIndexed { index, (hour, minute) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                editingTimeIndex = index
                                showTimePicker = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(String.format("%02d:%02d", hour, minute))
                        }
                        if (times.size > 1) {
                            IconButton(onClick = { times = times.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Remove, contentDescription = "Remove time")
                            }
                        }
                    }
                }
                TextButton(onClick = { times = times + Pair(12, 0) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Time")
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, dosage, frequency, notes, times) },
                enabled = name.isNotBlank() && dosage.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = times.getOrNull(editingTimeIndex)?.first ?: 8,
            initialMinute = times.getOrNull(editingTimeIndex)?.second ?: 0
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                Button(onClick = {
                    times = times.mapIndexed { index, pair ->
                        if (index == editingTimeIndex) {
                            Pair(timePickerState.hour, timePickerState.minute)
                        } else pair
                    }
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun MedicationFrequency.toDisplayString(): String = when (this) {
    MedicationFrequency.DAILY -> "Daily"
    MedicationFrequency.WEEKLY -> "Weekly"
    MedicationFrequency.MONTHLY -> "Monthly"
    MedicationFrequency.AS_NEEDED -> "As Needed"
}
