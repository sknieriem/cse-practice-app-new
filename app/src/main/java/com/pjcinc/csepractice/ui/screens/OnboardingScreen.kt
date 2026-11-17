package com.pjcinc.csepractice.ui.screens

import com.pjcinc.csepractice.ui.components.ChoiceSegments
import com.pjcinc.csepractice.ui.components.ChoiceList
import com.pjcinc.csepractice.ui.components.Choice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjcinc.csepractice.ui.theme.Blue
import com.pjcinc.csepractice.ui.theme.Navy
import com.pjcinc.csepractice.ui.theme.Sun
import com.pjcinc.csepractice.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var level by rememberSaveable { mutableStateOf("Professional") }
    var examDay by rememberSaveable { mutableLongStateOf(-1L) }
    var goal by rememberSaveable { mutableIntStateOf(20) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, Blue)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("📝", fontSize = 56.sp)
        Text("CSE Practice", color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text(
            "Review for the Civil Service Exam — anytime, even offline.",
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Inspired by Pearl Jynne Celestino · By PJC Inc",
            color = Sun,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("1. Which exam are you taking?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ChoiceList(EXAM_LEVELS, level) { level = it }
                Spacer(Modifier.height(16.dp))
                Text("2. When is your exam?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ChoiceList(
                    listOf(
                        Choice(
                            "date",
                            if (examDay < 0) "📅 Pick a date" else "📅 " + LocalDate.ofEpochDay(examDay).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                            if (examDay < 0) null else "Tap to change"
                        ),
                        Choice("unsure", "Not sure yet", "You can set it later in Settings")
                    ),
                    if (examDay < 0) "unsure" else "date"
                ) { if (it == "date") pickDate = true else examDay = -1 }
                Spacer(Modifier.height(16.dp))
                Text("3. Daily goal (questions)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ChoiceSegments(GOALS, goal.toString()) { goal = it.toInt() }
                Text(
                    "About ${goal / 2}–$goal minutes a day. You can change all of this later in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.completeOnboarding(examDay, level, goal) },
            colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Navy),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Tara, simulan na! →", style = MaterialTheme.typography.titleMedium) }
    }

    if (pickDate) ExamDateDialog(examDay, onDismiss = { pickDate = false }) { examDay = it; pickDate = false }
}

/** Shared by onboarding and Settings. */
val EXAM_LEVELS = listOf(
    Choice("Professional", "Professional"),
    Choice("Subprofessional", "Subprofessional")
)
val GOALS = listOf(10, 20, 40, 60).map { Choice(it.toString(), it.toString()) }
