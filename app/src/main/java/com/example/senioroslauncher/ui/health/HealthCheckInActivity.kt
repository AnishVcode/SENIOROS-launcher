package com.example.senioroslauncher.ui.health

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.database.entity.HealthCheckInEntity
import com.example.senioroslauncher.ui.components.LargeActionButton
import com.example.senioroslauncher.ui.components.SeniorTopAppBar
import com.example.senioroslauncher.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Date

class HealthCheckInActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                HealthCheckInScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCheckInScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SeniorLauncherApp
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    var selectedMood by remember { mutableIntStateOf(0) }
    var painLevel by remember { mutableFloatStateOf(1f) }
    var selectedSleepQuality by remember { mutableIntStateOf(0) }
    var selectedSymptoms by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val symptoms = listOf(
        "Headache", "Fatigue", "Dizziness", "Nausea",
        "Back Pain", "Joint Pain", "Shortness of Breath", "Cough",
        "Fever", "Chest Pain", "Loss of Appetite", "Other"
    )

    fun submitCheckIn() {
        if (selectedMood == 0) {
            Toast.makeText(context, "Please select your mood", Toast.LENGTH_SHORT).show()
            return
        }

        isSubmitting = true

        scope.launch {
            val checkIn = HealthCheckInEntity(
                date = Date(),
                mood = selectedMood,
                painLevel = painLevel.toInt(),
                sleepQuality = if (selectedSleepQuality > 0) selectedSleepQuality else null,
                symptoms = selectedSymptoms.toList(),
                notes = notes.trim(),
                createdAt = Date()
            )

            app.database.healthCheckInDao().insert(checkIn)

            Toast.makeText(context, "Health check-in saved!", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            SeniorTopAppBar(
                title = "Daily Check-In",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Introduction Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryBlue.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "How are you feeling today?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your daily check-in helps your family stay informed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mood Section
            Text(
                text = "Mood",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            MoodSelector(
                selectedMood = selectedMood,
                onMoodSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedMood = it
                }
            )

            // Pain Level Section
            Text(
                text = "Pain Level",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            PainLevelSlider(
                painLevel = painLevel,
                onPainLevelChanged = { painLevel = it }
            )

            // Sleep Quality Section
            Text(
                text = "Last Night's Sleep",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SleepQualitySelector(
                selectedQuality = selectedSleepQuality,
                onQualitySelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedSleepQuality = it
                }
            )

            // Symptoms Section
            Text(
                text = "Any Symptoms?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SymptomsGrid(
                symptoms = symptoms,
                selectedSymptoms = selectedSymptoms,
                onSymptomToggled = { symptom ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedSymptoms = if (selectedSymptoms.contains(symptom)) {
                        selectedSymptoms - symptom
                    } else {
                        selectedSymptoms + symptom
                    }
                }
            )

            // Notes Section
            Text(
                text = "Additional Notes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text("Add any other details about how you're feeling...")
                },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            LargeActionButton(
                text = if (isSubmitting) "Saving..." else "Submit Check-In",
                onClick = { submitCheckIn() },
                icon = Icons.Default.Check,
                backgroundColor = SuccessGreen,
                enabled = !isSubmitting && selectedMood > 0
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MoodSelector(
    selectedMood: Int,
    onMoodSelected: (Int) -> Unit
) {
    val moods = listOf(
        1 to "Very Bad",
        2 to "Bad",
        3 to "Okay",
        4 to "Good",
        5 to "Great"
    )

    val moodEmojis = listOf("", "Awful", "Bad", "Okay", "Good", "Great!")
    val moodColors = listOf(
        Color.Transparent,
        EmergencyRed,
        WarningOrange,
        Color(0xFFFFD700),
        SecondaryGreen,
        SuccessGreen
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        moods.forEach { (value, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { onMoodSelected(value) }
                        .then(
                            if (selectedMood == value)
                                Modifier.border(3.dp, moodColors[value], CircleShape)
                            else Modifier
                        ),
                    color = if (selectedMood == value)
                        moodColors[value].copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = when (value) {
                                1 -> "Awful"
                                2 -> "Bad"
                                3 -> "Okay"
                                4 -> "Good"
                                5 -> "Great"
                                else -> ""
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedMood == value) moodColors[value] else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedMood == value)
                        moodColors[value]
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PainLevelSlider(
    painLevel: Float,
    onPainLevelChanged: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "No Pain",
                style = MaterialTheme.typography.bodyMedium,
                color = SuccessGreen
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    painLevel <= 3 -> SuccessGreen.copy(alpha = 0.2f)
                    painLevel <= 6 -> WarningOrange.copy(alpha = 0.2f)
                    else -> EmergencyRed.copy(alpha = 0.2f)
                }
            ) {
                Text(
                    text = painLevel.toInt().toString(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        painLevel <= 3 -> SuccessGreen
                        painLevel <= 6 -> WarningOrange
                        else -> EmergencyRed
                    }
                )
            }
            Text(
                text = "Severe",
                style = MaterialTheme.typography.bodyMedium,
                color = EmergencyRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = painLevel,
            onValueChange = onPainLevelChanged,
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = when {
                    painLevel <= 3 -> SuccessGreen
                    painLevel <= 6 -> WarningOrange
                    else -> EmergencyRed
                },
                activeTrackColor = when {
                    painLevel <= 3 -> SuccessGreen
                    painLevel <= 6 -> WarningOrange
                    else -> EmergencyRed
                }
            )
        )
    }
}

@Composable
private fun SleepQualitySelector(
    selectedQuality: Int,
    onQualitySelected: (Int) -> Unit
) {
    val qualities = listOf(
        1 to "Very Poor",
        2 to "Poor",
        3 to "Fair",
        4 to "Good",
        5 to "Excellent"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        qualities.forEach { (value, label) ->
            FilterChip(
                selected = selectedQuality == value,
                onClick = { onQualitySelected(value) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (selectedQuality == value) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun SymptomsGrid(
    symptoms: List<String>,
    selectedSymptoms: Set<String>,
    onSymptomToggled: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        symptoms.chunked(3).forEach { rowSymptoms ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSymptoms.forEach { symptom ->
                    FilterChip(
                        selected = selectedSymptoms.contains(symptom),
                        onClick = { onSymptomToggled(symptom) },
                        label = { Text(symptom) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (selectedSymptoms.contains(symptom)) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
                // Fill empty space if row has fewer items
                repeat(3 - rowSymptoms.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
