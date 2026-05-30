package com.pjcinc.csepractice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.m3.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.repository.QuestionRepository
import com.pjcinc.csepractice.ui.theme.CSEPracticeAppTheme
import kotlinx.coroutines.flow.Flow

class ChartActivity : ComponentActivity() {
    private val repository by lazy { QuestionRepository(applicationContext) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CSEPracticeAppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.chart_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    ScoreChartScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        sessions = repository.getAllSessions()
                    )
                }
            }
        }
    }
}

/**
 * Score-over-time line chart, powered by Vico's Compose-native API.
 *
 * `ProvideVicoTheme` + `rememberM3VicoTheme()` makes Vico's defaults follow
 * MaterialTheme colors, so the chart respects light/dark mode automatically.
 */
@Composable
fun ScoreChartScreen(
    modifier: Modifier = Modifier,
    sessions: Flow<List<PracticeSession>>
) {
    val sessionList by sessions.collectAsState(emptyList())

    if (sessionList.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_history),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Sessions arrive newest-first; reverse for left-to-right chronological order.
    val scores = remember(sessionList) {
        sessionList.reversed().map { it.score }
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(scores) {
        modelProducer.runTransaction {
            lineSeries { series(scores) }
        }
    }

    ProvideVicoTheme(theme = rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom()
            ),
            modelProducer = modelProducer,
            modifier = modifier.padding(16.dp)
        )
    }
}
