package com.pjcinc.csepractice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.BuildConfig
import com.pjcinc.csepractice.ui.components.SectionTitle
import com.pjcinc.csepractice.viewmodel.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val user = vm.user.collectAsState().value ?: return
    val answered by vm.answeredTotal.collectAsState()
    var pickDate by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))

        SectionTitle("My exam")
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { pickDate = true }) {
                Column(Modifier.weight(1f)) {
                    Text("Exam date", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (user.examDay < 0) "Not set — tap to choose" else LocalDate.ofEpochDay(user.examDay).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { pickDate = true }) { Text("Change") }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Level", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Professional", "Subprofessional").forEach { l ->
                    FilterChip(selected = user.examLevel == l, onClick = { vm.setExamLevel(l) }, label = { Text(l) })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Daily goal", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 40, 60).forEach { g ->
                    FilterChip(selected = user.dailyGoal == g, onClick = { vm.setDailyGoal(g) }, label = { Text("$g") })
                }
            }
        }

        SectionTitle("Learning")
        SettingCard {
            ToggleRow(
                "Show answer right away",
                "See if you're right and why, after each question (practice modes). Exam mode always waits until the end.",
                user.instantFeedback
            ) { vm.setInstant(it) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Explanations", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = user.language == "en", onClick = { vm.setLanguage("en") }, label = { Text("English") })
                FilterChip(selected = user.language == "tl", onClick = { vm.setLanguage("tl") }, label = { Text("Taglish") })
            }
            Text(
                "You can also switch on any question.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle("Look")
        SettingCard {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Same as phone", "light" to "Light", "dark" to "Dark").forEach { (k, label) ->
                    FilterChip(selected = user.themeMode == k, onClick = { vm.setTheme(k) }, label = { Text(label) })
                }
            }
        }

        SectionTitle("About")
        SettingCard {
            Text("CSE Practice ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("Inspired by Pearl Jynne Celestino · By PJC Inc", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Works fully offline. Your progress stays on this phone. " +
                    (if (BuildConfig.TUTOR_ENABLED) "\"Ask the tutor\" needs internet." else "An online \"Ask the tutor\" feature is planned."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Practice questions are for review only and are not official CSC exam items.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SectionTitle("Data")
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset all progress ($answered answers)") }
        Spacer(Modifier.height(32.dp))
    }

    if (pickDate) ExamDateDialog(user.examDay, onDismiss = { pickDate = false }) { day ->
        vm.setExamDay(day); pickDate = false
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = { Text("This deletes your answers, XP, streak and badges on this phone. It cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmReset = false; vm.resetProgress() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Date picker limited to today or later. Returns LocalDate.toEpochDay(). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDateDialog(currentDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val todayMillis = LocalDate.now().toEpochDay() * 86_400_000L
    val state = rememberDatePickerState(
        initialSelectedDateMillis = if (currentDay >= 0) currentDay * 86_400_000L else null,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    } ?: onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
