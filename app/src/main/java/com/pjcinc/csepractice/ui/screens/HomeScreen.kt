package com.pjcinc.csepractice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.ui.components.ReadinessMeter
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.ui.components.ActionCard
import com.pjcinc.csepractice.ui.components.Bar
import com.pjcinc.csepractice.ui.components.Pill
import com.pjcinc.csepractice.ui.components.SectionTitle
import com.pjcinc.csepractice.ui.components.subjectStyle
import com.pjcinc.csepractice.ui.theme.Blue
import com.pjcinc.csepractice.ui.theme.Coral
import com.pjcinc.csepractice.ui.theme.Flame
import com.pjcinc.csepractice.ui.theme.Navy
import com.pjcinc.csepractice.ui.theme.Sun
import com.pjcinc.csepractice.viewmodel.AppViewModel
import com.pjcinc.csepractice.viewmodel.Tab
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(vm: AppViewModel) {
    val user = vm.user.collectAsState().value ?: return
    val answeredToday by vm.answeredToday.collectAsState()
    val due by vm.dueReviews.collectAsState()
    val saved by vm.savedQuiz.collectAsState()
    val progress by vm.topicProgress.collectAsState()
    val busy by vm.busy.collectAsState()
    val readiness by vm.readiness.collectAsState()
    val weeklyGain by vm.weeklyGain.collectAsState()
    val today = LocalDate.now().toEpochDay()
    val streak = Game.liveStreak(user, today)
    val level = Game.progress(user.xp)
    var unseen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(answeredToday) { unseen = vm.unseenSyllabusCount() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ---- hero header ----
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Navy, Blue)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lvl ${level.level} · ${level.title}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    StreakBadge(streak)
                }
                Spacer(Modifier.height(10.dp))
                Bar(level.fraction, Sun, height = 8.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${level.intoLevel} / ${level.levelSpan} XP to level ${level.level + 1}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(14.dp))
                TodayCard(
                    answered = answeredToday,
                    goal = user.dailyGoal,
                    examDay = user.examDay,
                    today = today,
                    examLevel = user.examLevel,
                    recommended = unseen?.let { u -> if (user.examDay > today) Planner.recommendedPerDay(u, user.examDay - today) else null },
                    onSetDate = { vm.selectTab(Tab.SETTINGS) }
                )
                Spacer(Modifier.height(14.dp))
                // ---- readiness vs the 80 passing rating ----
                val lang = user.language
                if (readiness.shown) {
                    Text(
                        Encourage.readinessLine(lang, readiness) { subjectStyle(it).short },
                        color = Color.White, style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    ReadinessMeter(
                        readiness.overall, track = Color.White.copy(alpha = 0.2f), fill = Sun,
                        passFill = Color(0xFF7BE3A6), markerColor = Color.White, labelColor = Color.White.copy(alpha = 0.8f)
                    )
                    weeklyGain?.takeIf { it > 0 }?.let {
                        Text(
                            Encourage.t(lang, "📈 +$it points since last week", "📈 +$it points mula last week"),
                            color = Color(0xFF7BE3A6), style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    val left = Planner.MIN_ANSWERS_FOR_READINESS - readiness.answered
                    Text(
                        Encourage.t(lang,
                            "Answer $left more question${if (left == 1) "" else "s"} to see your estimated score vs. the ${Planner.PASS_MARK}% passing rating.",
                            "Sagutin pa ang $left tanong para makita ang estimated score mo kumpara sa ${Planner.PASS_MARK}% na pasado."),
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            // ---- moments that matter: exam eve/day, after the exam, coming back ----
            val away = Game.daysAway(user, today)
            when {
                user.examDay == today || user.examDay == today + 1 -> {
                    ExamDayCard(vm, user, isToday = user.examDay == today)
                    Spacer(Modifier.height(16.dp))
                }
                user.examDay in 0 until today && user.examDay.toString() !in user.outcomeAskedFor &&
                    user.outcomeSnoozeDay <= today -> {
                    PostExamCard(vm, user)
                    Spacer(Modifier.height(16.dp))
                }
                away >= 2 -> {
                    ComebackCard(user, away, streakKept = streak > 0 && away - 1 <= user.freezes)
                    Spacer(Modifier.height(16.dp))
                }
            }
            // ---- the one big button ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable(enabled = !busy) { vm.start(QuizMode.SMART, 15) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(50)).background(Sun),
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Navy, strokeWidth = 3.dp)
                        else Icon(Icons.Filled.PlayArrow, null, tint = Navy, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Practice now", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            "15 questions picked for you: weak topics, reviews and new ones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            saved?.let { s ->
                Spacer(Modifier.height(12.dp))
                ActionCard(
                    title = "Continue: ${s.title}",
                    subtitle = "${s.answeredCount} of ${s.size} answered" + if (s.mode.isExam) " · exam clock paused" else "",
                    icon = Icons.Filled.Replay,
                    color = MaterialTheme.colorScheme.secondary,
                    trailing = {
                        IconButton(onClick = { vm.discardSaved() }) { Icon(Icons.Filled.Close, contentDescription = "Discard saved session") }
                    },
                    onClick = { vm.resume() }
                )
            }

            SectionTitle("Today")
            val dailyDone = user.dailyDoneDay == today
            ActionCard(
                title = "Daily challenge",
                subtitle = if (dailyDone) "Done for today — come back tomorrow!" else "10 questions, same for everyone today · +${Game.XP_DAILY} XP",
                icon = if (dailyDone) Icons.Filled.CheckCircle else Icons.Filled.Bolt,
                color = if (dailyDone) MaterialTheme.colorScheme.primary else Flame,
                enabled = !dailyDone && !busy,
                trailing = { if (!dailyDone) Pill("+${Game.XP_DAILY}", Flame) },
                onClick = { vm.start(QuizMode.DAILY) }
            )
            if (due > 0) {
                Spacer(Modifier.height(10.dp))
                ActionCard(
                    title = "Review missed questions",
                    subtitle = "$due due today. Getting them right now makes them stick.",
                    icon = Icons.Filled.Replay,
                    color = Coral,
                    enabled = !busy,
                    trailing = { Pill("$due", Coral) },
                    onClick = { vm.start(QuizMode.REVIEW, 20) }
                )
            }
            val weakest = Planner.weakest(progress).firstOrNull { it.answered >= 3 }
            if (weakest != null) {
                Spacer(Modifier.height(10.dp))
                val st = subjectStyle(weakest.topic.category)
                ActionCard(
                    title = "Focus: ${weakest.topic.name}",
                    subtitle = "Your weakest topic so far · ${weakest.accuracyPercent}% correct",
                    icon = Icons.Filled.TrackChanges,
                    color = st.color,
                    enabled = !busy,
                    onClick = { vm.start(QuizMode.TOPIC, 10, topicId = weakest.topic.id) }
                )
            }
            Spacer(Modifier.height(10.dp))
            ActionCard(
                title = "Mock exam",
                subtitle = "Timed, no hints — like the real thing",
                icon = Icons.Filled.Timer,
                color = Navy,
                onClick = { vm.openCategory(null) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = if (streak > 0) Flame else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
            Text("$streak", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Text(if (streak == 1) "day streak" else "day streak", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TodayCard(
    answered: Int,
    goal: Int,
    examDay: Long,
    today: Long,
    examLevel: String,
    recommended: Int?,
    onSetDate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (answered.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.size(58.dp),
                    color = Sun,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 6.dp
                )
                Text("$answered", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (answered >= goal) "Daily goal reached! 🎉" else "$answered of $goal questions today",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                if (examDay == today) {
                    Text("🍀 Today is exam day — good luck!", color = Sun, style = MaterialTheme.typography.bodySmall)
                } else if (examDay > today) {
                    val days = examDay - today
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, null, tint = Sun, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$days day${if (days == 1L) "" else "s"} to your $examLevel exam",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (recommended != null && recommended > goal) {
                        Text(
                            "Tip: $recommended/day lets you see every question before exam day",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        if (examDay in 0..today) "Exam day passed — set your next date" else "Set your exam date for a countdown →",
                        color = Sun,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(onClick = onSetDate)
                    )
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Magandang umaga! ☀️"
        h < 18 -> "Magandang hapon! 🌤️"
        else -> "Magandang gabi! 🌙"
    }
}
