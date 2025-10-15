package com.example.csepractice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.csepractice.ui.theme.CSEPracticeAppTheme
import com.example.csepractice.viewmodel.PracticeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)  // Default to light mode
        setContent {
            CSEPracticeAppTheme(darkTheme = isDarkMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AppTopBar(onSettingsClick = {
                            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                            startActivity(intent)
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
fun AppTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = { Text("CSE Practice") },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PracticeScreen(modifier: Modifier = Modifier, viewModel: PracticeViewModel = viewModel()) {
    val questions by viewModel.questions.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val selectedAnswers by viewModel.selectedAnswers.collectAsState()
    val score by viewModel.score.collectAsState()
    val sessions by viewModel.sessions.collectAsState(emptyList())
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val categories by viewModel.categories.collectAsState(emptyList())  // Dynamic
    val context = LocalContext.current

    val visible = remember { mutableStateOf(false) }
    val numQuestions = remember { mutableIntStateOf(10) }  // Default 10
    val isSelecting = remember { mutableStateOf(true) }  // New: Control selector vs loading

    LaunchedEffect(Unit) {
        delay(300)
        visible.value = true
    }

    AnimatedVisibility(visible = visible.value, enter = fadeIn()) {
        if (questions.isEmpty() && score == 0) {  // Selector or loading
            if (categories.isEmpty()) {
                CircularProgressIndicator()  // Loading categories
            } else if (isSelecting.value) {  // Show selector until button press
                Column(
                    modifier = modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start  // Left-align for better readability
                ) {
                    Text("Select Categories:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    categories.forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Checkbox(
                                checked = selectedCategories.contains(category),
                                onCheckedChange = { viewModel.toggleCategory(category) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))  // Space between checkbox and text
                            Text(category)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Number of Questions:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(10, 20, 30).forEach { num ->
                            RadioButton(selected = numQuestions.intValue == num, onClick = { numQuestions.intValue = num })
                            Text("$num", modifier = Modifier.padding(end = 16.dp))  // Space between options
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        isSelecting.value = false
                        viewModel.startPractice(numQuestions.intValue)
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Start Practice")
                    }
                }
            } else {
                Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Loading questions...", modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else if (score > 0) {
            val scrollState = rememberScrollState()
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your score: $score%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.resetForNewSession()
                            isSelecting.value = true  // Reset to show selector on new session
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Start New Practice")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.practiceWeakAreas() }) {
                            Text("Practice Weak Areas")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("Progress History:", style = MaterialTheme.typography.titleMedium)
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Date", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                        Text("Score", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    sessions.forEach { session ->
                        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.date))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(formattedDate, modifier = Modifier.weight(2f))
                            Text("${session.score}%", modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val averageScore = if (sessions.isNotEmpty()) {
                    sessions.map { it.score }.average().toInt()
                } else {
                    0
                }
                Text(
                    text = "Average Score: $averageScore%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Category Averages:", style = MaterialTheme.typography.titleMedium)
                categories.forEach { cat ->
                    val avg by viewModel.getAverageForCategory(cat).collectAsState(0.0)
                    Text("$cat: ${avg.toInt()}%")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, ChartActivity::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                ) {
                    Text("View Progress Chart", color = Color.White)
                }
            }
        } else {
            val currentQuestion = questions[currentIndex]
            val isCurrentAnswered = selectedAnswers.containsKey(currentIndex)
            Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                        } else {
                            slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                        }
                    },
                    label = "questionAnimation"
                ) { index ->
                    val question = questions[index]
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(question.text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            listOf(question.optionA, question.optionB, question.optionC, question.optionD).forEachIndexed { optIndex, option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedAnswers[index] == optIndex,
                                        onClick = { viewModel.selectAnswer(index, optIndex) }
                                    )
                                    Text(option)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { viewModel.previousQuestion() }, enabled = currentIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Previous")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { viewModel.nextQuestion() }, enabled = currentIndex < questions.size - 1 && isCurrentAnswered) {
                        Text("Next")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (currentIndex == questions.size - 1) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Submit pressed!", Toast.LENGTH_SHORT).show()
                            viewModel.calculateScore()
                        },
                        enabled = isCurrentAnswered,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Submit")
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}