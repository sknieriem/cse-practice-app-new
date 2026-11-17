package com.pjcinc.csepractice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.pjcinc.csepractice.BuildConfig
import com.pjcinc.csepractice.data.Question
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.QuizState
import com.pjcinc.csepractice.ui.components.Bar
import com.pjcinc.csepractice.ui.components.Pill
import com.pjcinc.csepractice.ui.components.formatClock
import com.pjcinc.csepractice.ui.components.subjectStyle
import com.pjcinc.csepractice.ui.theme.Coral
import com.pjcinc.csepractice.ui.theme.LocalFeedback
import com.pjcinc.csepractice.ui.theme.Sun
import com.pjcinc.csepractice.viewmodel.AppViewModel

private const val LETTERS = "ABCD"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val state = vm.quiz.collectAsState().value ?: return
    val questions by vm.quizQuestions.collectAsState()
    val user by vm.user.collectAsState()
    val busy by vm.busy.collectAsState()
    if (questions.size != state.size) return
    val q = questions[state.index]
    val i = state.index

    var showExit by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var lang by rememberSaveable { mutableStateOf(user?.language ?: "en") }

    BackHandler { showExit = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { QuizTopBar(state, onClose = { showExit = true }) },
        bottomBar = {
            QuizBottomBar(
                state = state,
                busy = busy,
                onPrev = vm::prev,
                onNext = vm::next,
                onMark = vm::toggleMark,
                onGrid = { showGrid = true },
                onSubmit = { if (state.answeredCount < state.size) confirmSubmit = true else vm.submit() }
            )
        }
    ) { padding ->
        // fresh scroll position for every question
        val scroll = androidx.compose.runtime.key(i) { rememberScrollState() }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            val st = subjectStyle(q.category)
            val topicName = vm.topics.firstOrNull { it.id == q.subtopic }?.name ?: ""
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Pill(st.short, st.color)
                Spacer(Modifier.width(8.dp))
                Text(topicName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (q.timeSensitive == true) {
                Text(
                    "⚠ This answer can change over time — check current facts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Coral,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            q.passage?.let { PassageCard(it) }
            Spacer(Modifier.height(12.dp))
            Text(q.text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(16.dp))

            val revealed = state.revealed[i]
            val chosenDisplay = state.answers[i]
            state.order[i].forEachIndexed { pos, canonical ->
                OptionRow(
                    tag = "option_$pos",
                    letter = LETTERS[pos],
                    text = q.options()[canonical],
                    selected = chosenDisplay == pos,
                    revealed = revealed,
                    isCorrect = canonical == q.correctIndex(),
                    onClick = { vm.answer(pos) }
                )
            }

            // Hint (practice modes only, before answering)
            if (!state.mode.isExam && !revealed) {
                val hint = hintFor(vm, q, lang)
                if (state.hinted[i]) {
                    InfoCard(Icons.Filled.Lightbulb, "Hint", hint, Sun)
                } else if (hint.isNotBlank()) {
                    TextButton(onClick = vm::useHint, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Lightbulb, null, tint = Sun)
                        Spacer(Modifier.width(6.dp))
                        Text("Show a hint (−${Game.XP_CORRECT - Game.XP_CORRECT_WITH_HINT} XP)")
                    }
                }
            }

            AnimatedVisibility(visible = revealed) {
                Column {
                    FeedbackPanel(vm, q, state, lang, onLang = { lang = it; vm.setLanguage(it) })
                }
            }
            if (state.mode.isExam && chosenDisplay >= 0) {
                Text(
                    "Answer saved. You can change it until you submit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Take a break?") },
            text = {
                Text(
                    "Your answers are saved. Continue any time from Home." +
                        if (state.mode.isExam) " The exam clock pauses while you're away." else ""
                )
            },
            confirmButton = { Button(onClick = { showExit = false; vm.pauseQuiz() }) { Text("Save & exit") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExit = false; vm.discardQuiz() }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showExit = false }) { Text("Keep going") }
                }
            }
        )
    }

    if (confirmSubmit) {
        val left = state.size - state.answeredCount
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Submit now?") },
            text = { Text("You have $left unanswered question${if (left == 1) "" else "s"}. They will count as wrong.") },
            confirmButton = { Button(onClick = { confirmSubmit = false; vm.submit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep answering") } }
        )
    }

    if (showGrid) {
        ModalBottomSheet(onDismissRequest = { showGrid = false }) {
            QuestionGrid(state, questions, onPick = { vm.goTo(it); showGrid = false }, onSubmit = {
                showGrid = false
                if (state.answeredCount < state.size) confirmSubmit = true else vm.submit()
            })
        }
    }
}

@Composable
private fun QuizTopBar(state: QuizState, onClose: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit session") }
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Bar((state.index + 1f) / state.size, MaterialTheme.colorScheme.primary, height = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text("${state.index + 1}/${state.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            val timed = state.timeLimitSec > 0
            val secs = if (timed) state.remainingSec.toLong() else state.elapsedMs / 1000
            val low = timed && state.remainingSec < 60
            Surface(
                shape = CircleShape,
                color = if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatClock(secs), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun QuizBottomBar(
    state: QuizState,
    busy: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMark: () -> Unit,
    onGrid: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = state.index > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous question")
            }
            IconButton(onClick = onMark) {
                val marked = state.marked[state.index]
                Icon(
                    if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (marked) "Unmark" else "Mark for review",
                    tint = if (marked) Sun else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onGrid) { Icon(Icons.Filled.GridView, contentDescription = "All questions") }
            Spacer(Modifier.weight(1f))
            val last = state.index == state.size - 1
            val answered = state.answers[state.index] >= 0
            if (last) {
                Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("submit")) {
                    Text("Submit")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.CheckCircle, null)
                }
            } else {
                Button(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp).testTag("next")) {
                    Text(if (answered) "Next" else "Skip")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun PassageCard(passage: String) {
    var open by rememberSaveable(passage) { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.medium).clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(if (open) "Passage (tap to hide)" else "Passage (tap to show)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (open) {
                Spacer(Modifier.height(6.dp))
                Text(passage, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun OptionRow(tag: String, letter: Char, text: String, selected: Boolean, revealed: Boolean, isCorrect: Boolean, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    val scheme = MaterialTheme.colorScheme
    val (bg, border, badge) = when {
        revealed && isCorrect -> Triple(fb.goodBg, fb.good, fb.good)
        revealed && selected -> Triple(fb.badBg, fb.bad, fb.bad)
        selected -> Triple(scheme.primaryContainer, scheme.primary, scheme.primary)
        else -> Triple(scheme.surface, scheme.outlineVariant, scheme.onSurfaceVariant)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag(tag)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !revealed, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(if (selected || (revealed && isCorrect)) 2.dp else 1.dp, border)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (selected || (revealed && isCorrect)) badge else Color.Transparent)
                    .border(1.5.dp, badge, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    revealed && isCorrect -> Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    revealed && selected -> Icon(Icons.Filled.Cancel, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    else -> Text(
                        letter.toString(),
                        fontWeight = FontWeight.Bold,
                        color = if (selected) scheme.onPrimary else badge
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeedbackPanel(vm: AppViewModel, q: Question, state: QuizState, lang: String, onLang: (String) -> Unit) {
    val fb = LocalFeedback.current
    val i = state.index
    val chosen = state.canonicalChosen(i)
    val right = chosen == q.correctIndex()
    val correctPos = state.order[i].indexOf(q.correctIndex())
    val xp = if (right) (if (state.hinted[i]) Game.XP_CORRECT_WITH_HINT else Game.XP_CORRECT) else Game.XP_WRONG
    val tl = lang == "tl"

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("feedback"),
        colors = CardDefaults.cardColors(containerColor = if (right) fb.goodBg else fb.badBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (right) com.pjcinc.csepractice.domain.Encourage.correctHeader(lang, q.id)
                    else com.pjcinc.csepractice.domain.Encourage.wrongHeader(lang, q.id, LETTERS[correctPos]),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (right) fb.good else fb.bad,
                    modifier = Modifier.weight(1f)
                )
                Pill("+$xp XP", if (right) fb.good else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !tl, onClick = { onLang("en") }, label = { Text("English") })
                FilterChip(selected = tl, onClick = { onLang("tl") }, label = { Text("Taglish") })
            }
            ExplanationBody(vm, q, chosen, tl)
            if (BuildConfig.TUTOR_ENABLED) {
                OutlinedButton(onClick = { /* online tutor: see TutorClient */ }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.QuestionAnswer, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ask the tutor")
                }
            }
        }
    }
}

/** Shared by the quiz feedback panel and the review screen. */
@Composable
fun ExplanationBody(vm: AppViewModel, q: Question, chosen: Int, tl: Boolean) {
    val steps = if (tl) q.stepsTl ?: q.steps else q.steps
    val explanation = if (tl) q.explanationTl ?: q.explanation else q.explanation
    val tip = vm.topics.firstOrNull { it.id == q.subtopic }?.let { if (tl) it.tipTl else it.tip }
    val showedTlFallback = tl && q.stepsTl == null && q.explanationTl == null

    if (!steps.isNullOrBlank()) {
        Label("Solution")
        Text(steps, style = MaterialTheme.typography.bodyLarge)
    }
    if (explanation.isNotBlank() && (steps.isNullOrBlank() || explanation != steps)) {
        Label(if (steps.isNullOrBlank()) "Explanation" else "In short")
        Text(explanation, style = MaterialTheme.typography.bodyLarge)
    }
    if (chosen >= 0 && chosen != q.correctIndex()) {
        q.whyWrong(chosen)?.let {
            Label("Why ${q.options()[chosen].take(40)} is wrong")
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!tip.isNullOrBlank()) {
        Label(if (tl) "Tip sa topic na ito" else "Topic tip")
        Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showedTlFallback) {
        Text(
            "Taglish explanation for this question is coming soon — showing English.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun hintFor(vm: AppViewModel, q: Question, lang: String): String {
    val own = if (lang == "tl") q.hintTl ?: q.hint else q.hint
    if (!own.isNullOrBlank()) return own
    val t = vm.topics.firstOrNull { it.id == q.subtopic } ?: return ""
    return if (lang == "tl") t.tipTl else t.tip
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionGrid(state: QuizState, questions: List<Question>, onPick: (Int) -> Unit, onSubmit: () -> Unit) {
    val fb = LocalFeedback.current
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("All questions", style = MaterialTheme.typography.titleMedium)
        Text(
            "${state.answeredCount} answered · ${state.marked.count { it }} marked · ${state.size - state.answeredCount} left",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            questions.indices.forEach { idx ->
                val answered = state.answers[idx] >= 0
                val color = when {
                    state.revealed[idx] && state.canonicalChosen(idx) == questions[idx].correctIndex() -> fb.good
                    state.revealed[idx] -> fb.bad
                    answered -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(color)
                        .border(
                            width = if (idx == state.index || state.marked[idx]) 3.dp else 0.dp,
                            color = if (state.marked[idx]) Sun else MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onPick(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${idx + 1}",
                        color = if (answered || state.revealed[idx]) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors()) {
            Text("Submit now")
        }
    }
}
