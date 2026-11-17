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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.domain.TopicProgress
import com.pjcinc.csepractice.ui.components.ActionCard
import com.pjcinc.csepractice.ui.components.Bar
import com.pjcinc.csepractice.ui.components.IconBubble
import com.pjcinc.csepractice.ui.components.Pill
import com.pjcinc.csepractice.ui.components.SectionTitle
import com.pjcinc.csepractice.ui.components.subjectStyle
import com.pjcinc.csepractice.ui.theme.Coral
import com.pjcinc.csepractice.ui.theme.Navy
import com.pjcinc.csepractice.ui.theme.Success
import com.pjcinc.csepractice.viewmodel.AppViewModel

@Composable
fun PracticeScreen(vm: AppViewModel) {
    val category by vm.practiceCategory.collectAsState()
    val c = category
    if (c == null) PracticeHub(vm) else SubjectTopics(vm, c)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeHub(vm: AppViewModel) {
    val progress by vm.topicProgress.collectAsState()
    val busy by vm.busy.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text("Practice", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Pick a subject to see its topics, or take a timed mock exam.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("Subjects")
        Planner.CATEGORIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                row.forEach { cat ->
                    SubjectCard(cat, Planner.readiness(progress, cat), progress.filter { it.topic.category == cat }.sumOf { it.questions },
                        Modifier.weight(1f)) { vm.openCategory(cat) }
                }
            }
        }

        SectionTitle("Mock exam")
        ActionCard(
            title = "Mini mock",
            subtitle = "40 items · 40 minutes · all four subjects",
            icon = Icons.Filled.Timer,
            color = Navy,
            enabled = !busy,
            onClick = { vm.start(QuizMode.EXAM, 40) }
        )
        Spacer(Modifier.height(10.dp))
        ActionCard(
            title = "Long mock",
            subtitle = "100 items · 100 minutes · build exam stamina",
            icon = Icons.Filled.Timer,
            color = Coral,
            enabled = !busy,
            onClick = { vm.start(QuizMode.EXAM, 100) }
        )
        Text(
            "Exam mode: no hints and no answers until you submit. Pass mark shown: 80%.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        SectionTitle("Custom practice")
        var picked by rememberSaveable { mutableStateOf(Planner.CATEGORIES.toSet().toList()) }
        var count by rememberSaveable { mutableIntStateOf(25) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Subjects", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Planner.CATEGORIES.forEach { cat ->
                        val on = cat in picked
                        FilterChip(
                            selected = on,
                            onClick = { picked = if (on) picked - cat else picked + cat },
                            label = { Text(subjectStyle(cat).short) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("How many", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 50, 100).forEach { n ->
                        FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.start(QuizMode.CUSTOM, count, categories = picked) },
                    enabled = picked.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Tune, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (picked.isEmpty()) "Pick at least one subject" else "Start $count questions")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SubjectCard(category: String, readiness: Int, questions: Int, modifier: Modifier, onClick: () -> Unit) {
    val st = subjectStyle(category)
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            IconBubble(st.icon, st.color)
            Spacer(Modifier.height(10.dp))
            Text(st.short, style = MaterialTheme.typography.titleMedium)
            Text("$questions questions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Bar(readiness / 100f, st.color)
            Text(
                if (readiness < 0) "Score after ${Planner.MIN_ANSWERS_FOR_READINESS} answers" else "Est. score $readiness%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SubjectTopics(vm: AppViewModel, category: String) {
    val progress by vm.topicProgress.collectAsState()
    val busy by vm.busy.collectAsState()
    val user by vm.user.collectAsState()
    val st = subjectStyle(category)
    val topics = progress.filter { it.topic.category == category }
        .sortedWith(compareByDescending<TopicProgress> { it.topic.syllabus }.thenBy { it.skill })
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)) {
            IconButton(onClick = { vm.openCategory(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to subjects") }
            Text(category, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Button(
                    onClick = { vm.start(QuizMode.CUSTOM, 15, categories = listOf(category)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Text("Practice all of ${st.short} · 15 questions") }
                Text(
                    "Or tap a topic for a focused 10-question drill. Weakest topics are listed first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(topics, key = { it.topic.id }) { tp ->
                val open = expanded == tp.topic.id
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(MaterialTheme.shapes.medium)
                        .clickable { expanded = if (open) null else tp.topic.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tp.topic.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${tp.questions} questions" + (if (tp.answered > 0) " · ${tp.accuracyPercent}% correct of ${tp.answered}" else "") +
                                        (if (!tp.topic.syllabus) " · extra" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Pill(tp.status, statusColor(tp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Bar(if (tp.answered == 0) 0f else tp.accuracyPercent / 100f, st.color, height = 6.dp)
                        if (open) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (user?.language == "tl") tp.topic.tipTl else tp.topic.tip,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { vm.start(QuizMode.TOPIC, 10, topicId = tp.topic.id) }, enabled = !busy) {
                                Text("Drill 10 questions")
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun statusColor(tp: TopicProgress) = when (tp.status) {
    "Strong" -> Success
    "Getting there" -> MaterialTheme.colorScheme.secondary
    "Needs work" -> Coral
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
