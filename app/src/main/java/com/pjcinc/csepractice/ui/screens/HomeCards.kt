package com.pjcinc.csepractice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.ui.components.InspirationCard
import com.pjcinc.csepractice.viewmodel.AppViewModel

/** "Welcome back" after 2+ days away. No guilt, ever. */
@Composable
fun ComebackCard(user: UserState, daysAway: Long, streakKept: Boolean) {
    val lang = user.language
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(Encourage.comeback(lang, daysAway, streakKept), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (!streakKept && user.streak > 1) {
                Text(
                    Encourage.t(lang, "A new streak starts today. Your best is ${user.bestStreak} days — you've done it before.",
                        "Bagong streak ngayong araw. Ang best mo ay ${user.bestStreak} araw — kaya mo ulit 'yan."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    if (user.inspirationLine.isNotBlank() || user.inspirationPhoto.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        InspirationCard(user.inspirationLine, user.inspirationPhoto, lang)
    }
}

/** The day before and the day of the exam: calm checklist + good-luck message. */
@Composable
fun ExamDayCard(vm: AppViewModel, user: UserState, isToday: Boolean) {
    val lang = user.language
    val dayKey = user.examDay.toString()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (isToday) "🍀" else "🌙", fontSize = 34.sp)
            Text(
                if (isToday) Encourage.t(lang, "Exam day! You've got this.", "Exam day na! Kaya mo 'to.")
                else Encourage.t(lang, "Tomorrow is the big day", "Bukas na ang malaking araw"),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(Encourage.goodLuck(lang, user.firstName), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(10.dp))
            Text(Encourage.t(lang, "Checklist", "Checklist"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Encourage.checklist(lang).forEach { (id, label) ->
                val key = "$dayKey:$id"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleChecklist(key) }
                ) {
                    Checkbox(checked = key in user.checklist, onCheckedChange = { vm.toggleChecklist(key) })
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(
                Encourage.t(lang, "Always follow the official instructions on your CSC exam notice.",
                    "Sundin palagi ang opisyal na instructions sa CSC exam notice mo."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    if (isToday) {
        Spacer(Modifier.height(10.dp))
        InspirationCard(user.inspirationLine, user.inspirationPhoto, lang)
    }
}

/** After exam day: "How did it go?" — results take a while, so "still waiting" snoozes a week. */
@Composable
fun PostExamCard(vm: AppViewModel, user: UserState) {
    val lang = user.language
    var notPassed by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(Encourage.t(lang, "How did your exam go? 🙏", "Kumusta ang exam mo? 🙏"), style = MaterialTheme.typography.titleMedium)
            Text(
                Encourage.t(lang, "When your results are out, tell us — it helps us help you.",
                    "Kapag lumabas na ang resulta, sabihin mo sa amin para matulungan ka namin."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.reportExam(passed = true) }, modifier = Modifier.fillMaxWidth()) {
                Text(Encourage.t(lang, "I passed! 🎉", "Pumasa ako! 🎉"))
            }
            OutlinedButton(onClick = { notPassed = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(Encourage.t(lang, "Not this time", "Hindi pa ngayon"))
            }
            TextButton(onClick = { vm.snoozeExamQuestion() }, modifier = Modifier.fillMaxWidth()) {
                Text(Encourage.t(lang, "Still waiting for results", "Hinihintay pa ang resulta"))
            }
        }
    }
    if (notPassed) {
        AlertDialog(
            onDismissRequest = { notPassed = false },
            title = { Text(Encourage.t(lang, "We're with you 💛", "Kasama mo kami 💛")) },
            text = { Text(Encourage.notPassed(lang)) },
            confirmButton = {
                Button(onClick = { vm.reportExam(passed = false); notPassed = false; pickDate = true }) {
                    Text(Encourage.t(lang, "Set my next exam date", "Itakda ang susunod na exam"))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.reportExam(passed = false); notPassed = false }) {
                    Text(Encourage.t(lang, "Later", "Mamaya na"))
                }
            }
        )
    }
    if (pickDate) ExamDateDialog(-1, onDismiss = { pickDate = false }) { day -> vm.setExamDay(day); pickDate = false }
}
