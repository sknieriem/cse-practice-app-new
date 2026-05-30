package com.pjcinc.csepractice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pjcinc.csepractice.ui.components.HistoryPanel
import com.pjcinc.csepractice.ui.theme.CSEPracticeAppTheme
import com.pjcinc.csepractice.viewmodel.PracticeViewModel
import com.pjcinc.csepractice.viewmodel.UiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CSEPracticeAppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AppTopBar(onSettingsClick = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        })
                    }
                ) { innerPadding ->
                    PracticeScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.title_practice)) },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
            }
        }
    )
}

@Composable
fun PracticeScreen(
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState(initial = emptyList())

    // Whether the menu is currently showing the history panel instead of the selector.
    var showHistory by rememberSaveable { mutableStateOf(false) }

    when (val state = uiState) {
        UiState.Initial -> {
            if (categories.isEmpty()) {
                CenteredLoader(stringResource(R.string.loading_categories), modifier)
            } else if (showHistory) {
                HistoryView(
                    viewModel = viewModel,
                    onBack = { showHistory = false },
                    onViewChart = {
                        context.startActivity(Intent(context, ChartActivity::class.java))
                    },
                    modifier = modifier
                )
            } else {
                CategorySelectorView(
                    viewModel = viewModel,
                    onShowHistory = { showHistory = true },
                    modifier = modifier
                )
            }
        }
        UiState.Loading -> CenteredLoader(stringResource(R.string.loading), modifier)

        is UiState.InProgress -> InProgressView(viewModel = viewModel, modifier = modifier)

        is UiState.Finished -> FinishedView(
            state = state,
            viewModel = viewModel,
            onViewChart = { context.startActivity(Intent(context, ChartActivity::class.java)) },
            modifier = modifier
        )

        is UiState.Review -> ReviewView(items = state.items, viewModel = viewModel, modifier = modifier)

        is UiState.Error -> ErrorView(
            message = state.message,
            onRetry = { viewModel.resetForNewSession() },
            modifier = modifier
        )
    }
}

@Composable
private fun CenteredLoader(text: String, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(text, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CategorySelectorView(
    viewModel: PracticeViewModel,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val numQuestions = remember { mutableIntStateOf(10) }

    val canStart = selectedCategories.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.select_categories),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        categories.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = selectedCategories.contains(category),
                    onCheckedChange = { viewModel.toggleCategory(category) }
                )
                Spacer(Modifier.width(8.dp))
                Text(category)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.num_questions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(10, 25, 50, 100).forEach { num ->
                RadioButton(
                    selected = numQuestions.intValue == num,
                    onClick = { numQuestions.intValue = num }
                )
                Text("$num", modifier = Modifier.padding(end = 16.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        if (!canStart) {
            Text(
                stringResource(R.string.select_at_least_one_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { viewModel.startPractice(numQuestions.intValue) },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_practice))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onShowHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.view_scores_and_chart))
        }
    }
}

@Composable
private fun HistoryView(
    viewModel: PracticeViewModel,
    onBack: () -> Unit,
    onViewChart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HistoryPanel(viewModel = viewModel, onViewChartClick = onViewChart)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_selector))
        }
    }
}

@Composable
private fun InProgressView(viewModel: PracticeViewModel, modifier: Modifier = Modifier) {
    val questions by viewModel.questions.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val selectedAnswers by viewModel.selectedAnswers.collectAsState()

    if (questions.isEmpty()) return

    val isCurrentAnswered = selectedAnswers.containsKey(currentIndex)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.question_progress, currentIndex + 1, questions.size),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "questionAnimation"
        ) { index ->
            val question = questions.getOrNull(index) ?: return@AnimatedContent
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        question.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val options = listOf(
                        question.optionA, question.optionB,
                        question.optionC, question.optionD
                    )
                    options.forEachIndexed { optIndex, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedAnswers[index] == optIndex,
                                onClick = { viewModel.selectAnswer(index, optIndex) }
                            )
                            Text(option, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.previousQuestion() },
                enabled = currentIndex > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.previous))
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { viewModel.nextQuestion() },
                enabled = currentIndex < questions.size - 1 && isCurrentAnswered
            ) {
                Text(stringResource(R.string.next))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (currentIndex == questions.size - 1) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.calculateScore() },
                enabled = isCurrentAnswered,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.submit))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FinishedView(
    state: UiState.Finished,
    viewModel: PracticeViewModel,
    onViewChart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.your_score, state.score),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.your_score_detail, state.correct, state.total),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.showReview() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_title))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.resetForNewSession() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.start_new_practice))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.practiceWeakAreas() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.practice_weak_areas))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HistoryPanel(viewModel = viewModel, onViewChartClick = onViewChart)
    }
}

@Composable
private fun ReviewView(
    items: List<com.pjcinc.csepractice.viewmodel.QuestionResult>,
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.review_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.lazy.items(items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            item.question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                if (item.isCorrect) R.string.answer_correct
                                else R.string.answer_incorrect
                            ),
                            color = if (item.isCorrect)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.explanation_label, item.question.explanation))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.resetForNewSession() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.done_back_to_start))
        }
    }
}
