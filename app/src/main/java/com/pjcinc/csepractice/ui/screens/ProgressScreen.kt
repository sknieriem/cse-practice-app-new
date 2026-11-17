package com.pjcinc.csepractice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.ui.components.Bar
import com.pjcinc.csepractice.ui.components.SectionTitle
import com.pjcinc.csepractice.ui.components.StatBox
import com.pjcinc.csepractice.ui.components.subjectStyle
import com.pjcinc.csepractice.ui.theme.Flame
import com.pjcinc.csepractice.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val user = vm.user.collectAsState().value ?: return
    val progress by vm.topicProgress.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val answered by vm.answeredTotal.collectAsState()
    val today = LocalDate.now().toEpochDay()
    val lp = Game.progress(user.xp)
    val correctAll = progress.sumOf { it.correct }
    val accuracy = if (answered == 0) 0 else correctAll * 100 / answered

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Level ${lp.level}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(lp.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Bar(lp.fraction, MaterialTheme.colorScheme.primary, height = 10.dp)
                Text(
                    "${user.xp} XP total · ${lp.levelSpan - lp.intoLevel} XP to next level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("$answered", "answered", Modifier.weight(1f))
            StatBox("$accuracy%", "correct", Modifier.weight(1f))
            StatBox("🔥${Game.liveStreak(user, today)}", "streak", Modifier.weight(1f), Flame)
            StatBox("${user.bestStreak}", "best streak", Modifier.weight(1f), Flame)
        }

        SectionTitle("Exam readiness")
        Text(
            "Grows as you cover each topic AND get it right. Untried topics count as zero. Aim for 80%.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Planner.CATEGORIES.forEach { cat ->
            val st = subjectStyle(cat)
            val r = Planner.readiness(progress, cat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { vm.openCategory(cat) }.padding(vertical = 6.dp)
            ) {
                Text(st.short, modifier = Modifier.width(96.dp))
                Bar(r / 100f, st.color, Modifier.weight(1f), height = 12.dp)
                Text(if (r == 0) "—" else "$r%", modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
            }
        }

        val weak = Planner.weakest(progress).filter { it.answered >= 3 }.take(5)
        if (weak.isNotEmpty()) {
            SectionTitle("Focus on these")
            weak.forEach { tp ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.start(QuizMode.TOPIC, 10, topicId = tp.topic.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tp.topic.name, style = MaterialTheme.typography.titleMedium)
                            Text("${tp.accuracyPercent}% of ${tp.answered} · tap to drill", style = MaterialTheme.typography.labelSmall)
                        }
                        Bar(tp.accuracyPercent / 100f, subjectStyle(tp.topic.category).color, Modifier.width(80.dp))
                    }
                }
            }
        }

        SectionTitle("Badges · ${user.badges.size}/${Game.BADGES.size}")
        Game.BADGES.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { b ->
                    val earned = b.id in user.badges
                    Card(
                        modifier = Modifier.weight(1f).alpha(if (earned) 1f else 0.4f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (earned) b.emoji else "🔒", fontSize = 26.sp)
                            Text(b.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                            Text(b.description, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        SectionTitle("Score history")
        if (sessions.size < 2) {
            Text("Finish two sessions to see your trend.", style = MaterialTheme.typography.bodySmall)
        } else {
            ScoreChart(sessions.take(30).reversed().map { it.score })
        }
        var showAll by remember { mutableStateOf(false) }
        val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        (if (showAll) sessions else sessions.take(5)).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(QuizMode.entries.firstOrNull { it.name == s.mode }?.label ?: s.mode, style = MaterialTheme.typography.bodyMedium)
                    Text(fmt.format(Date(s.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${s.correctCount}/${s.totalQuestions}", modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                Text("${s.score}%", modifier = Modifier.width(52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (sessions.size > 5) {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all ${sessions.size}") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScoreChart(scores: List<Int>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(scores) { producer.runTransaction { lineSeries { series(scores) } } }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(180.dp)
    )
}
